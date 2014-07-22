package com.rmb938.mn2.docker.nc;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.ServerAddress;
import com.rabbitmq.client.Address;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import com.rmb938.mn2.docker.nc.database.NodeLoader;
import com.rmb938.mn2.docker.nc.database.ServerLoader;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.slave.SlaveLoop;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

import java.io.IOException;
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

        String db = System.getenv("MONGO_DB");

        if (db == null) {
            log.error("MONGO_DB is not set.");
            return;
        }

        List<ServerAddress> mongoAddresses = new ArrayList<>();

        for (String host : hosts.split(",")) {
            String[] info = host.split(":");
            try {
                mongoAddresses.add(new ServerAddress(info[0], Integer.parseInt(info[1])));
            } catch (UnknownHostException e) {
                log.error("Invalid Mongo Address " + host);
            }
        }

        if (mongoAddresses.isEmpty()) {
            log.error("No valid mongo addresses");
            return;
        }

        MongoDatabase mongoDatabase = new MongoDatabase(null, db);

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

        if (rabbitAddresses.isEmpty()) {
            log.error("No valid RabbitMQ addresses");
            return;
        }

        RabbitMQ rabbitMQ = null;
        try {
            rabbitMQ = new RabbitMQ(rabbitAddresses, username, password);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String myIP = System.getenv("MY_NODE_IP");

        NodeLoader nodeLoader = new NodeLoader(mongoDatabase);
        ServerTypeLoader serverTypeLoader = new ServerTypeLoader(mongoDatabase);
        ServerLoader serverLoader = new ServerLoader(mongoDatabase);

        DBObject dbObject = mongoDatabase.findOne(nodeLoader.getCollection(), new BasicDBObject("host", myIP));
        if (dbObject == null) {
            log.error("Cannot find my node info");
            return;
        }

        ExecutorService executorService = Executors.newCachedThreadPool();

        try {
            MasterLoop masterLoop = new MasterLoop((ObjectId)dbObject.get("_id"), rabbitMQ, nodeLoader, serverTypeLoader, serverLoader);
            executorService.submit(masterLoop);
        } catch (Exception e) {
            e.printStackTrace();
        }

        SlaveLoop slaveLoop = new SlaveLoop(rabbitMQ, serverTypeLoader, executorService);
        executorService.submit(slaveLoop);

    }

}
