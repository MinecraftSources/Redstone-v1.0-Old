package com.rmb938.mn2.docker.nc.slave;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import com.rmb938.mn2.docker.db.rabbitmq.RabbitMQ;
import com.rmb938.mn2.docker.nc.database.ServerTypeLoader;
import com.rmb938.mn2.docker.nc.entity.ServerType;
import org.bson.types.ObjectId;
import org.json.JSONObject;

import java.io.IOException;

public class SlaveLoopWorker implements Runnable {

    private final ServerTypeLoader serverTypeLoader;
    private final QueueingConsumer consumer;
    private final Channel channel;
    private final ObjectId _myServerTypeId;

    public SlaveLoopWorker(ServerType serverType, RabbitMQ rabbitMQ, ServerTypeLoader serverTypeLoader) throws Exception {
        _myServerTypeId = serverType.get_id();
        channel = rabbitMQ.getChannel();
        try {
            channel.queueDeclarePassive(serverType.getName()+"-worker");
        } catch (IOException e) {
            channel.queueDeclare(serverType.getName()+"-worker", true, false, true, null);
        }
        channel.basicQos(1);
        consumer = new QueueingConsumer(channel);
        channel.basicConsume(serverType.getName()+"-worker", false, consumer);
        this.serverTypeLoader = serverTypeLoader;
    }

    @Override
    public void run() {
        ServerType serverType = serverTypeLoader.loadEntity(_myServerTypeId);
        while (serverType != null) {
            try {
                QueueingConsumer.Delivery delivery = consumer.nextDelivery();

                JSONObject object = new JSONObject(delivery.getBody());

                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
        try {
            channel.basicCancel(consumer.getConsumerTag());
            channel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
