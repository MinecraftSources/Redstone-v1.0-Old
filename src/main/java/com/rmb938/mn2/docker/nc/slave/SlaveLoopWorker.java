package com.rmb938.mn2.docker.nc.slave;

import com.github.dockerjava.client.DockerClient;
import com.github.dockerjava.client.model.ContainerCreateResponse;
import com.github.dockerjava.client.model.ExposedPort;
import com.rabbitmq.client.*;
import com.rmb938.mn2.docker.nc.database.NodeLoader;
import com.rmb938.mn2.docker.nc.database.ServerLoader;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.entity.Node;
import com.rmb938.mn2.docker.nc.entity.Server;
import com.rmb938.mn2.docker.nc.entity.ServerType;
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
    private Channel channel;
    private final ObjectId _myServerTypeId;
    private final ObjectId _myNodeId;

    public SlaveLoopWorker(ServerType serverType, Node node, Connection connection, ServerTypeLoader serverTypeLoader, ServerLoader serverLoader, NodeLoader nodeLoader) throws Exception {
        _myServerTypeId = serverType.get_id();
        _myNodeId = node.get_id();
        channel = connection.createChannel();
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
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*@Override
    public void run() {
        while (true) {
            try {
                log.info("Waiting for Delivery");
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();
                ServerType serverType = serverTypeLoader.loadEntity(_myServerTypeId);
                if (serverType == null) {
                    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    break;
                }

                JSONObject object = new JSONObject(new String(delivery.getBody()));
                log.info("Received Server build request "+object);

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (InterruptedException | IOException e) {
                //e.printStackTrace();
                log.info("Stopping Consumer");
                break;
            }
        }
        try {
            channel.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }*/

    class SlaveConsumer extends DefaultConsumer {

        public SlaveConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleRecoverOk(String consumerTag) {

        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            Node node = nodeLoader.loadEntity(_myNodeId);

            if (node == null) {
                log.error("Received build message but cannot find my node info");
                channel.basicNack(envelope.getDeliveryTag(), false, true);
                return;
            }

            ServerType serverType = serverTypeLoader.loadEntity(_myServerTypeId);
            if (serverType == null) {
                log.error("Server Type " + _myServerTypeId + " no longer exists destroying build request");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            JSONObject object = new JSONObject(new String(body));
            log.info("Received Server build request "+object);

            if (!object.getString("type").equals(_myServerTypeId.toString())) {
                log.error("Mismatched Server Type Received: " + object.getString("type") + " Required: " + _myServerTypeId);
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            Server server = new Server(serverType, node);
            ObjectId serverId = serverLoader.insertEntity(server);

            DockerClient dockerClient = new DockerClient("http://localhost:4342");
            ContainerCreateResponse response = null;
            try {
                log.info("Creating container for "+serverType.getName());
                response = dockerClient.createContainerCmd("mnsquared/server")
                        .withEnv("MONGO_HOSTS="+System.getenv("MONGO_HOSTS"))
                        .withEnv("MONGO_DB="+System.getenv("MONGO_DB"))
                        .withEnv("RABBITMQ_HOSTS="+System.getenv("RABBITMQ_HOSTS"))
                        .withEnv("RABBITMQ_USERNAME="+System.getenv("RABBITMQ_USERNAME"))
                        .withEnv("RABBITMQ_PASSWORD="+System.getenv("RABBITMQ_PASSWORD"))
                        .withEnv("MY_SERVER_ID="+serverId.toString())
                        .exec();
            } catch (Exception ex) {
                log.error("Unable to create container for server "+serverType.getName());
                channel.basicNack(envelope.getDeliveryTag(), false, true);
                return;
            }

            if (response == null) {
                log.error("Null docker response");
                channel.basicNack(envelope.getDeliveryTag(), false, true);
                return;
            }

            server = serverLoader.loadEntity(serverId);

            if (server == null) {
                log.error("Created server is null");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            String containerId = response.getId();
            server.setContainerId(containerId);
            server.setLastUpdate(System.currentTimeMillis()+300000);//set last update to 5 mins from now this should be way more then enough to setup the server container

            serverLoader.saveEntity(server);

            try {
                log.info("Starting container for "+serverType.getName());
                dockerClient.startContainerCmd(containerId).withPublishAllPorts(true).exec();
            } catch (Exception ex) {
                log.error("Unable to start container for server "+serverType.getName());
                channel.basicNack(envelope.getDeliveryTag(), false, true);
                return;
            }

            channel.basicAck(envelope.getDeliveryTag(), false);
        }
    }

}
