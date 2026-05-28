package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

public class AdDownloadManager {

    private static final String AD_DIR_NAME = ".scamusica" + File.separator + "ads";

    public static File getAdDir() {
        File dir = new File(System.getProperty("user.home") + File.separator + AD_DIR_NAME);
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    public static File getLocalAdFile(Ad ad) {
        if (ad == null || ad.getId() == null) return null;
        return new File(getAdDir(), "ad-" + ad.getId() + ".mp3");
    }

    public static boolean isAdDownloaded(Ad ad) {
        File f = getLocalAdFile(ad);
        return f != null && f.exists() && f.length() > 1024;
    }

    public static void downloadAd(Ad ad) {
        if (ad == null || ad.getId() == null) return;
        if (isAdDownloaded(ad)) {
            AppLogger.log("[AdDownload] Already exists: ad-" + ad.getId());
            return;
        }

        String audioFile = ad.getAudioFile();
        if (audioFile == null || audioFile.isEmpty()) return;

        String downloadUrl;
        if (audioFile.startsWith("http://") || audioFile.startsWith("https://")) {
            downloadUrl = audioFile;
        } else {
            String encoded = audioFile
                    .replace(" ", "%20")
                    .replace("(", "%28")
                    .replace(")", "%29");
            if (!encoded.startsWith("/")) encoded = "/" + encoded;
            downloadUrl = Utility.BASE_URL.get() + encoded;
        }

        final String finalUrl = downloadUrl;

        Thread t = new Thread(() -> {
            try {
                File outFile = getLocalAdFile(ad);
                AppLogger.log("[AdDownload] Downloading ad-" + ad.getId() + " from: " + finalUrl);

                HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                String token = SessionManager.loadToken();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (InputStream is = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                    AppLogger.log("[AdDownload] Done: ad-" + ad.getId() + " size=" + outFile.length());
                } else {
                    AppLogger.log("[AdDownload] Failed HTTP " + responseCode + " for ad-" + ad.getId());
                }
                conn.disconnect();

            } catch (Exception e) {
                AppLogger.log("[AdDownload] Error downloading ad-" + ad.getId() + ": " + e.getMessage());
            }
        });
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        t.start();
    }

    public static void downloadAllAds(List<Ad> ads) {
        if (ads == null || ads.isEmpty()) return;
        for (Ad ad : ads) {
            downloadAd(ad);
        }
    }
}