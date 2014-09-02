package com.rmb938.mn2.docker.nc.slave;

import io.minestack.db.database.NodeLoader;
import io.minestack.db.database.ServerLoader;
import io.minestack.db.database.ServerTypeLoader;
import io.minestack.db.entity.MN2Node;
import io.minestack.db.entity.MN2ServerType;
import io.minestack.db.rabbitmq.RabbitMQ;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Log4j2
public class SlaveLoop implements Runnable {

    private final RabbitMQ rabbitmq;
    private final ServerTypeLoader serverTypeLoader;
    private final ServerLoader serverLoader;
    private final NodeLoader nodeLoader;
    private final MN2Node node;

    public SlaveLoop(RabbitMQ rabbitMQ, MN2Node node, ServerTypeLoader serverTypeLoader, ServerLoader serverLoader, NodeLoader nodeLoader) throws IOException {
        this.rabbitmq = rabbitMQ;
        this.node = node;
        this.serverTypeLoader = serverTypeLoader;
        this.serverLoader = serverLoader;
        this.nodeLoader = nodeLoader;
    }

    @Override
    public void run() {
        Map<MN2ServerType, SlaveLoopWorker> workers = new HashMap<MN2ServerType, SlaveLoopWorker>();
        while(true) {

            Iterator<Map.Entry<MN2ServerType, SlaveLoopWorker>> iterator = workers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<MN2ServerType, SlaveLoopWorker> workerEntry = iterator.next();
                if (serverTypeLoader.loadEntity(workerEntry.getKey().get_id()) == null) {
                    log.info("Removing slave worker loop " + workerEntry.getKey().getName());
                    workerEntry.getValue().stopWorking();
                    iterator.remove();
                }
            }

            serverTypeLoader.getTypes().stream().filter(serverType -> !workers.containsKey(serverType)).forEach(serverType -> {
                try {
                    log.info("Starting Slave Loop Worker "+serverType.getName());
                    SlaveLoopWorker slaveLoopWorker = new SlaveLoopWorker(serverType, node, rabbitmq.getConnection(), serverTypeLoader, serverLoader, nodeLoader);
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
