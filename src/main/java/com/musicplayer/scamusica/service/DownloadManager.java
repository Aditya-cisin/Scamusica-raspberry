package com.musicplayer.scamusica.service;


import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class DownloadManager {

//    public interface DownloadListener {
//        void onDownloadStarted(int songId, File outputFile);
//
//        /**
//         * bytesDownloaded: bytes read so far for current file
//         * contentLength: total content length in bytes (may be -1 if unknown)
//         */
//        void onDownloadProgress(int songId, long bytesDownloaded, long contentLength);
//
//        /**
//         * Fired when a file was actually downloaded (new file).
//         */
//        void onDownloadCompleted(int songId, File outputFile);
//
//        /**
//         * Fired when an existing file was present and we skipped downloading it.
//         */
//        void onDownloadSkipped(int songId, File existingFile);
//
//        void onDownloadFailed(int songId, Exception ex);
//
//        void onAllDownloadsFinished();
//
//        void onCancelled();
//    }
//
//    private final ExecutorService executor = Executors.newSingleThreadExecutor();
//    private volatile boolean cancelled = false;
//    private final List<Integer> downloadSequence;
//    private final DownloadListener listener;
//    private final String downloadFolderPath;
//
//    public DownloadManager(List<Integer> downloadSequence,
//                           String downloadFolderPath,
//                           DownloadListener listener) {
//        this.downloadSequence = downloadSequence == null ? Collections.emptyList() : new ArrayList<>
//        (downloadSequence);
//        this.listener = listener;
//        this.downloadFolderPath = downloadFolderPath == null ? "./downloads" : downloadFolderPath;
//    }
//
//    public void start() {
//        cancelled = false;
//        executor.submit(this::runDownloadSequence);
//    }
//
//    public void stop() {
//        cancelled = true;
//    }
//
//    public void shutdownNow() {
//        cancelled = true;
//        executor.shutdownNow();
//    }
//
//    private void runDownloadSequence() {
//        try {
//            File baseDir = new File(downloadFolderPath);
//            if (!baseDir.exists()) {
//                boolean ok = baseDir.mkdirs();
//                if (!ok)
//                    AppLogger.log("[DownloadManager] Could not create download directory: "
//                            + baseDir.getAbsolutePath());
//            }
//
//            for (Integer id : downloadSequence) {
//                if (cancelled) {
//                    notifyCancelled();
//                    return;
//                }
//                if (id == null) continue;
//
//                try {
//                    File outFile = new File(baseDir, "song-" + id + ".dat");
//
//                    if (outFile.exists() && outFile.length() > 0) {
//                        AppLogger.log("[DownloadManager] File exists, skipping id: " + id);
//                        if (listener != null) listener.onDownloadSkipped(id, outFile);
//                        continue;
//                    }
//
//                    String streamUrl = Utility.BASE_URL.get() + "/api/music/songs/" + id + "/stream";
//
//                    if (listener != null) listener.onDownloadStarted(id, outFile);
//
//                    Map<String, String> headers = new HashMap<>();
//                    String token = SessionManager.loadToken();
//                    if (token != null && !token.trim().isEmpty()) {
//                        headers.put("Authorization", "Bearer " + token);
//                    }
//
//                    final int songId = id;
//                    ApiClient.ProgressCallback progressCallback = (bytesRead, contentLength) -> {
//                        if (listener != null) {
//                            try {
//                                listener.onDownloadProgress(songId, bytesRead, contentLength);
//                            } catch (Exception ignored) {
//                            }
//                        }
//                    };
//
//                    boolean success = ApiClient.downloadEncrypted(streamUrl, headers, outFile, progressCallback);
//
//                    if (success && outFile.exists() && outFile.length() > 0) {
//                        if (listener != null) listener.onDownloadCompleted(id, outFile);
//                        AppLogger.log("[DownloadManager] Download completed for id: " + id);
//                    } else {
//                        if (listener != null) listener.onDownloadFailed(id, new RuntimeException("Non-2xx response"));
//                        AppLogger.log("[DownloadManager] Download failed for id: " + id);
//                    }
//
//                } catch (Exception ex) {
//                    if (listener != null) listener.onDownloadFailed(id, ex);
//                    AppLogger.log("[DownloadManager] Exception downloading id " + id + ": " + ex.getMessage());
//                }
//            }
//
//            if (!cancelled) {
//                if (listener != null) listener.onAllDownloadsFinished();
//            } else {
//                notifyCancelled();
//            }
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//    }
//
//    private void notifyCancelled() {
//        AppLogger.log("[DownloadManager] Downloading cancelled.");
//        if (listener != null) listener.onCancelled();
//    }

    public interface DownloadListener {
        void onDownloadStarted(int songId, File outputFile);

        void onDownloadProgress(int songId, long bytesDownloaded, long contentLength);

        void onDownloadCompleted(int songId, File outputFile);

        void onDownloadSkipped(int songId, File existingFile);

        void onDownloadFailed(int songId, Exception ex);

        void onAllDownloadsFinished();

        void onCancelled();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Integer> downloadQueue = new LinkedBlockingQueue<>();
    private volatile boolean cancelled = false;
    private final Set<Integer> activeDownloads = ConcurrentHashMap.newKeySet();

    private final DownloadListener listener;
    private final String downloadFolderPath;

    public DownloadManager(String downloadFolderPath,
                           DownloadListener listener) {
        this.listener = listener;
        this.downloadFolderPath = downloadFolderPath;
    }

    public void start() {
        cancelled = false;
        executor.submit(this::runWorker);
    }

    public void stop() {
        cancelled = true;
        executor.shutdownNow();
    }

    public void queueDownload(int songId) {
        if (!cancelled) {
            if (activeDownloads.add(songId)) {
                AppLogger.log("[DOWNLOAD] Queued: " + songId);
                downloadQueue.offer(songId);
            }
        }
    }

    private void runWorker() {
        while (!cancelled) {
            try {
                Integer id = downloadQueue.poll(2, TimeUnit.SECONDS);
                if (id == null) continue;

                processDownload(id);

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processDownload(Integer id) {
        AppLogger.log("[DOWNLOAD] Starting: " + id);
        try {
            File baseDir = new File(downloadFolderPath);
            if (!baseDir.exists()) baseDir.mkdirs();

            File outFile = new File(baseDir, "song-" + id + ".dat");

            if (outFile.exists() && outFile.length() > 0) {
                AppLogger.log("[DOWNLOAD][SKIP] Already exists: " + id);
                if (listener != null) listener.onDownloadSkipped(id, outFile);
                activeDownloads.remove(id); // 🔥 IMPORTANT
                return;
            }

            String streamUrl = Utility.BASE_URL.get() + "/api/music/songs/" + id + "/stream";

            if (listener != null) listener.onDownloadStarted(id, outFile);

            Map<String, String> headers = new HashMap<>();
            String token = SessionManager.loadToken();
            if (token != null && !token.trim().isEmpty()) {
                headers.put("Authorization", "Bearer " + token);
            }

            ApiClient.ProgressCallback progressCallback = (bytesRead, contentLength) -> {
                if (listener != null) {
                    listener.onDownloadProgress(id, bytesRead, contentLength);
                }
            };

            boolean success = ApiClient.downloadEncrypted(streamUrl, headers, outFile, progressCallback);

            if (success) {
                AppLogger.log("[DOWNLOAD][DONE] " + id);
                if (listener != null) listener.onDownloadCompleted(id, outFile);
            } else {
                AppLogger.log("[DOWNLOAD][FAIL] " + id);
                if (listener != null) listener.onDownloadFailed(id, new RuntimeException("Download failed"));
            }

            activeDownloads.remove(id);

        } catch (Exception ex) {
            if (listener != null) listener.onDownloadFailed(id, ex);
        }
    }
}
