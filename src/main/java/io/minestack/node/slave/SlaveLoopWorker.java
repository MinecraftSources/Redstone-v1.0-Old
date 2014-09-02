package io.minestack.node.slave;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.mongodb.DuplicateKeyException;
import com.rabbitmq.client.*;
import io.minestack.db.DoubleChest;
import io.minestack.db.entity.DCNode;
import io.minestack.db.entity.DCServer;
import io.minestack.db.entity.DCServerType;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;

@Log4j2
public class SlaveLoopWorker {

    private SlaveConsumer consumer;
    private final Connection connection;
    private Channel channel;
    private final ObjectId _myServerTypeId;
    private final ObjectId _myNodeId;
    private boolean stop = false;

    public SlaveLoopWorker(DCServerType serverType, ObjectId _myNodeId, Connection connection) throws Exception {
        _myServerTypeId = serverType.get_id();
        this._myNodeId = _myNodeId;
        this.connection = connection;
        channel = connection.createChannel();
        connection.addShutdownListener(new ShutdownListener() {
            @Override
            public void shutdownCompleted(ShutdownSignalException e) {
                log.error("Slave Loop Worker RabbitMQ Shutdown", e);
            }
        });
        consumerSetup();
    }

    private void consumerSetup() throws IOException {
        try {
            log.info("Connecting to Queue "+_myServerTypeId.toString()+"-server-worker");
            channel.queueDeclarePassive(_myServerTypeId.toString()+ "-server-worker");
        } catch (IOException e) {
            channel = connection.createChannel();
            log.info("Creating Queue "+_myServerTypeId.toString()+"-server-worker");
            HashMap<String, Object> args = new HashMap<>();
            args.put("x-ha-policy", "all");
            channel.queueDeclare(_myServerTypeId.toString()+"-server-worker", true, false, true, args);
        }
        channel.basicQos(1);
        consumer = new SlaveConsumer(channel);
        HashMap<String, Object> args = new HashMap<String, Object>();
        args.put("x-cancel-on-ha-failover", true);
        channel.basicConsume(_myServerTypeId.toString()+"-server-worker", false, args, consumer);
    }

    public void stopWorking() {
        try {
            stop = true;
            channel.basicCancel(consumer.getConsumerTag());
            channel.close();
            connection.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    class SlaveConsumer extends DefaultConsumer {

        public SlaveConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleCancel(String consumerTag) throws IOException {
            if (!stop) {
                consumerSetup();
            }
        }

        @Override
        public void handleRecoverOk(String consumerTag) {

        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            JSONObject object = new JSONObject(new String(body));
            log.info("Received Server build request "+object);

            if (object.getInt("ttl") <= 0) {
                log.error("TTL for " + object + " is 0. Dropping Build request");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }
            object.put("ttl", object.getInt("ttl")-1);

            log.info("Setting Server Type");
            DCServerType serverType = DoubleChest.getServerTypeLoader().loadEntity(_myServerTypeId);
            if (serverType == null) {
                log.error("Server Type " + _myServerTypeId + " no longer exists destroying build request");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            log.info("Getting Node");
            DCNode node = DoubleChest.getNodeLoader().loadEntity(_myNodeId);
            if (node == null) {
                log.error("Received build message but cannot find my node info");
                channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            if (!object.getString("type").equals(_myServerTypeId.toString())) {
                log.error("Mismatched Server Type Received: " + object.getString("type") + " Required: " + _myServerTypeId);
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            log.info("Checking Ram");
            int currentRamUsage = 0;

            for (DCServer server : DoubleChest.getServerLoader().getNodeServers(node)) {
                currentRamUsage += server.getServerType().getMemory();
                if (server.getPort() == -1) {
                    log.error("Already creating a server. Waiting...");
                    channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                    channel.basicNack(envelope.getDeliveryTag(), false, false);
                    return;
                }
            }

            if ((currentRamUsage+serverType.getMemory()) > node.getRam()) {
                log.error("Not enough memory to create " + serverType.getName() + " re-queuing request");
                channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }


            log.info("Creating Server");
            DCServer server = new DCServer();
            server.setServerType(serverType);
            server.setNode(node);
            server.setLastUpdate(System.currentTimeMillis()+300000);//set last update to 5 mins from now this should be way more then enough to setup the server container

            try {
                ObjectId serverId = DoubleChest.getServerLoader().insertEntity(server);
                server = DoubleChest.getServerLoader().loadEntity(serverId);
            } catch (Exception ex) {
                if (ex instanceof DuplicateKeyException) {
                    log.error("Error inserting new server for " + serverType.getName() + " duplicate");
                } else {
                    log.error("Error inserting new server for " + serverType.getName() + " " + ex.getMessage());
                }
                channel.basicPublish("", serverType.get_id() + "-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            if (server == null) {
                log.error("Created server is null");
                channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            log.info("Creating Docker Container " + serverType.getName() + "." + server.getNumber());
            DockerClientConfig.DockerClientConfigBuilder config = DockerClientConfig.createDefaultConfigBuilder();
            config.withVersion("1.13");
            config.withUri("http://" + node.getAddress() + ":4243");
            DockerClient dockerClient = new DockerClientImpl(config.build());

            CreateContainerResponse response;
            try {
                for (Container container : dockerClient.listContainersCmd().withShowAll(true).exec()) {
                    String name = container.getNames()[0];
                    if (name.equals("/"+serverType.getName()+"."+server.getNumber())) {
                        log.info("Deleting " + Arrays.toString(container.getNames()));
                        try {
                            dockerClient.killContainerCmd(container.getId()).exec();
                        } catch (Exception ignored) {
                        }
                        dockerClient.removeContainerCmd(container.getId()).withForce(true).exec();
                        break;
                    }
                }

                response = dockerClient.createContainerCmd("mnsquared/server")
                        .withEnv("MONGO_HOSTS=" + System.getenv("MONGO_HOSTS"),
                                "RABBITMQ_HOSTS=" + System.getenv("RABBITMQ_HOSTS"),
                                "RABBITMQ_USERNAME=" + System.getenv("RABBITMQ_USERNAME"),
                                "RABBITMQ_PASSWORD=" + System.getenv("RABBITMQ_PASSWORD"),
                                "MY_SERVER_ID=" + server.get_id().toString())
                        .withName(serverType.getName()+"."+server.getNumber())
                        .withStdinOpen(true)
                        .exec();
            } catch (Exception ex) {
                ex.printStackTrace();
                log.error("Unable to create container for server " + serverType.getName()+"."+server.getNumber());
                channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            if (response == null) {
                log.error("Null docker response");
                channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            log.info("Saving container Info");
            String containerId = response.getId();
            server.setContainerId(containerId);

            DoubleChest.getServerLoader().saveEntity(server);

            try {
                log.info("Starting container for "+serverType.getName()+"."+server.getNumber());
                dockerClient.startContainerCmd(containerId).withPublishAllPorts(true).withBinds(new Bind("/mnt/minestack", new Volume("/mnt/minestack"))).exec();
            } catch (Exception ex) {
                log.error("Unable to start container for server " + serverType.getName());
                channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            channel.basicAck(envelope.getDeliveryTag(), false);
        }
    }

}
