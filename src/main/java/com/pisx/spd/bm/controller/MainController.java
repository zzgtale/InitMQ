package com.pisx.spd.bm.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.pisx.spd.bm.StartApplication;
import com.pisx.spd.bm.rmq.RmqProductor;
import com.pisx.spd.bm.rmq.RmqReceiver;
import com.pisx.spd.bm.util.Md5Utils;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author QIANTIANYI
 * @date 2021/7/13
 */
public class MainController implements Initializable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainController.class);

    @FXML
	public TextField hostField1;
    @FXML
	public TextField portField1;
    @FXML
	public TextField userNameField1;
    @FXML
	public TextField vhostField1;
    @FXML
	public PasswordField passwordField1;
    @FXML
	public TextField exchangeField;
    @FXML
	public TextField routingKeyField;
    @FXML
    private TextField sourceDirField;

    @FXML
    public Label currentCount;


    private static final Map<String, RmqProductor> productorMap = new HashMap<>();


    public void sendMessage() throws IOException, TimeoutException {
        RmqProductor rmqProductor = getRmqProductor();
        if (rmqProductor!=null){
            if (!rmqProductor.isInited()){
                rmqProductor.init();
            }
            String exchange = exchangeField.getText();
            String routingKey = routingKeyField.getText();
            String sourceDir = sourceDirField.getText();
            send(rmqProductor,sourceDir,exchange,routingKey);
        }
    }


    private void send(RmqProductor rmqProductor,String filePath,String exchange,String routingKey) throws IOException, TimeoutException {
        File dir = new File(filePath);
        List<File> files = Arrays.asList(dir.listFiles());
        AtomicLong count = new AtomicLong(0L);
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
                    } catch (Exception e) {
                        return JSON.parseArray(x).toJavaList(String.class).stream().map(JSON::parseObject);
                    }
                })
                .filter(jsonObject -> {
                    String subClass = jsonObject.getString("subClass");
                    if (!isEmpty(subClass) && "user".equals(subClass)) {
                        String content = jsonObject.getString("content");
                        if (!isEmpty(content)) {
                            JSONObject userinfo = JSONObject.parseObject(content);
                            int userStatusEnum = userinfo.getIntValue("userStatusEnum");
                            return 1 == userStatusEnum;
                        }
                    }
                    return false;
                })
                .map(JSONObject::toString)
                .forEach(s-> {
                    try {
                        count.getAndIncrement();
                        rmqProductor.send(exchange, routingKey, s);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        LOGGER.info("Publish Done,Total Count:{}",count.get());
        rmqProductor.close();
    }


    public RmqProductor getRmqProductor(){
        if (!checkProductor()){
            return null;
        }
        String host = hostField1.getText();
        String port = portField1.getText();
        String userName = userNameField1.getText();
        String password = passwordField1.getText();
        String vhost = vhostField1.getText();

        String key = generateMd5(host, port, userName, password, vhost);
        RmqProductor rmqProductor = null;
        if (productorMap.containsKey(key)) {
            rmqProductor = productorMap.get(key);

        } else {
            rmqProductor = new RmqProductor()
                    .setHost(host).setPort(Integer.parseInt(port))
                    .setUsername(userName).setPassword(password).setVhost(vhost);
            productorMap.put(key, rmqProductor);
        }
        return rmqProductor;
    }

    public boolean checkProductor() {
        String host = hostField1.getText();
        String port = portField1.getText();
        String userName = userNameField1.getText();
        String password = passwordField1.getText();
        String vhost = vhostField1.getText();
        String exchange = exchangeField.getText();
        String routingKey = routingKeyField.getText();
        String sourceDir = sourceDirField.getText();
        if (isEmpty(host)) {
            errAlert.setContentText("Host is Empty");
            errAlert.show();
            return false;
        }
        if (!isEmpty(port)) {
            try {
                Integer.parseInt(port);
            } catch (Exception e) {
                errAlert.setContentText("Port is not Number");
                errAlert.show();
                return false;
            }
        }else {
            errAlert.setContentText("Port is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(userName)) {
            errAlert.setContentText("UserName is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(password)) {
            errAlert.setContentText("Password is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(exchange)) {
            errAlert.setContentText("Exchange is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(routingKey)) {
            errAlert.setContentText("RoutingKey is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(vhost)) {
            errAlert.setContentText("Vhost is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(sourceDir)) {
            errAlert.setContentText("SourceDir is Empty");
            errAlert.show();
            return false;
        }
        return true;

    }



    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private TextField userNameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField vhostField;
    @FXML
    private TextField queueNameField;
    @FXML
    private TextField targetDirField;





    private Alert errAlert = new Alert(Alert.AlertType.ERROR);
    DirectoryChooser directoryChooser = new DirectoryChooser();


    private static final Map<String, RmqReceiver> receiverMap = new HashMap<>();


    public boolean checkReceiver() {
        String host = hostField.getText();
        String port = portField.getText();
        String userName = userNameField.getText();
        String password = passwordField.getText();
        String vhost = vhostField.getText();
        String queueName = queueNameField.getText();
        String targetDir = targetDirField.getText();
        if (isEmpty(host)) {
            errAlert.setContentText("Host is Empty");
            errAlert.show();
            return false;
        }
        if (!isEmpty(port)) {
            try {
                Integer.parseInt(port);
            } catch (Exception e) {
                errAlert.setContentText("Port is not Number");
                errAlert.show();
                return false;
            }
        }else {
            errAlert.setContentText("Port is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(userName)) {
            errAlert.setContentText("UserName is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(password)) {
            errAlert.setContentText("Password is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(queueName)) {
            errAlert.setContentText("QueueName is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(vhost)) {
            errAlert.setContentText("Vhost is Empty");
            errAlert.show();
            return false;
        }
        if (isEmpty(targetDir)) {
            errAlert.setContentText("TargetDir is Empty");
            errAlert.show();
            return false;
        }
        return true;
    }


    private boolean isEmpty(String str) {
        return str == null || "".equals(str.trim());
    }


    public void closeReceiver() throws IOException, TimeoutException {
        RmqReceiver rmqReceiver = getRmqReceiver();
        if (rmqReceiver!=null){
            if (rmqReceiver.isInited()){
                rmqReceiver.close();
            }
        }
    }

    public void doWriteFile() throws IOException, TimeoutException {
        RmqReceiver rmqReceiver = getRmqReceiver();
        if (rmqReceiver!=null){
            if (rmqReceiver.isInited()){
                rmqReceiver.writeFile();
            }else {
                errAlert.setContentText("Receiver Doesn't Init");
            }
        }
    }



    public void doReceiver() throws IOException, TimeoutException {
        RmqReceiver rmqReceiver = getRmqReceiver();
        if (rmqReceiver!=null){
            String queueName = queueNameField.getText();
            if (!rmqReceiver.isInited()){
                rmqReceiver.init();
                rmqReceiver.startWork();
                rmqReceiver.receive(queueName);
            }
        }
    }

    private RmqReceiver getRmqReceiver() throws IOException, TimeoutException {
        if (!checkReceiver()) {
            return null;
        }
        String host = hostField.getText();
        String port = portField.getText();
        String userName = userNameField.getText();
        String password = passwordField.getText();
        String vhost = vhostField.getText();
        String targetDir = targetDirField.getText();

        String key = generateMd5(host, port, userName, password, vhost);
        RmqReceiver rmqReceiver = null;
        if (receiverMap.containsKey(key)) {
            rmqReceiver = receiverMap.get(key);

        } else {
            rmqReceiver = new RmqReceiver()
                    .setHost(host).setPort(Integer.parseInt(port))
                    .setUsername(userName).setPassword(password).setVhost(vhost).setBasePath(targetDir);
            receiverMap.put(key, rmqReceiver);
        }
        return rmqReceiver;
    }

    private String generateMd5(String... strs) {
        String collect = Arrays.asList(strs).stream().collect(Collectors.joining(","));
        return Md5Utils.hash(collect);
    }

    public void chooseTargetDir() {
        try {
            File file = directoryChooser.showDialog(StartApplication.stage);
            targetDirField.setText(file.getAbsolutePath());
        } catch (Exception e) {
        }
    }
    public void chooseSourceDir() {
        try {
            File file = directoryChooser.showDialog(StartApplication.stage);
            sourceDirField.setText(file.getAbsolutePath());
        } catch (Exception e) {
        }
    }


    @Override
    public void initialize(URL location, ResourceBundle resources) {

    }
}
