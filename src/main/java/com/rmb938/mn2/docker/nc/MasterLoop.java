package com.rmb938.mn2.docker.nc;

import com.github.dockerjava.client.DockerClient;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.MessageProperties;
import com.rmb938.mn2.docker.db.database.NodeLoader;
import com.rmb938.mn2.docker.db.database.ServerLoader;
import com.rmb938.mn2.docker.db.database.ServerTypeLoader;
import com.rmb938.mn2.docker.db.entity.MN2Node;
import com.rmb938.mn2.docker.db.entity.MN2Server;
import com.rmb938.mn2.docker.db.entity.MN2ServerType;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.IOException;


@Log4j2
public class MasterLoop implements Runnable {

    private final NodeLoader nodeLoader;
    private final ServerTypeLoader serverTypeLoader;
    private final ServerLoader serverLoader;
    private final ObjectId _myNodeId;
    private Channel channel;
    private final Connection connection;

    public MasterLoop(ObjectId _myNodeId, RabbitMQ rabbitMQ, NodeLoader nodeLoader, ServerTypeLoader serverTypeLoader, ServerLoader serverLoader) throws Exception {
        this.nodeLoader = nodeLoader;
        this.serverTypeLoader = serverTypeLoader;
        this.serverLoader = serverLoader;
        this._myNodeId = _myNodeId;
        connection = rabbitMQ.getConnection();
        connection.addShutdownListener(Throwable::printStackTrace);
        channel = connection.createChannel();
    }

    private boolean amIMaster() {
        MN2Node master = nodeLoader.getMaster();
        return master != null && master.get_id().compareTo(_myNodeId) == 0;
    }

    @Override
    public void run() {
        while (true) {
            log.info("Sending Update");
            nodeLoader.getDb().updateDocument(nodeLoader.getCollection(), new BasicDBObject("_id", _myNodeId), new BasicDBObject("$set", new BasicDBObject("lastUpdate", System.currentTimeMillis())));
            if (amIMaster()) {
                /*BasicDBList and = new BasicDBList();
                and.add(new BasicDBObject("lastUpdate", new BasicDBObject("$lt", System.currentTimeMillis()-60000)));
                and.add(new BasicDBObject("node", node.getAddress()));
                DBObject query = new BasicDBObject("$and", and);*/

                DBObject query = new BasicDBObject("lastUpdate", new BasicDBObject("$lt", System.currentTimeMillis() - 60000));

                DBCursor dbCursor = serverLoader.getDb().findMany(serverLoader.getCollection(), query);
                while (dbCursor.hasNext()) {
                    DBObject dbObject = dbCursor.next();
                    MN2Server server = serverLoader.loadEntity((ObjectId) dbObject.get("_id"));
                    if (server != null) {
                        if (server.getNode() != null) {
                            DockerClient dockerClient = new DockerClient("http://" + server.getNode().getAddress() + ":4243");

                            try {
                                log.info("Killing dead server " + server.getServerType().getName());
                                dockerClient.killContainerCmd(server.getContainerId()).exec();
                            } catch (Exception ex) {
                                log.error("Error killing dead server "+ex.getMessage());
                                continue;
                            }
                            try {
                                log.info("Remove dead server container " + server.getServerType().getName());
                                dockerClient.removeContainerCmd(server.getContainerId()).exec();
                            } catch (Exception ex) {
                                log.error("Error removing dead server "+ex.getMessage());
                                continue;
                            }
                        }

                        log.info("Removing dead server " + server.getServerType().getName());
                        serverLoader.getDb().remove(serverLoader.getCollection(), dbObject);
                    }
                }
                dbCursor.close();

                for (MN2ServerType serverType : serverTypeLoader.getTypes()) {
                    try {
                        AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(serverType.get_id()+ "-server-worker");
                        int messages = declareOk.getMessageCount();
                        if (messages > 0) {
                            continue;
                        }
                    } catch (IOException e) {
                        if (!channel.isOpen()) {
                            try {
                                channel = connection.createChannel();
                            } catch (IOException e1) {
                                e1.printStackTrace();
                            }
                        }
                        //Queue hasn't been made yet so continue
                        continue;
                    }

                    int amount = serverType.getAmount();
                    long current = serverLoader.getCount(serverType);
                    if (amount > current) {
                        long needed = amount - current;
                        for (int i = 0; i < needed; i++) {
                            JSONObject object = new JSONObject();
                            object.put("type", serverType.get_id().toString());
                            object.put("ttl", 3);
                            try {
                                channel.basicPublish("", serverType.get_id() + "-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                                log.info("Sent server build request " + object);
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log.info("Stopping Tick");
                break;
            }
        }
    }
}
