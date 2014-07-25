package com.rmb938.mn2.docker.nc;

import com.github.dockerjava.client.DockerClient;
import com.mongodb.BasicDBObject;
import com.rabbitmq.client.*;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import com.rmb938.mn2.docker.nc.database.NodeLoader;
import com.rmb938.mn2.docker.nc.database.ServerLoader;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.entity.Node;
import com.rmb938.mn2.docker.nc.entity.ServerType;
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
        Node master = nodeLoader.getMaster();
        return master != null && master.get_id().compareTo(_myNodeId) == 0;
    }

    @Override
    public void run() {
        while (true) {
            log.info("Sending Update");
            nodeLoader.getDb().updateDocument(nodeLoader.getCollection(), new BasicDBObject("_id", _myNodeId), new BasicDBObject("$set", new BasicDBObject("lastUpdate", System.currentTimeMillis())));
            if (amIMaster()) {
                for (ServerType serverType : serverTypeLoader.getTypes()) {
                    try {
                        AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(serverType.getName()+"-server-worker");
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
                            try {
                                channel.basicPublish("", serverType.getName()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                                log.info("Sent server build request "+object);
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
