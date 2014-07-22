package com.rmb938.mn2.docker.nc.slave;

import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.ExecutorService;

@Log4j2
public class SlaveLoop implements Runnable {

    private final RabbitMQ rabbitMQ;
    private final ServerTypeLoader serverTypeLoader;
    private final ExecutorService executorService;

    public SlaveLoop(RabbitMQ rabbitMQ, ServerTypeLoader serverTypeLoader, ExecutorService executorService) {
        this.rabbitMQ = rabbitMQ;
        this.serverTypeLoader = serverTypeLoader;
        this.executorService = executorService;
    }

    @Override
    public void run() {
        while(true) {
            for (ServerType serverType : serverTypeLoader.getTypes()) {
                try {
                    SlaveLoopWorker slaveLoopWorker = new SlaveLoopWorker(serverType, rabbitMQ, serverTypeLoader);
                    executorService.submit(slaveLoopWorker);
                } catch (Exception e) {
                    e.printStackTrace();
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
