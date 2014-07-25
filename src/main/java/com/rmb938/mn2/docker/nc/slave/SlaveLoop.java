package com.rmb938.mn2.docker.nc.slave;

import com.github.dockerjava.client.DockerClient;
import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import com.rmb938.mn2.docker.nc.database.NodeLoader;
import com.rmb938.mn2.docker.nc.database.ServerLoader;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.entity.Node;
import com.rmb938.mn2.docker.nc.entity.Server;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;

@Log4j2
public class SlaveLoop implements Runnable {

    private final Connection connection;
    private final ServerTypeLoader serverTypeLoader;
    private final ServerLoader serverLoader;
    private final NodeLoader nodeLoader;
    private final Node node;

    public SlaveLoop(RabbitMQ rabbitMQ, Node node, ServerTypeLoader serverTypeLoader, ServerLoader serverLoader, NodeLoader nodeLoader) throws IOException {
        connection = rabbitMQ.getConnection();
        connection.addShutdownListener(Throwable::printStackTrace);
        this.node = node;
        this.serverTypeLoader = serverTypeLoader;
        this.serverLoader = serverLoader;
        this.nodeLoader = nodeLoader;
    }

    @Override
    public void run() {
        Map<ServerType, SlaveLoopWorker> workers = new HashMap<ServerType, SlaveLoopWorker>();
        while(true) {

            Iterator<Map.Entry<ServerType, SlaveLoopWorker>> iterator = workers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ServerType, SlaveLoopWorker> workerEntry = iterator.next();
                if (serverTypeLoader.loadEntity(workerEntry.getKey().get_id()) == null) {
                    log.info("Removing slave worker loop " + workerEntry.getKey().getName());
                    workerEntry.getValue().stopWorking();
                    iterator.remove();
                }
            }

            serverTypeLoader.getTypes().stream().filter(serverType -> !workers.containsKey(serverType)).forEach(serverType -> {
                try {
                    log.info("Starting Slave Loop Worker "+serverType.getName());
                    SlaveLoopWorker slaveLoopWorker = new SlaveLoopWorker(serverType, node, connection, serverTypeLoader, serverLoader, nodeLoader);
                    workers.put(serverType, slaveLoopWorker);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            BasicDBList and = new BasicDBList();
            and.add(new BasicDBObject("lastUpdate", new BasicDBObject("$ne", 0)));
            and.add(new BasicDBObject("lastUpdate", new BasicDBObject("$lt", System.currentTimeMillis()-60000)));
            and.add(new BasicDBObject("node", node.getAddress()));

            DBCursor dbCursor = serverLoader.getDb().findMany(serverLoader.getCollection(), new BasicDBObject("$and", and));
            while (dbCursor.hasNext()) {
                DBObject dbObject = dbCursor.next();
                Server server = serverLoader.loadEntity((ObjectId)dbObject.get("_id"));
                if (server != null) {
                    DockerClient dockerClient = new DockerClient("http://"+node.getAddress()+":4243");

                    try {
                        log.info("Killing dead server "+server.getServerType().getName());
                        dockerClient.killContainerCmd(server.getContainerId()).exec();
                    } catch (Exception ex) {
                        log.info("Error killing dead server");
                        continue;
                    }
                    try {
                        log.info("Remove dead server container "+server.getServerType().getName());
                        dockerClient.removeContainerCmd(server.getContainerId()).exec();
                    } catch (Exception ex) {
                        log.info("Error removing dead server");
                        continue;
                    }

                    log.info("Removing dead server "+server.getServerType().getName());
                    serverLoader.getDb().remove(serverLoader.getCollection(), dbObject);
                }
            }

            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                log.info("Stopping Tick");
                break;
            }
        }
    }
}
