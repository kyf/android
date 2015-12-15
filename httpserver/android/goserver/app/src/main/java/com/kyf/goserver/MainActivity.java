package com.kyf.goserver;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;


import go.httpserver.Httpserver;

public class MainActivity extends Activity implements View.OnClickListener {

    public static final int HANDLER_ERR = 1001;

    public static final int HANDLER_MSG = 1002;

    private Thread thread;

    private TextView monitor;

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
                    int limit = 7;

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

        httpServer = InstanceHttpServer.getInstance("8096");

        init();
    }

    private void init(){
        startServer();
        monitor = (TextView) findViewById(R.id.monitor);
        startListenMsg();
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


    }
}
