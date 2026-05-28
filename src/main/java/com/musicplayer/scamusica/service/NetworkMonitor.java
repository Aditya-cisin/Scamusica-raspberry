package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.util.AppLogger;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NetworkMonitor {

    private static NetworkMonitor instance;

    private final BooleanProperty online = new SimpleBooleanProperty(false);

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    private static final String PING_URL = "https://clients3.google.com/generate_204";
    private static final int TIMEOUT_MS = 3000;
    private static final int CHECK_INTERVAL_SEC = 5;

    private NetworkMonitor() {}

    public static NetworkMonitor getInstance() {
        if (instance == null) {
            instance = new NetworkMonitor();
        }
        return instance;
    }

    public BooleanProperty onlineProperty() {
        return online;
    }

    public boolean isOnline() {
        return online.get();
    }

    public void start() {
        if (running) return;
        running = true;

        checkConnectivity();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NetworkMonitor");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleAtFixedRate(
                this::checkConnectivity,
                CHECK_INTERVAL_SEC,
                CHECK_INTERVAL_SEC,
                TimeUnit.SECONDS
        );

        AppLogger.log("[NetworkMonitor] Started");
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        AppLogger.log("[NetworkMonitor] Stopped");
    }

    private void checkConnectivity() {
        boolean result = pingServer();
        Platform.runLater(() -> {
            if (online.get() != result) {
                online.set(result);
                AppLogger.log("[NetworkMonitor] Status changed → " + (result ? "ONLINE" : "OFFLINE"));
            }
        });
    }

    private boolean pingServer() {
        try {
            HttpURLConnection connection = (HttpURLConnection)
                    new URL(PING_URL).openConnection();
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return (responseCode == 204 || responseCode == 200 || responseCode == 301);
        } catch (IOException e) {
            return false;
        }
    }
}