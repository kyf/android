package com.kyf.goserver;

import android.os.Environment;

import go.httpserver.Httpserver;

/**
 * Created by keyf on 2015/12/15.
 */
public class InstanceHttpServer {

    public static Httpserver.HttpServer instance;

    private InstanceHttpServer(){

    }

    public static Httpserver.HttpServer getInstance(String port){
        if(instance == null){
            String sdcard = Environment.getExternalStorageDirectory().toString();
            instance = Httpserver.NewServer(port, sdcard);
        }

        return instance;
    }
}
