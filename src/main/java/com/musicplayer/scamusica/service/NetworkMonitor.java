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

/**
 * Monitors internet connectivity and exposes a JavaFX observable property.
 * Checks every 5 seconds by making a lightweight HTTP HEAD request.
 */
public class NetworkMonitor {

    // Singleton instance
    private static NetworkMonitor instance;

    // Observable property — UI binds to this
    private final BooleanProperty online = new SimpleBooleanProperty(false);

    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    // URL used for connectivity ping (lightweight, reliable)
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

    /**
     * Returns the observable online property.
     * Bind your UI elements to this.
     */
    public BooleanProperty onlineProperty() {
        return online;
    }

    public boolean isOnline() {
        return online.get();
    }

    /**
     * Start monitoring. Safe to call multiple times.
     */
    public void start() {
        if (running) return;
        running = true;

        // Check immediately on start
        checkConnectivity();

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "NetworkMonitor");
            t.setDaemon(true); // Won't block app shutdown
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

    /**
     * Stop monitoring. Call on app close.
     */
    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        AppLogger.log("[NetworkMonitor] Stopped");
    }

    /**
     * Performs the actual connectivity check.
     * Runs on background thread, updates property on FX thread.
     */
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
            // 204 = Google's no-content response, 200/301 also valid
            return (responseCode == 204 || responseCode == 200 || responseCode == 301);
        } catch (IOException e) {
            return false;
        }
    }
}