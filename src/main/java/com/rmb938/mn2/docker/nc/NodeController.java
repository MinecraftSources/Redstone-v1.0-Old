package com.rmb938.mn2.docker.nc;

import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.ServerAddress;
import com.rabbitmq.client.Address;
import com.rmb938.mn2.docker.db.database.*;
import com.rmb938.mn2.docker.db.entity.MN2Node;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
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

        if (mongoAddresses.isEmpty()) {
            log.error("No valid mongo addresses");
            return;
        }

        log.info("Setting up mongo database mn2");
        MongoDatabase mongoDatabase = new MongoDatabase(mongoAddresses, "mn2");

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
            log.info("Setting up RabbitMQ");
            rabbitMQ = new RabbitMQ(rabbitAddresses, username, password);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        String myIP = System.getenv("MY_NODE_IP");

        PluginLoader pluginLoader = new PluginLoader(mongoDatabase);
        WorldLoader worldLoader = new WorldLoader(mongoDatabase);
        ServerTypeLoader serverTypeLoader = new ServerTypeLoader(mongoDatabase, pluginLoader, worldLoader);
        BungeeTypeLoader bungeeTypeLoader = new BungeeTypeLoader(mongoDatabase, pluginLoader, serverTypeLoader);
        NodeLoader nodeLoader = new NodeLoader(mongoDatabase, bungeeTypeLoader);
        ServerLoader serverLoader = new ServerLoader(mongoDatabase, nodeLoader, serverTypeLoader);
        BungeeLoader bungeeLoader = new BungeeLoader(mongoDatabase, bungeeTypeLoader, nodeLoader);

        log.info("Finding Node info "+myIP);
        DBObject dbObject = mongoDatabase.findOne(nodeLoader.getCollection(), new BasicDBObject("host", myIP));
        if (dbObject == null) {
            log.error("Cannot find my node info");
            return;
        }
        MN2Node node = nodeLoader.loadEntity((ObjectId) dbObject.get("_id"));

        ExecutorService executorService = Executors.newCachedThreadPool();

        try {
            log.info("Starting Master Loop");
            MasterLoop masterLoop = new MasterLoop(node.get_id(), rabbitMQ, nodeLoader, serverTypeLoader, serverLoader, bungeeTypeLoader, bungeeLoader);
            executorService.submit(masterLoop);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            log.info("Starting Slave Loop");
            SlaveLoop slaveLoop = new SlaveLoop(rabbitMQ, node, serverTypeLoader, serverLoader, nodeLoader);
            executorService.submit(slaveLoop);
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

}
