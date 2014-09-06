package io.minestack.node.slave;

import com.rabbitmq.client.Connection;
import io.minestack.db.DoubleChest;
import io.minestack.db.entity.DCNode;
import io.minestack.db.entity.server.DCServerType;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Log4j2
public class SlaveLoop implements Runnable {

    private final Connection connection;
    private final ObjectId _nodeId;

    public SlaveLoop(DCNode node) throws IOException {
        connection = DoubleChest.getRabbitMQ().getConnection();
        this._nodeId = node.get_id();
    }

    @Override
    public void run() {
        Map<DCServerType, SlaveLoopWorker> workers = new HashMap<DCServerType, SlaveLoopWorker>();
        while(true) {

            Iterator<Map.Entry<DCServerType, SlaveLoopWorker>> iterator = workers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<DCServerType, SlaveLoopWorker> workerEntry = iterator.next();
                if (DoubleChest.getServerTypeLoader().loadEntity(workerEntry.getKey().get_id()) == null) {
                    log.info("Removing slave worker loop " + workerEntry.getKey().getName());
                    workerEntry.getValue().stopWorking();
                    iterator.remove();
                }
            }

            for (DCServerType serverType : DoubleChest.getServerTypeLoader().getTypes()) {
                boolean has = false;
                for (DCServerType serverType1 : workers.keySet()) {
                    if (serverType1.get_id().equals(serverType.get_id())) {
                        has = true;
                        break;
                    }
                }
                if (!has) {
                    try {
                        log.info("Starting Slave Loop Worker "+serverType.getName());
                        SlaveLoopWorker slaveLoopWorker = new SlaveLoopWorker(serverType, _nodeId, connection);
                        workers.put(serverType, slaveLoopWorker);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
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
