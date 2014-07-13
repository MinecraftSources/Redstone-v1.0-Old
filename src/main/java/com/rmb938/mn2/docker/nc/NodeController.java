package com.rmb938.mn2.docker.nc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class NodeController {

    private static final Logger logger = LogManager.getLogger(NodeController.class.getName());

    public static void main(String[] args) {
        new NodeController();
    }

    public NodeController() {
        logger.info("Started Node Controller");

        System.getenv("MONGO_URL");
        System.getenv("RABBITMQ_URL");
        System.getenv("REDIS_IP");
        System.getenv("REDIS_PORT");
    }

}
