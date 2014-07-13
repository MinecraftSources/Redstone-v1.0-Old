package com.rmb938.mn2.docker.nc;

public class NodeController {

    public static void main(String[] args) {
        new NodeController();
    }



    public NodeController() {
        System.getenv("MONGO_URL");
        System.getenv("RABBITMQ_URL");
        System.getenv("REDIS_IP");
        System.getenv("REDIS_PORT");
    }

}
