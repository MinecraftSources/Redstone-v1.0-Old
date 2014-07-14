package com.rmb938.mn2.docker.nc;

import com.mongodb.MongoException;
import com.mongodb.ServerAddress;
import com.rmb938.mn2.docker.db.mongo.MongoDatabase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

public class NodeController {

    private static final Logger logger = LogManager.getLogger(NodeController.class.getName());

    public static void main(String[] args) {
        new NodeController();
    }

    private MongoDatabase mongoDatabase;

    public NodeController() {
        logger.info("Started Node Controller");

        String mongoHosts = System.getenv("MONGO_HOSTS");
        if (mongoHosts != null) {
            String mongoDB = System.getenv("MONGO_DB");
            if (mongoDB != null) {
                ArrayList<ServerAddress> serverAddresses = new ArrayList<>();

                try {
                   mongoDatabase = new MongoDatabase(serverAddresses, mongoDB);
                } catch (MongoException ex) {
                    ex.printStackTrace();
                    return;
                }
            } else {
                logger.error("The MONGO_DB environment variable must be set");
                return;
            }
        } else {
            logger.error("The MONGO_HOSTS environment variable must be set");
            return;
        }

        System.getenv("RABBITMQ_HOSTS");
        System.getenv("RABBITMQ_USERNAME");
        System.getenv("RABBITMQ_PASSWORD");

        System.getenv("REDIS_IP");
        System.getenv("REDIS_PORT");
    }

}
