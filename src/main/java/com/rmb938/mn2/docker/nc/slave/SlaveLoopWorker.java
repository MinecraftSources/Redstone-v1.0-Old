package com.rmb938.mn2.docker.nc.slave;

import com.github.dockerjava.client.DockerClient;
import com.github.dockerjava.client.model.*;
import com.mongodb.DuplicateKeyException;
import com.rabbitmq.client.*;
import com.rmb938.mn2.docker.db.database.NodeLoader;
import com.rmb938.mn2.docker.db.database.ServerLoader;
import com.rmb938.mn2.docker.db.database.ServerTypeLoader;
import com.rmb938.mn2.docker.db.entity.MN2Node;
import com.rmb938.mn2.docker.db.entity.MN2Server;
import com.rmb938.mn2.docker.db.entity.MN2ServerType;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.IOException;

@Log4j2
public class SlaveLoopWorker {

    private final ServerTypeLoader serverTypeLoader;
    private final ServerLoader serverLoader;
    private final NodeLoader nodeLoader;
    private final SlaveConsumer consumer;
    private final Connection connection;
    private Channel channel;
    private final ObjectId _myServerTypeId;
    private final ObjectId _myNodeId;

    public SlaveLoopWorker(MN2ServerType serverType, MN2Node node, Connection connection, ServerTypeLoader serverTypeLoader, ServerLoader serverLoader, NodeLoader nodeLoader) throws Exception {
        _myServerTypeId = serverType.get_id();
        _myNodeId = node.get_id();
        this.connection = connection;
        channel = connection.createChannel();
        connection.addShutdownListener(Throwable::printStackTrace);
        try {
            log.info("Connecting to Queue "+serverType.getName()+"-server-worker");
            channel.queueDeclarePassive(serverType.getName() + "-server-worker");
        } catch (IOException e) {
            channel = connection.createChannel();
            log.info("Creating Queue "+serverType.getName()+"-server-worker");
            channel.queueDeclare(serverType.getName()+"-server-worker", true, false, true, null);
        }
        channel.basicQos(1);
        consumer = new SlaveConsumer(channel);
        channel.basicConsume(serverType.getName()+"-server-worker", false, consumer);
        this.serverTypeLoader = serverTypeLoader;
        this.serverLoader = serverLoader;
        this.nodeLoader = nodeLoader;
    }

    public void stopWorking() {
        try {
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
        public void handleRecoverOk(String consumerTag) {

        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            JSONObject object = new JSONObject(new String(body));
            log.info("Received Server build request "+object);

            if (object.getInt("ttl") <= 0) {
                log.error("TTL for " + object + " is 0. Dropping Build request");
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }
            object.put("ttl", object.getInt("ttl")-1);

            MN2ServerType serverType = serverTypeLoader.loadEntity(_myServerTypeId);
            if (serverType == null) {
                log.error("Server Type " + _myServerTypeId + " no longer exists destroying build request");
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            MN2Node node = nodeLoader.loadEntity(_myNodeId);
            if (node == null) {
                log.error("Received build message but cannot find my node info");
                channel.basicPublish("", serverType.getName()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            if (!object.getString("type").equals(_myServerTypeId.toString())) {
                log.error("Mismatched Server Type Received: " + object.getString("type") + " Required: " + _myServerTypeId);
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            int currentRamUsage = 0;
            for (MN2Server server : serverLoader.nodeServers(node)) {
                currentRamUsage += server.getServerType().getMemory();
            }

            if ((currentRamUsage+serverType.getMemory()) > node.getRam()) {
                log.error("Not enough memory to create " + serverType.getName() + " re-queuing request");
                channel.basicPublish("", serverType.getName()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            MN2Server server = new MN2Server();
            server.setServerType(serverType);
            server.setNode(node);
            server.setLastUpdate(System.currentTimeMillis()+300000);//set last update to 5 mins from now this should be way more then enough to setup the server container

            try {
                ObjectId serverId = serverLoader.insertEntity(server);
                server = serverLoader.loadEntity(serverId);
            } catch (Exception ex) {
                if (ex instanceof DuplicateKeyException) {
                    log.error("Error inserting new server for " + serverType.getName() + " duplicate");
                    channel.basicPublish("", serverType.getName() + "-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                    channel.basicAck(envelope.getDeliveryTag(), false);
                }
                log.error("Error inserting new server for " + serverType.getName() + " " + ex.getMessage());
                channel.basicPublish("", serverType.getName() + "-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            if (server == null) {
                log.error("Created server is null");
                channel.basicPublish("", serverType.getName()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            DockerClient dockerClient = new DockerClient("http://"+node.getAddress()+":4243");
            ContainerCreateResponse response;
            try {
                log.info("Creating container for "+serverType.getName());
                response = dockerClient.createContainerCmd("mnsquared/server")
                        .withEnv("MONGO_HOSTS=" + System.getenv("MONGO_HOSTS"),
                                "RABBITMQ_HOSTS=" + System.getenv("RABBITMQ_HOSTS"),
                                "RABBITMQ_USERNAME=" + System.getenv("RABBITMQ_USERNAME"),
                                "RABBITMQ_PASSWORD=" + System.getenv("RABBITMQ_PASSWORD"),
                                "RACKSPACE_USERNAME=" + System.getenv("RACKSPACE_USERNAME"),
                                "RACKSPACE_API=" + System.getenv("RACKSPACE_API"),
                                "MY_SERVER_ID=" + server.get_id().toString())
                        .withName(serverType.getName()+"."+server.getNumber())
                        .exec();
            } catch (Exception ex) {
                ex.printStackTrace();
                log.error("Unable to create container for server " + serverType.getName());
                channel.basicPublish("", serverType.getName()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            if (response == null) {
                log.error("Null docker response");
                channel.basicPublish("", serverType.getName()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            String containerId = response.getId();
            server.setContainerId(containerId);

            serverLoader.saveEntity(server);

            try {
                log.info("Starting container for "+serverType.getName());
                dockerClient.startContainerCmd(containerId).withPublishAllPorts(true).withBinds(new Bind("/mnt/cloudfiles", new Volume("/mnt/cloudfiles"))).exec();
            } catch (Exception ex) {
                log.error("Unable to start container for server " + serverType.getName());
                channel.basicPublish("", serverType.getName()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicAck(envelope.getDeliveryTag(), false);
                return;
            }

            channel.basicAck(envelope.getDeliveryTag(), false);
        }
    }

}
