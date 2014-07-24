package com.rmb938.mn2.docker.nc.slave;

import com.rabbitmq.client.*;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import lombok.extern.log4j.Log4j2;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.IOException;

@Log4j2
public class SlaveLoopWorker {

    private final ServerTypeLoader serverTypeLoader;
    private final SlaveConsumer consumer;
    private Channel channel;
    private final ObjectId _myServerTypeId;

    public SlaveLoopWorker(ServerType serverType, Connection connection, ServerTypeLoader serverTypeLoader) throws Exception {
        _myServerTypeId = serverType.get_id();
        channel = connection.createChannel();
        try {
            log.info("Connecting to Queue "+serverType.getName()+"-worker");
            channel.queueDeclarePassive(serverType.getName() + "-worker");
        } catch (IOException e) {
            channel = connection.createChannel();
            log.info("Creating Queue "+serverType.getName()+"-worker");
            channel.queueDeclare(serverType.getName()+"-worker", true, false, true, null);
        }
        channel.basicQos(1);
        consumer = new SlaveConsumer(channel);
        channel.basicConsume(serverType.getName()+"-worker", false, consumer);
        this.serverTypeLoader = serverTypeLoader;
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
            ServerType serverType = serverTypeLoader.loadEntity(_myServerTypeId);
            if (serverType == null) {
                channel.basicAck(envelope.getDeliveryTag(), false);
            }

            JSONObject object = new JSONObject(new String(body));
            log.info("Received Server build request "+object);

            channel.basicAck(envelope.getDeliveryTag(), false);
        }
    }

}
