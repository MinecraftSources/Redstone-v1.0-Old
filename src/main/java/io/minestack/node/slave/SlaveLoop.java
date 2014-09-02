package io.minestack.node.slave;

import com.rabbitmq.client.Connection;
import io.minestack.db.Uranium;
import io.minestack.db.entity.UNode;
import io.minestack.db.entity.UServerType;
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

    public SlaveLoop(UNode node) throws IOException {
        connection = Uranium.getRabbitMQ().getConnection();
        this._nodeId = node.get_id();
    }

    @Override
    public void run() {
        Map<UServerType, SlaveLoopWorker> workers = new HashMap<UServerType, SlaveLoopWorker>();
        while(true) {

            Iterator<Map.Entry<UServerType, SlaveLoopWorker>> iterator = workers.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<UServerType, SlaveLoopWorker> workerEntry = iterator.next();
                if (Uranium.getServerTypeLoader().loadEntity(workerEntry.getKey().get_id()) == null) {
                    log.info("Removing slave worker loop " + workerEntry.getKey().getName());
                    workerEntry.getValue().stopWorking();
                    iterator.remove();
                }
            }

            Uranium.getServerTypeLoader().getTypes().stream().filter(serverType -> !workers.containsKey(serverType)).forEach(serverType -> {
                try {
                    log.info("Starting Slave Loop Worker "+serverType.getName());
                    SlaveLoopWorker slaveLoopWorker = new SlaveLoopWorker(serverType, _nodeId, connection);
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
