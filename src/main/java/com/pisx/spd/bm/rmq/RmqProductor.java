package com.pisx.spd.bm.rmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

/**
 * @author QIANTIANYI
 * @date 2021/7/13
 */
public class RmqProductor {

    private String host = "127.0.0.1";
    private int port = 5672;
    private String username;
    private String password;
    private String vhost = "/";

    private Connection conn;
    private Channel channel;

    private boolean inited = false;


    public void init() throws IOException, TimeoutException {
        conn = RmqUtil.getConnection(host, port, username, password, vhost);
        channel = conn.createChannel();
        this.inited = true;
    }

    public RmqProductor setHost(String host) {
        this.host = host;
        return this;
    }

    public RmqProductor setPort(int port) {
        this.port = port;
        return this;
    }

    public RmqProductor setUsername(String username) {
        this.username = username;
        return this;
    }

    public RmqProductor setPassword(String password) {
        this.password = password;
        return this;
    }

    public RmqProductor setVhost(String vhost) {
        this.vhost = vhost;
        return this;
    }

    public boolean isInited() {
        return inited;
    }

    public void send(String exchange, String routingKey, String body) throws IOException {
        AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder().deliveryMode(2).
                contentEncoding("UTF-8").build();

        //第一个参数是exchange参数，如果是为空字符串，那么就会发送到(AMQP default)默认的exchange，而且routingKey
        //便是所要发送到的队列名
        channel.basicPublish(exchange, routingKey, properties, body.getBytes());
    }


    public void close() throws IOException, TimeoutException {
        channel.close();
        conn.close();
        this.inited = false;
    }
}


