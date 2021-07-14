package com.pisx.spd.bm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.pisx.spd.bm.rmq.RmqProductor;
import com.pisx.spd.bm.rmq.RmqReceiver;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.*;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * @author QIANTIANYI
 * @date 2021/7/13
 */
public class StartApplication extends Application {

    public static Stage stage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getClassLoader().getResource("main.fxml")));
        primaryStage.setTitle("BM Rabbit MQ Init");
        Scene scene = new Scene(root);
        primaryStage.setScene(scene);
        stage = primaryStage;
        primaryStage.show();
    }

    private static void doProduce() throws IOException, TimeoutException {
        RmqProductor rmqProductor = new RmqProductor()
                .setHost("qiantianyi.cn")
                .setPort(5672)
                .setUsername("admin")
                .setPassword("admin")
                .setVhost("test");
        rmqProductor.init();
        String filePath = "E:\\PISX\\testDir\\";
        File dir = new File(filePath);
        List<File> files = Arrays.asList(dir.listFiles());
        files
                .stream()
                .map(file -> {
                    try {
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        return reader.lines().collect(Collectors.joining());
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                    return "";
                })
                .flatMap(x -> {
                    try {
                        return JSON.parseArray(x).toJavaList(JSONObject.class).stream();
                    }catch (Exception e){
                        return JSON.parseArray(x).toJavaList(String.class).stream().map(JSON::parseObject);
                    }
                })
                .filter(jsonObject->{
                    String subClass = jsonObject.getString("subClass");
                    if (!isEmpty(subClass) && "user".equals(subClass)) {
                        String content = jsonObject.getString("content");
                        if (!isEmpty(content)) {
                            JSONObject userinfo = JSONObject.parseObject(content);
                            int userStatusEnum = userinfo.getIntValue("userStatusEnum");
                            if (1 == userStatusEnum) {
                               return true;
                            }
                        }
                    }
                    return false;
                })
                .map(JSONObject::toString)
                .collect(Collectors.toList())
                .forEach(s-> {
                    try {
                        rmqProductor.send("test.input", "IN", s);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        rmqProductor.close();
        System.out.println("produce Done");
    }

    private static boolean isEmpty(String str){
        return str==null || "".equals(str.trim());
    }

    private static RmqReceiver doReceive() throws IOException, TimeoutException {
        RmqReceiver rmqReceiver = new RmqReceiver()
                .setHost("qiantianyi.cn")
                .setPort(5672)
                .setUsername("admin")
                .setPassword("admin")
                .setBasePath("E:\\PISX\\testDir_1\\")
                .setVhost("/");
        rmqReceiver.startWork();
        rmqReceiver.receive("orgUserSyncfaw_bm_qty");
        return rmqReceiver;
    }


}
