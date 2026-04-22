package com.musicplayer.scamusica.service;
import com.musicplayer.scamusica.util.Utility;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class ConnectivityMonitor {

    public enum Status { ONLINE, OFFLINE }

    private volatile boolean running = false;
    private final Consumer<Status> listener;
    private Status lastStatus = null;

    public ConnectivityMonitor(Consumer<Status> listener) {
        this.listener = listener;
    }

    public void start() {
        running = true;
        new Thread(this::loop, "ConnectivityMonitor").start();
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        while (running) {
            Status current = checkApiConnectivity();

            if (current != lastStatus) {
                lastStatus = current;
                listener.accept(current);
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {}
        }
    }

    private Status checkApiConnectivity() {
        try {
            URL url = new URL(Utility.BASE_URL.get());
            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setConnectTimeout(2000);
            con.setReadTimeout(2000);
            con.connect();

            int code = con.getResponseCode();
            return (code >= 200 && code < 500)
                    ? Status.ONLINE
                    : Status.OFFLINE;

        } catch (Exception e) {
            e.printStackTrace();
            return Status.OFFLINE;
        }
    }
}
