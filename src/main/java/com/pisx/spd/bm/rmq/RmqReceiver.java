package com.pisx.spd.bm.rmq;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Vector;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author QIANTIANYI
 * @date 2021/7/13
 */
public class RmqReceiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(RmqReceiver.class);

    private long intervalTime = 10000;
    private String host = "127.0.0.1";
    private int port = 5672;
    private String username;
    private String password;
    private String vhost = "/";
    private String basePath;

    private boolean inited = false;

    Connection conn = null;
    Channel channel = null;

    public RmqReceiver setHost(String host) {
        this.host = host;
        return this;
    }

    public RmqReceiver setPort(int port) {
        this.port = port;
        return this;
    }

    public RmqReceiver setUsername(String username) {
        this.username = username;
        return this;
    }

    public RmqReceiver setPassword(String password) {
        this.password = password;
        return this;
    }

    public RmqReceiver setVhost(String vhost) {
        this.vhost = vhost;
        return this;
    }

    public RmqReceiver setBasePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    public boolean isInited() {
        return inited;
    }

    private Future future;

    private AtomicLong count = new AtomicLong();
    private Vector<JSONObject> vector = new Vector<>();
    private volatile Date lastReceiveDate = new Date();

    private static ThreadPoolExecutor threadPool = null;


    public void init() throws IOException, TimeoutException {
        this.conn = RmqUtil.getConnection(this.host, this.port, this.username, this.password, this.vhost);
        this.channel = this.conn.createChannel();
        this.inited = true;
    }

    static {
        // 线程池维护线程的最少数量
        int corePoolSize = 10;
        // 线程池维护线程的最大数量
        int maximumPoolSize = 20;
        // 线程池维护线程所允许的空闲时间
        long keepAliveTime = 60;
        // 线程池维护线程所允许的空闲时间的单位
        TimeUnit unit = TimeUnit.SECONDS;
        BlockingQueue taskQueue = new ArrayBlockingQueue(10);
        // CallerRunsPolicy策略
        ThreadPoolExecutor.CallerRunsPolicy callerRunsPolicy = new ThreadPoolExecutor.CallerRunsPolicy();
        // 初始化线程池
        threadPool = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, unit, taskQueue, callerRunsPolicy);
    }

    public void startWork() {
        future = threadPool.submit(() -> {
            while (true) {
                try {
                    if (this.lastReceiveDate != null && this.vector.size() != 0 && System.currentTimeMillis() - this.lastReceiveDate.getTime() > TimeUnit.SECONDS.toMillis(20)) {
                        writeFile();
                    }
                    Thread.sleep(this.intervalTime);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void stopWork(){
        future.cancel(false);
    }

    public void writeFile() throws IOException {
        LOGGER.info("Start Write File");
        this.lastReceiveDate = new Date();
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMddHHmmss");
        String collect = JSON.toJSONString(this.vector);
        File file = new File(this.basePath + File.separator + format.format(this.lastReceiveDate) + ".txt");
        BufferedWriter writer = new BufferedWriter(new FileWriter(file));
        writer.write(collect);
        writer.flush();
        writer.close();
        LOGGER.info("End Write File,File List Size:{}",vector.size());
        this.vector.clear();
    }

    public void receive(String queueName) throws IOException {
        String tag = this.channel.basicConsume(queueName, false, new DefaultConsumer(this.channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                try {
                    lastReceiveDate = new Date();
                    long value = count.addAndGet(1L);
                    System.out.println(value);
                    LOGGER.debug("Current Count {}",value);
                    vector.add(JSON.parseObject(new String(body)));
                    if (vector.size() % 10000 == 0 && vector.size() != 0) {
                        writeFile();
                    }
                    channel.basicAck(envelope.getDeliveryTag(), false);
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }
        });
        System.out.println(tag);
    }

    public void close() throws IOException, TimeoutException {
        this.channel.close();
        this.conn.close();
        this.inited = false;
        this.stopWork();
        System.out.println("Receiver's Connection is closed");
    }

}


