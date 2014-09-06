package io.minestack.node.master;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.NotFoundException;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.mongodb.BasicDBObject;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.rabbitmq.client.*;
import io.minestack.db.DoubleChest;
import io.minestack.db.entity.DCNode;
import io.minestack.db.entity.proxy.DCProxy;
import io.minestack.db.entity.proxy.DCProxyType;
import io.minestack.db.entity.proxy.driver.bungee.DCBungee;
import io.minestack.db.entity.proxy.driver.bungee.DCBungeeType;
import io.minestack.db.entity.server.DCServer;
import io.minestack.db.entity.server.DCServerType;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.IOException;


@Log4j2
public class MasterLoop implements Runnable {

    private final ObjectId _myNodeId;
    private final Connection connection;

    public MasterLoop(ObjectId _myNodeId) throws Exception {
        this._myNodeId = _myNodeId;
        connection = DoubleChest.getRabbitMQ().getConnection();
        connection.addShutdownListener(new ShutdownListener() {
            @Override
            public void shutdownCompleted(ShutdownSignalException e) {
                log.error("Master Loop RabbitMQ Shutdown", e);
            }
        });
    }

    private boolean amIMaster() {
        DCNode master = DoubleChest.getNodeLoader().getMaster();
        return master != null && master.get_id().equals(_myNodeId);
    }

    @Override
    public void run() {
        while (true) {
            log.info("Checking Master");
            if (amIMaster()) {
                log.info("I am Master");
                serverRun();
                proxyMasterRun();
            }
            proxyRun();
            log.info("Sleeping");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                log.info("Stopping Tick");
                break;
            }
        }
    }

    private void serverRun() {
        log.info("Server Master Run");

        DBObject query = new BasicDBObject("lastUpdate", new BasicDBObject("$lt", System.currentTimeMillis() - 60000));

        DBCursor dbCursor = DoubleChest.getServerLoader().getDb().findMany(DoubleChest.getServerLoader().getCollection(), query);
        while (dbCursor.hasNext()) {
            DBObject dbObject = dbCursor.next();
            DCServer server = DoubleChest.getServerLoader().loadEntity((ObjectId) dbObject.get("_id"));
            if (server != null) {
                if (server.getNode() != null) {
                    DCNode myNode = DoubleChest.getNodeLoader().loadEntity(_myNodeId);
                    if (myNode == null) {
                        log.error("Cannot find my node info");
                        return;
                    }
                    DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig.createDefaultConfigBuilder();
                    config.withVersion("1.14");
                    if (myNode.get_id().equals(server.getNode().get_id())) {
                        config.withUri("http://10.0.42.1:4243");
                    } else {
                        config.withUri("http://" + server.getNode().getAddress() + ":4243");
                    }
                    DockerClient dockerClient = new DockerClientImpl(config.build());
                    boolean found = true;
                    try {
                        dockerClient.inspectContainerCmd(server.getContainerId()).exec();
                    } catch (Exception ex) {
                        if (ex instanceof NotFoundException) {
                            found = false;
                        } else {
                            log.error("Error checking if server container exists");
                            continue;
                        }
                    }
                    if (found) {
                        try {
                            if (server.getServerType() != null) {
                                log.info("Killing dead server " + server.getServerType().getName() + "." + server.getNumber());
                            } else {
                                log.info("Killing dead server " + server.get_id() + "." + server.getNumber());
                            }
                            dockerClient.killContainerCmd(server.getContainerId()).exec();
                        } catch (Exception ex) {
                            log.error("Error killing dead server " + ex.getMessage());
                        }
                        try {
                            if (server.getServerType() != null) {
                                log.info("Remove dead server container " + server.getServerType().getName() + "." + server.getNumber());
                            } else {
                                log.info("Remove dead server container " + server.get_id() + "." + server.getNumber());
                            }
                            dockerClient.removeContainerCmd(server.getContainerId()).withForce(true).exec();
                        } catch (Exception ex) {
                            log.error("Error removing dead server " + ex.getMessage());
                            continue;
                        }
                    }
                }
                if (server.getServerType() != null) {
                    log.info("Removing dead server " + server.getServerType().getName() + "." + server.getNumber());
                } else {
                    log.info("Removing dead server " + server.get_id() + "." + server.getNumber());
                }
                DoubleChest.getServerLoader().getDb().remove(DoubleChest.getServerLoader().getCollection(), dbObject);
            }
        }
        dbCursor.close();

        for (DCServerType serverType : DoubleChest.getServerTypeLoader().getTypes()) {
            if (serverType.isDisabled()) {
                continue;
            }
            Channel channel;
            try {
                channel = connection.createChannel();
                AMQP.Queue.DeclareOk declareOk = channel.queueDeclarePassive(serverType.get_id()+ "-server-worker");
                int messages = declareOk.getMessageCount();
                if (messages > 0) {
                    log.info("Queue for "+serverType.getName()+" has build requests. Waiting...");
                    continue;
                }
            } catch (IOException e) {
                //Queue hasn't been made yet so continue
                log.info("Queue for "+serverType.getName()+" hasn't been created yet. Waiting...");
                continue;
            }

            int amount = serverType.getAmount();
            long current = DoubleChest.getServerLoader().getCount(serverType);
            if (amount > current) {
                long needed = amount - current;
                for (int i = 0; i < needed; i++) {
                    JSONObject object = new JSONObject();
                    object.put("type", serverType.get_id().toString());
                    object.put("ttl", 3);
                    try {
                        channel.basicPublish("", serverType.get_id() + "-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                        log.info("Sent server build request " + object);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        log.info("Finished Server Master Run");
    }

    public void proxyMasterRun() {
        log.info("Proxy Master Run");
        log.info("Removing Dead Proxies");
        DBCursor dbCursor = DoubleChest.getProxyLoader().getDb().findMany(DoubleChest.getProxyLoader().getCollection(), new BasicDBObject("lastUpdate", new BasicDBObject("$lt", System.currentTimeMillis() - 60000)));
        while (dbCursor.hasNext()) {
            DBObject dbObject = dbCursor.next();
            DCProxy proxy = DoubleChest.getProxyLoader().loadEntity((ObjectId) dbObject.get("_id"));
            if (proxy != null) {
                if (proxy.getNode() != null) {
                    DCNode myNode = DoubleChest.getNodeLoader().loadEntity(_myNodeId);
                    if (myNode == null) {
                        log.error("Cannot find my node info");
                        return;
                    }
                    DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig.createDefaultConfigBuilder();
                    config.withVersion("1.14");
                    if (myNode.get_id().equals(proxy.getNode().get_id())) {
                        config.withUri("http://10.0.42.1:4243");
                    } else {
                        config.withUri("http://" + proxy.getNode().getAddress() + ":4243");
                    }
                    DockerClient dockerClient = new DockerClientImpl(config.build());
                    boolean found = true;
                    try {
                        dockerClient.inspectContainerCmd(proxy.getContainerId()).exec();
                    } catch (Exception ex) {
                        if (ex instanceof NotFoundException) {
                            found = false;
                        } else {
                            log.error("Error checking if proxy container exists");
                            continue;
                        }
                    }
                    if (found) {
                        try {
                            if (proxy.getProxyType() != null) {
                                log.info("Killing dead proxy " + proxy.getProxyType().getName());
                            } else {
                                log.info("Killing dead proxy " + proxy.get_id());
                            }
                            dockerClient.killContainerCmd(proxy.getContainerId()).exec();
                        } catch (Exception ex) {
                            log.error("Error killing dead proxy " + ex.getMessage());
                        }
                        try {
                            if (proxy.getProxyType() != null) {
                                log.info("Remove dead proxy container " + proxy.getProxyType().getName());
                            } else {
                                log.info("Remove dead proxy container " + proxy.get_id());
                            }
                            dockerClient.removeContainerCmd(proxy.getContainerId()).withForce(true).exec();
                        } catch (Exception ex) {
                            log.error("Error removing dead proxy " + ex.getMessage());
                            continue;
                        }
                    }
                }

                if (proxy.getProxyType() != null) {
                    log.info("Removing dead proxy " + proxy.getProxyType().getName());
                } else {
                    log.info("Removing dead proxy " + proxy.get_id());
                }
                DoubleChest.getProxyLoader().removeEntity(proxy);
            }
        }
        dbCursor.close();
        log.info("Finished Proxy Master Run");
    }

    private void proxyRun() {
        log.info("Proxy Run");
        DCNode node = DoubleChest.getNodeLoader().loadEntity(_myNodeId);
        if (node == null) {
            log.error("Cannot find my node");
            return;
        }

        if (node.getProxyType() != null) {
            log.info("Looking for proxy to create");
            if (DoubleChest.getProxyLoader().getNodeProxies(node) == null) {
                DCProxyType proxyType = node.getProxyType();
                DCProxy proxy = new DCProxy();
                proxy.setNode(node);
                proxy.setProxyType(proxyType);
                proxy.setLastUpdate(System.currentTimeMillis() + 300000);

                if (proxyType.getDriver() instanceof DCBungeeType) {
                    proxy.setDriver(new DCBungee());
                }

                ObjectId objectId = DoubleChest.getProxyLoader().insertEntity(proxy);
                proxy = DoubleChest.getProxyLoader().loadEntity(objectId);

                if (proxy == null) {
                    log.error("Created proxy is null");
                    return;
                }

                DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig.createDefaultConfigBuilder();
                config.withVersion("1.14");
                config.withUri("http://10.0.42.1:4243");
                DockerClient dockerClient = new DockerClientImpl(config.build());
                CreateContainerResponse response = null;
                try {
                    for (Container container : dockerClient.listContainersCmd().withShowAll(true).exec()) {
                        String name = container.getNames()[0];
                        if (name.equals("/" + proxyType.getName())) {
                            try {
                                dockerClient.killContainerCmd(container.getId()).exec();
                            } catch (Exception ignored) {
                            }
                            dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                            break;
                        }
                    }

                    log.info("Creating container for " + proxyType.getName());
                    if (proxy.getDriver() instanceof DCBungee) {
                        response = dockerClient.createContainerCmd("minestack/bungee")
                                .withEnv("MONGO_HOSTS=" + System.getenv("MONGO_HOSTS"),
                                        "RABBITMQ_HOSTS=" + System.getenv("RABBITMQ_HOSTS"),
                                        "RABBITMQ_USERNAME=" + System.getenv("RABBITMQ_USERNAME"),
                                        "RABBITMQ_PASSWORD=" + System.getenv("RABBITMQ_PASSWORD"),
                                        "MY_PROXY_ID=" + proxy.get_id().toString())
                                .withExposedPorts(new ExposedPort("tcp", 25565))
                                .withName(proxyType.getName())
                                .withStdinOpen(true)
                                .exec();
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    log.error("Unable to create container for proxy " + proxyType.getName());
                    return;
                }

                if (response == null) {
                    log.error("Null docker response");
                    return;
                }

                String containerId = response.getId();
                proxy.setContainerId(containerId);

                DoubleChest.getProxyLoader().saveEntity(proxy);

                try {
                    log.info("Starting container for " + proxyType.getName());
                    dockerClient.startContainerCmd(containerId).withPortBindings(new Ports(new ExposedPort("tcp", 25565), new Ports.Binding("0.0.0.0", 25565)))
                            .withBinds(new Bind("/mnt/minestack", new Volume("/mnt/minestack"))).exec();
                } catch (Exception ex) {
                    log.error("Unable to start container for proxy " + proxyType.getName());
                    return;
                }
            }
        }
        log.info("Finished Proxy Run");
    }
}
