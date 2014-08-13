package com.rmb938.mn2.docker.nc.slave;

import com.github.dockerjava.client.DockerClient;
import com.github.dockerjava.client.command.CreateContainerResponse;
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
import java.util.HashMap;

@Log4j2
public class SlaveLoopWorker {

    private final ServerTypeLoader serverTypeLoader;
    private final ServerLoader serverLoader;
    private final NodeLoader nodeLoader;
    private SlaveConsumer consumer;
    private final Connection connection;
    private Channel channel;
    private final ObjectId _myServerTypeId;
    private final ObjectId _myNodeId;
    private boolean stop = false;

    public SlaveLoopWorker(MN2ServerType serverType, MN2Node node, Connection connection, ServerTypeLoader serverTypeLoader, ServerLoader serverLoader, NodeLoader nodeLoader) throws Exception {
        _myServerTypeId = serverType.get_id();
        _myNodeId = node.get_id();
        this.connection = connection;
        channel = connection.createChannel();
        connection.addShutdownListener(Throwable::printStackTrace);
        consumerSetup();
        this.serverTypeLoader = serverTypeLoader;
        this.serverLoader = serverLoader;
        this.nodeLoader = nodeLoader;
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

            MN2ServerType serverType = serverTypeLoader.loadEntity(_myServerTypeId);
            if (serverType == null) {
                log.error("Server Type " + _myServerTypeId + " no longer exists destroying build request");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            MN2Node node = nodeLoader.loadEntity(_myNodeId);
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

            int currentRamUsage = 0;
            for (MN2Server server : serverLoader.nodeServers(node)) {
                currentRamUsage += server.getServerType().getMemory();
            }

            if ((currentRamUsage+serverType.getMemory()) > node.getRam()) {
                log.error("Not enough memory to create " + serverType.getName() + " re-queuing request");
                channel.basicPublish("", serverType.get_id()+"-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
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
                    channel.basicPublish("", serverType.get_id() + "-server-worker", MessageProperties.PERSISTENT_TEXT_PLAIN, object.toString().getBytes());
                    channel.basicNack(envelope.getDeliveryTag(), false, false);
                }
                log.error("Error inserting new server for " + serverType.getName() + " " + ex.getMessage());
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

            DockerClient dockerClient = new DockerClient("http://"+node.getAddress()+":4243");
            CreateContainerResponse response;
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

            String containerId = response.getId();
            server.setContainerId(containerId);

            serverLoader.saveEntity(server);

            try {
                log.info("Starting container for "+serverType.getName());
                dockerClient.startContainerCmd(containerId).withPublishAllPorts(true).withBinds(new Bind("/mnt/cloudfiles", new Volume("/mnt/cloudfiles"))).exec();
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
