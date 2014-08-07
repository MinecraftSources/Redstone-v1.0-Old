package com.rmb938.mn2.docker.nc.slave;

import com.github.dockerjava.client.DockerClient;
import com.github.dockerjava.client.model.*;
import com.rabbitmq.client.*;
import com.rmb938.mn2.docker.db.database.BungeeLoader;
import com.rmb938.mn2.docker.db.database.BungeeTypeLoader;
import com.rmb938.mn2.docker.db.database.NodeLoader;
import com.rmb938.mn2.docker.db.entity.MN2Bungee;
import com.rmb938.mn2.docker.db.entity.MN2BungeeType;
import com.rmb938.mn2.docker.db.entity.MN2Node;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import javax.xml.soap.Node;
import java.io.IOException;
import java.util.HashMap;

@Log4j2
public class BungeeLoopWorker {

    private Channel channel;
    private final ObjectId _myNodeId;
    private final BungeeTypeLoader bungeeTypeLoader;
    private final BungeeLoader bungeeLoader;
    private final NodeLoader nodeLoader;

    public BungeeLoopWorker(ObjectId myNodeId, RabbitMQ rabbitMQ, BungeeTypeLoader bungeeTypeLoader, BungeeLoader bungeeLoader, NodeLoader nodeLoader) throws IOException {
        _myNodeId = myNodeId;

        Connection connection = rabbitMQ.getConnection();
        channel = connection.createChannel();

        this.bungeeTypeLoader = bungeeTypeLoader;
        this.bungeeLoader = bungeeLoader;
        this.nodeLoader = nodeLoader;
        consumerSetup();
    }

    private void consumerSetup() throws IOException {
        String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, "bungee-worker", _myNodeId.toString());
        //channel.basicQos(1);
        BungeeConsumer consumer = new BungeeConsumer(channel);
        channel.basicConsume(queueName, false, consumer);
    }

    class BungeeConsumer extends DefaultConsumer {

        public BungeeConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            JSONObject object = new JSONObject(new String(body));
            log.info("Received Bungee build request "+object);

            MN2Node node = nodeLoader.loadEntity(_myNodeId);
            if (node == null) {
                log.error("Received build message but cannot find my node info");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
            }

            ObjectId objectId = new ObjectId(object.getString("type"));
            MN2BungeeType bungeeType = bungeeTypeLoader.loadEntity(objectId);
            if (bungeeType == null) {
                log.error("Bungee Type " + object.get("type") + " no longer exists destroying build request");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            MN2Bungee bungee = new MN2Bungee();
            bungee.setNode(node);
            bungee.setBungeeType(bungeeType);
            bungee.setLastUpdate(System.currentTimeMillis() + 300000);

            bungeeLoader.saveEntity(bungee);

            DockerClient dockerClient = new DockerClient("http://"+node.getAddress()+":4243");
            ContainerCreateResponse response;
            try {
                log.info("Creating container for "+bungeeType.getName());
                response = dockerClient.createContainerCmd("mnsquared/bungee")
                        .withEnv("MONGO_HOSTS=" + System.getenv("MONGO_HOSTS"),
                                "RABBITMQ_HOSTS=" + System.getenv("RABBITMQ_HOSTS"),
                                "RABBITMQ_USERNAME=" + System.getenv("RABBITMQ_USERNAME"),
                                "RABBITMQ_PASSWORD=" + System.getenv("RABBITMQ_PASSWORD"),
                                "RACKSPACE_USERNAME=" + System.getenv("RACKSPACE_USERNAME"),
                                "RACKSPACE_API=" + System.getenv("RACKSPACE_API"),
                                "MY_BUNGEE_ID=" + bungee.get_id().toString())
                        .withName(bungeeType.getName())
                        .exec();
            } catch (Exception ex) {
                ex.printStackTrace();
                log.error("Unable to create container for bungee " + bungeeType.getName());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            if (response == null) {
                log.error("Null docker response");
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            String containerId = response.getId();
            bungee.setContainerId(containerId);

            bungeeLoader.saveEntity(bungee);

            try {
                log.info("Starting container for "+bungeeType.getName());
                dockerClient.startContainerCmd(containerId).withPortBindings(new Ports(new ExposedPort("tcp", 25565), new Ports.Binding("0.0.0.0", 25565)))
                        .withBinds(new Bind("/mnt/cloudfiles", new Volume("/mnt/cloudfiles"))).exec();
            } catch (Exception ex) {
                log.error("Unable to start container for bungee " + bungeeType.getName());
                channel.basicNack(envelope.getDeliveryTag(), false, false);
                return;
            }

            channel.basicAck(envelope.getDeliveryTag(), false);
        }

        @Override
        public void handleRecoverOk(String consumerTag) {
        }

        @Override
        public void handleCancel(String consumerTag) throws IOException {
            consumerSetup();
        }
    }

}
