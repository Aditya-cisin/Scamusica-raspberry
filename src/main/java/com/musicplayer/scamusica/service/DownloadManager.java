package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.EncryptionUtil;
import com.musicplayer.scamusica.util.Utility;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DownloadManager {

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
    private final List<Integer> downloadSequence;
    private final String downloadFolderPath;
    private final DownloadListener listener;
    private volatile boolean cancelled = false;

    public DownloadManager(List<Integer> downloadSequence,
                           String downloadFolderPath,
                           DownloadListener listener) {
        this.downloadSequence = downloadSequence;
        this.downloadFolderPath = downloadFolderPath;
        this.listener = listener;
    }

    public void start() {
        executor.submit(this::run);
    }

    public void stop() {
        cancelled = true;
    }

    private void run() {

        File baseDir = new File(downloadFolderPath);
        baseDir.mkdirs();

        for (Integer id : downloadSequence) {
            if (cancelled) {
                listener.onCancelled();
                return;
            }

            try {
                File out = new File(baseDir, "song-" + id + ".mp3");

                if (out.exists() && out.length() > 0) {
                    listener.onDownloadSkipped(id, out);
                    continue;
                }

                String url = Utility.BASE_URL.get()
                        + "/api/music/songs/" + id + "/stream";

                listener.onDownloadStarted(id, out);

                Map<String, String> headers = new HashMap<>();
                headers.put("Authorization",
                        "Bearer " + SessionManager.loadToken());

                File tmp = new File(baseDir, "song-" + id + ".tmp");

                ApiClient.downloadToFile(
                        url,
                        headers,
                        tmp,
                        (b, t) -> listener.onDownloadProgress(id, b, t)
                );

                EncryptionUtil.encryptFile(tmp, out);
                tmp.delete();

                listener.onDownloadCompleted(id, out);

            } catch (Exception e) {
                listener.onDownloadFailed(id, e);
            }
        }

        listener.onAllDownloadsFinished();
    }
}
