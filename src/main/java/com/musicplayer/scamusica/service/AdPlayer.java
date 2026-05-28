package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;
import javafx.application.Platform;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class AdPlayer {

    public interface AdPlaybackListener {
        void onAdPlaybackStarted(Ad ad);
        void onAdPlaybackFinished(Ad ad);
        void onSongPaused(String reason);
        void onSongResumed();
        void onPlaybackError(Exception ex);
    }

    private final MediaPlayer vlcPlayer;
    private final AdPlaybackListener listener;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AdPlayer-Thread");
        t.setDaemon(true);
        return t;
    });
    private final Queue<Ad> adQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isPlayingAd = false;

    private volatile String savedSongPath = null;
    private volatile long savedSongTime = 0L;

    public AdPlayer(MediaPlayer vlcPlayer, AdPlaybackListener listener) {
        this.vlcPlayer = vlcPlayer;
        this.listener = listener;
    }

    public void queueAds(List<Ad> ads) {
        if (ads == null || ads.isEmpty()) return;

        AppLogger.log("[AdPlayer] Queueing " + ads.size() + " ads");

        List<Ad> playableAds = new ArrayList<>();

        for (Ad ad : ads) {
            if (AdDownloadManager.isAdDownloaded(ad)) {
                playableAds.add(ad);
                AppLogger.log("[AdPlayer] Ad queued (local): " + ad.getCampaignName());
            } else if (NetworkMonitor.getInstance().isOnline()) {
                playableAds.add(ad);
                AppLogger.log("[AdPlayer] Ad queued (stream): " + ad.getCampaignName());
            } else {
                AppLogger.log("[AdPlayer] Skipping ad (offline + not downloaded): " + ad.getCampaignName());
            }
        }

        if (playableAds.isEmpty()) return;

        AppLogger.log("[AdPlayer] Queueing " + playableAds.size() + " playable ads");
        List<Ad> shuffled = new ArrayList<>(playableAds);
        Collections.shuffle(shuffled);
        adQueue.addAll(shuffled);

        if (!isPlayingAd) {
            playNextAd();
        }
    }

    private void playNextAd() {
        Ad nextAd = adQueue.poll();
        if (nextAd == null) {
            AppLogger.log("[AdPlayer] Queue empty, resuming song");
            isPlayingAd = false;
            resumeSong();
            return;
        }

        isPlayingAd = true;
        executor.submit(() -> {
            try {
                playAdInternal(nextAd);
            } catch (Exception e) {
                AppLogger.log("[AdPlayer] Error: " + e.getMessage());
                listener.onPlaybackError(e);
                playNextAd();
            }
        });
    }

    public long getSavedSongTime() {
        return savedSongTime;
    }

    private void playAdInternal(Ad ad) throws Exception {
        AppLogger.log("[AdPlayer] Preparing ad: " + ad.getCampaignName());

        try {
            savedSongTime = vlcPlayer.status().time();
        } catch (Exception ignored) {}

        Platform.runLater(() -> {
            try {

                savedSongTime = vlcPlayer.status().time();

                AppLogger.log("[AdPlayer] Saving song position: " + savedSongTime);

                vlcPlayer.controls().pause();

                listener.onSongPaused("Ad starting");

            } catch (Exception ignored) {}
        });
        Thread.sleep(600);

        String adUrl = buildAdUrl(ad);
        if (adUrl == null) {
            AppLogger.log("[AdPlayer] Invalid ad URL, skipping");
            playNextAd();
            return;
        }

        AppLogger.log("[AdPlayer] Playing ad from URL: " + adUrl);

        CountDownLatch latch = new CountDownLatch(1);

        Platform.runLater(() -> {
            try {
                listener.onAdPlaybackStarted(ad);
                AppLogger.log("[AdPlayer] STARTING ACTUAL VLC PLAY");
                boolean result = vlcPlayer.media().play(adUrl);

                AppLogger.log("[AdPlayer] VLC PLAY RESULT = " + result);

                vlcPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
                    @Override
                    public void finished(MediaPlayer mediaPlayer) {
                        AppLogger.log("[AdPlayer] Ad finished: " + ad.getCampaignName());
                        vlcPlayer.events().removeMediaPlayerEventListener(this);
                        latch.countDown();
                    }

                    @Override
                    public void error(MediaPlayer mediaPlayer) {
                        AppLogger.log("[AdPlayer] Ad error: " + ad.getCampaignName());
                        vlcPlayer.events().removeMediaPlayerEventListener(this);
                        latch.countDown();
                    }
                });
            } catch (Exception e) {
                AppLogger.log("[AdPlayer] Failed to start ad: " + e.getMessage());
                latch.countDown();
            }
        });

        boolean finished = latch.await(10, TimeUnit.MINUTES);
        AppLogger.log("[AdPlayer] Ad latch released, finished=" + finished);

        Thread.sleep(300);

        Platform.runLater(() -> listener.onAdPlaybackFinished(ad));

        playNextAd();
    }

    private void resumeSong() {
        Platform.runLater(() -> {
            try {
                listener.onSongResumed();
            } catch (Exception e) {
                AppLogger.log("[AdPlayer] Resume error: " + e.getMessage());
            }
        });
    }

    private String buildAdUrl(Ad ad) {
        if (ad == null) return null;

        if (AdDownloadManager.isAdDownloaded(ad)) {
            File localFile = AdDownloadManager.getLocalAdFile(ad);
            AppLogger.log("[AdPlayer] Playing ad from local file: " + localFile.getAbsolutePath());
            return localFile.getAbsolutePath();
        }

        if (!NetworkMonitor.getInstance().isOnline()) {
            AppLogger.log("[AdPlayer] Ad not downloaded and offline, skipping: " + ad.getCampaignName());
            return null;
        }

        String audioFile = ad.getAudioFile();
        if (audioFile == null || audioFile.isEmpty()) return null;

        if (audioFile.startsWith("http://") || audioFile.startsWith("https://")) {
            return audioFile;
        }

        String encoded = audioFile
                .replace(" ", "%20")
                .replace("(", "%28")
                .replace(")", "%29")
                .replace("[", "%5B")
                .replace("]", "%5D");
        if (!encoded.startsWith("/")) {
            encoded = "/" + encoded;
        }

        return Utility.BASE_URL.get() + encoded;
    }

    public boolean isPlayingAd() {
        return isPlayingAd;
    }

    public void clearQueue() {
        adQueue.clear();
        AppLogger.log("[AdPlayer] Queue cleared");
    }

    public void stop() {
        clearQueue();
        isPlayingAd = false;
        executor.shutdownNow();
        AppLogger.log("[AdPlayer] Stopped");
    }
}