package io.minestack.node;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.ServerAddress;
import com.rabbitmq.client.Address;
import io.minestack.db.DoubleChest;
import io.minestack.db.entity.DCNode;
import io.minestack.node.master.MasterLoop;
import io.minestack.node.slave.SlaveLoop;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Log4j2
public class NodeController {

    public static void main(String[] args) {
        new NodeController();
    }


    public NodeController() {
        log.info("Started Node Controller");

        String hosts = System.getenv("MONGO_HOSTS");

        if (hosts == null) {
            log.error("MONGO_HOSTS is not set.");
            return;
        }

        List<ServerAddress> mongoAddresses = new ArrayList<ServerAddress>();
        for (String host : hosts.split(",")) {

            String[] info = host.split(":");
            try {
                mongoAddresses.add(new ServerAddress(info[0], Integer.parseInt(info[1])));
                log.info("Added Mongo Address " + host);
            } catch (UnknownHostException e) {
                log.error("Invalid Mongo Address " + host);
            }
        }

        hosts = System.getenv("RABBITMQ_HOSTS");
        String username = System.getenv("RABBITMQ_USERNAME");
        String password = System.getenv("RABBITMQ_PASSWORD");

        List<Address> rabbitAddresses = new ArrayList<>();
        for (String host : hosts.split(",")) {
            String[] info = host.split(":");
            try {
                rabbitAddresses.add(new Address(info[0], Integer.parseInt(info[1])));
            } catch (Exception e) {
                log.error("Invalid RabbitMQ Address " + host);
            }
        }

        try {
            DoubleChest.initDatabase(mongoAddresses, rabbitAddresses, username, password);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }

        String myIP = System.getenv("MY_NODE_IP");

        log.info("Finding Node info "+myIP);
        DBObject dbObject = DoubleChest.getNodeLoader().getDb().findOne(DoubleChest.getNodeLoader().getCollection(), new BasicDBObject("host", myIP));
        if (dbObject == null) {
            log.error("Cannot find my node info");
            return;
        }
        DCNode node = DoubleChest.getNodeLoader().loadEntity((ObjectId) dbObject.get("_id"));

        ExecutorService executorService = Executors.newCachedThreadPool();

        try {
            log.info("Starting Heartbeat Loop");
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        log.info("Sending Update");
                        DoubleChest.getNodeLoader().getDb().updateDocument(DoubleChest.getNodeLoader().getCollection(), new BasicDBObject("_id", node.get_id()), new BasicDBObject("$set", new BasicDBObject("lastUpdate", System.currentTimeMillis())));
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            log.info("Starting Slave Loop");
            SlaveLoop slaveLoop = new SlaveLoop(node);
            executorService.submit(slaveLoop);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            Thread.sleep(10000 + ((int) (Math.random() * 10000)));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        try {
            log.info("Starting Master Loop");
            MasterLoop masterLoop = new MasterLoop(node.get_id());
            executorService.submit(masterLoop);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }

}
