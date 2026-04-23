package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.Utility;

import java.io.File;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {

    public interface DownloadListener {
        void onDownloadStarted(int songId, File outputFile);

        /**
         * bytesDownloaded: bytes read so far for current file
         * contentLength: total content length in bytes (may be -1 if unknown)
         */
        void onDownloadProgress(int songId, long bytesDownloaded, long contentLength);

        /**
         * Fired when a file was actually downloaded (new file).
         */
        void onDownloadCompleted(int songId, File outputFile);

        /**
         * Fired when an existing file was present and we skipped downloading it.
         */
        void onDownloadSkipped(int songId, File existingFile);

        void onDownloadFailed(int songId, Exception ex);

        void onAllDownloadsFinished();

        void onCancelled();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean cancelled = false;
    private final List<Integer> downloadSequence;
    private final DownloadListener listener;
    private final String downloadFolderPath;

    public DownloadManager(List<Integer> downloadSequence,
                           String downloadFolderPath,
                           DownloadListener listener) {
        this.downloadSequence = downloadSequence == null ? Collections.emptyList() : new ArrayList<>(downloadSequence);
        this.listener = listener;
        this.downloadFolderPath = downloadFolderPath == null ? "./downloads" : downloadFolderPath;
    }

    public void start() {
        cancelled = false;
        executor.submit(this::runDownloadSequence);
    }

    public void stop() {
        cancelled = true;
    }

    public void shutdownNow() {
        cancelled = true;
        executor.shutdownNow();
    }

    private void runDownloadSequence() {
        try {
            File baseDir = new File(downloadFolderPath);
            if (!baseDir.exists()) {
                boolean ok = baseDir.mkdirs();
                if (!ok)
                    System.out.println("[DownloadManager] Could not create download directory: "
                            + baseDir.getAbsolutePath());
            }

            for (Integer id : downloadSequence) {
                if (cancelled) {
                    notifyCancelled();
                    return;
                }
                if (id == null) continue;

                try {
                    File outFile = new File(baseDir, "song-" + id + ".dat");

                    if (outFile.exists() && outFile.length() > 0) {
                        System.out.println("[DownloadManager] File exists, skipping id: " + id);
                        if (listener != null) listener.onDownloadSkipped(id, outFile);
                        continue;
                    }

                    String streamUrl = Utility.BASE_URL.get() + "/api/music/songs/" + id + "/stream";

                    if (listener != null) listener.onDownloadStarted(id, outFile);

                    Map<String, String> headers = new HashMap<>();
                    String token = SessionManager.loadToken();
                    if (token != null && !token.trim().isEmpty()) {
                        headers.put("Authorization", "Bearer " + token);
                    }

                    final int songId = id;
                    ApiClient.ProgressCallback progressCallback = (bytesRead, contentLength) -> {
                        if (listener != null) {
                            try {
                                listener.onDownloadProgress(songId, bytesRead, contentLength);
                            } catch (Exception ignored) {
                            }
                        }
                    };

                    boolean success = ApiClient.downloadEncrypted(streamUrl, headers, outFile, progressCallback);

                    if (success && outFile.exists() && outFile.length() > 0) {
                        if (listener != null) listener.onDownloadCompleted(id, outFile);
                        System.out.println("[DownloadManager] Download completed for id: " + id);
                    } else {
                        if (listener != null) listener.onDownloadFailed(id, new RuntimeException("Non-2xx response"));
                        System.out.println("[DownloadManager] Download failed for id: " + id);
                    }

                } catch (Exception ex) {
                    if (listener != null) listener.onDownloadFailed(id, ex);
                    System.out.println("[DownloadManager] Exception downloading id " + id + ": " + ex.getMessage());
                }
            }

            if (!cancelled) {
                if (listener != null) listener.onAllDownloadsFinished();
            } else {
                notifyCancelled();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void notifyCancelled() {
        System.out.println("[DownloadManager] Downloading cancelled.");
        if (listener != null) listener.onCancelled();
    }
}
