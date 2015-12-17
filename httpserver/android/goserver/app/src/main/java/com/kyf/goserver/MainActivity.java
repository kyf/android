package com.kyf.goserver;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.content.ClipboardManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import go.httpserver.Httpserver;

public class MainActivity extends Activity implements View.OnClickListener {

    public static final int HANDLER_ERR = 1001;

    public static final int HANDLER_MSG = 1002;

    private static final String port = "8096";

    private Thread thread;

    private TextView monitor, tips;

    private String access_url = "";

    private Httpserver.HttpServer httpServer;

    private Handler myHandler = new Handler(){
        @Override
        public void handleMessage(Message msg){
            switch(msg.what){
                case HANDLER_ERR:{
                    Toast.makeText(MainActivity.this, msg.obj.toString(), Toast.LENGTH_SHORT).show();
                }
                case HANDLER_MSG:{
                    String separator = System.getProperty("line.separator");
                    String last = monitor.getText().toString() + msg.obj.toString() + separator;
                    String[] lines = last.split(separator);
                    int limit = 15;

                    if(lines.length > limit) {
                        last = lines[1] + separator;
                        for (int index = 2; index < lines.length; index++) {
                            last += lines[index] + separator;
                        }
                    }

                    monitor.setText(last);
                    monitor.invalidate();
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        httpServer = InstanceHttpServer.getInstance(port);

        init();
    }

    private void init(){
        tips = (TextView) findViewById(R.id.tips);
        monitor = (TextView) findViewById(R.id.monitor);
        startListenMsg();
        String ip = getWifiIp();
        if(ip.equals("")){
            access_url = "http://localhost:" + port + "/";
            tips.setText("服务未启动，当前非WIFI环境，只能本机" + access_url + "访问");
        }else {
            access_url = "http://" + ip + ":" + port + "/" ;
            tips.setText("服务未启动，访问地址" + access_url );
        }

        Button statebt = (Button) findViewById(R.id.statebt);
        Button startbt = (Button) findViewById(R.id.startbt);
        Button stopbt = (Button) findViewById(R.id.stopbt);

        statebt.setOnClickListener(this);
        startbt.setOnClickListener(this);
        stopbt.setOnClickListener(this);
        tips.setOnClickListener(this);
    }

    private String getWifiIp(){
        WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        int state = wifiManager.getWifiState();
        if(state != WifiManager.WIFI_STATE_ENABLED){
            return "";
        }
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        int addr = wifiInfo.getIpAddress();
        return String.format("%d.%d.%d.%d", addr & 0xff, addr >> 8 & 0xff, addr >> 16 & 0xff, addr >> 24 & 0xff);
    }

    private void startServer(){
        if(httpServer.State() == 0) {
            thread = new Thread(new Runnable(){
                @Override
                public void run(){
                    Log.e("6renyou", "enter thread ...");
                    String result = httpServer.Start();
                    if (result != null && !result.isEmpty()) {
                        Message msg = Message.obtain();
                        msg.what = HANDLER_ERR;
                        msg.obj = result;
                        myHandler.sendMessage(msg);
                    }
                }
            });
            thread.start();
        }
    }

    private void startListenMsg(){
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    String result = httpServer.ReadMsg();
                    if (result != null && !result.isEmpty()) {
                        Message msg = Message.obtain();
                        msg.what = HANDLER_MSG;
                        msg.obj = result;
                        myHandler.sendMessage(msg);
                    }
                }
            }
        }).start();
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch(id){
            case R.id.tips:{
                ClipboardManager clipboardManager = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clipData = ClipData.newPlainText("url", access_url);
                clipboardManager.setPrimaryClip(clipData);
                Toast.makeText(this, "地址已复制剪贴板", Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.startbt:{
                startServer();
                break;
            }
            case R.id.statebt:{
                String state = httpServer.State() == 1 ? "启动" : "未启动";
                Toast.makeText(this, "当前状态：" + state, Toast.LENGTH_SHORT).show();
                break;
            }
            case R.id.stopbt:{
                httpServer.Stop();
                break;
            }
        }
    }
}
