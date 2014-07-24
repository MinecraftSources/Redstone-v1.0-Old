package com.rmb938.mn2.docker.nc.slave;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import lombok.extern.log4j.Log4j2;

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

    public SlaveLoop(RabbitMQ rabbitMQ, ServerTypeLoader serverTypeLoader) throws IOException {
        connection = rabbitMQ.getConnection();
        connection.addShutdownListener(Throwable::printStackTrace);
        this.serverTypeLoader = serverTypeLoader;
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
                    SlaveLoopWorker slaveLoopWorker = new SlaveLoopWorker(serverType, connection, serverTypeLoader);
                    workers.put(serverType, slaveLoopWorker);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            try {
                Thread.sleep(30000);
            } catch (InterruptedException e) {
                log.info("Stopping Tick");
                break;
            }
        }
    }
}
