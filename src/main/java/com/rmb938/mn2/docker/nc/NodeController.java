package com.rmb938.mn2.docker.nc;

import com.mongodb.ServerAddress;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import lombok.extern.log4j.Log4j2;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class NodeController {

    public static void main(String[] args) {
        new NodeController();
    }

    private MongoDatabase mongoDatabase;

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

        List<ServerAddress> addresses = new ArrayList<>();

        for (String host : hosts.split(",")) {
            String[] info = host.split(":");
            try {
                addresses.add(new ServerAddress(info[0], Integer.parseInt(info[1])));
            } catch (UnknownHostException e) {
                log.error("Invalid Mongo Address "+host);
            }
        }

        if (addresses.isEmpty()) {
            log.error("No valid mongo addresses");
            return;
        }

        mongoDatabase = new MongoDatabase(null, db);

        System.getenv("RABBITMQ_HOSTS");
        System.getenv("RABBITMQ_USERNAME");
        System.getenv("RABBITMQ_PASSWORD");

    }

}
