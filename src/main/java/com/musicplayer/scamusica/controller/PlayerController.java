package com.musicplayer.scamusica.controller;

import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.model.PlaylistTrack;
import com.musicplayer.scamusica.service.*;
import com.musicplayer.scamusica.ui.*;

import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.CryptoUtil;
import com.musicplayer.scamusica.util.PlaybackHistoryLogger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent;

import javax.crypto.CipherInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import com.musicplayer.scamusica.util.OfflineCache;

public class PlayerController extends Application {

    private Label globalAlbumHeading;
    private Label globalTitleLabel;

    private Slider globalProgressSlider;
    private HBox globalControlsWrapper;
    private HBox globalBottomBar;

    private MediaPlayer vlcPlayer;
    private AudioPlayerComponent vlcPlayerComponent;
    private boolean vlcHandlersAttached = false;
    private boolean userPaused = false;

    private MediaPlayerEventAdapter currentVlcListener = null;
    private File currentTempFile = null;
    private Thread queueWorkerThread = null;

    private final PlayerSidebar sidebarUtil = new PlayerSidebar();
    private final PlayerHeader headerUtil = new PlayerHeader();
    private final PlayerDropdown dropdownUtil = new PlayerDropdown();
    private final PlayerControls controlsUtil = new PlayerControls();
    private final PlayerAlbum albumUtil = new PlayerAlbum();

    private VBox playlistDropdownCard;
    private HBox playlistPill;

    private String currentPlaylistName;

    private static final String PREF_VOLUME = "player_volume";
    private final Preferences prefs = Preferences.userNodeForPackage(PlayerController.class);

    private final List<PlaylistTrack> playQueue = Collections.synchronizedList(new ArrayList<>());
    private int currentTrackIndex = 0;

    private volatile boolean isFirstTrackStarted = false;

    private ImageView albumImageView;

    private final List<String> tempPlaylist = Arrays.asList(
            "Secuencias-Estilos-playlist",
            "Sequence 1",
            "Sequence 2",
            "Sequence 3",
            "Playlist Custom 1",
            "Playlist Custom 2",
            "Playlist Custom 3"
    );

    private DownloadManager downloadManager;

    private final AtomicInteger totalDownloadedCounter = new AtomicInteger(0);

    private volatile int currentGenreTotalFiles = 0;
    private final AtomicInteger currentGenreDownloadedCount = new AtomicInteger(0);
    private volatile double currentFileProgressFraction = 0.0;

    private ScheduledExecutorService schedular;
    private BlockingQueue<Runnable> operationQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private List<Integer> lastServerIds = new ArrayList<>();

    private AdScheduler adScheduler;
    private AdPlayer adPlayer;
    private List<Ad> allAds = new ArrayList<>();

    @Override
    public void start(Stage primaryStage) {

        AppLogger.init();
        // === NETWORK MONITOR START ===
        NetworkMonitor.getInstance().start();
        AppLogger.log("[APP] Player started");

        String appDir = System.getProperty("user.dir");

        String vlcPath = appDir + File.separator + "vlc";

        System.setProperty("jna.library.path", vlcPath);

        vlcPlayerComponent = new AudioPlayerComponent();
        vlcPlayer = vlcPlayerComponent.mediaPlayer();

        initializeAdSystem();

        Button headphonesButton = sidebarUtil.createIconButton("fas-headphones");
        List<Button> sidebarButtons = Arrays.asList(headphonesButton);
        sidebarUtil.addSidebarLogic(sidebarButtons, headphonesButton);
        VBox sidebarTop = sidebarUtil.createSidebarTop(headphonesButton);
        FontIcon settingsIcon = sidebarUtil.createSettingsIcon(primaryStage);
        VBox sidebar = sidebarUtil.createSidebar(sidebarTop, settingsIcon);

        HBox leftMeta = headerUtil.createLeftMeta();
        ImageView logoView = headerUtil.createLogoView(getClass());
        HBox rightMeta = headerUtil.createRightMeta();
        ComboBox<LangItem> languageBox = LanguageManager.createLanguageSelector();
        languageBox.getStyleClass().add("language-selector");
        rightMeta.getChildren().add(languageBox);
        BorderPane header = headerUtil.createHeader(leftMeta, logoView, rightMeta);


//        Label albumHeading = albumUtil.createAlbumHeading();
        globalAlbumHeading = albumUtil.createAlbumHeading();
        Label albumHeading = globalAlbumHeading;
        // New Code
        Label currentStyleLabel = new Label();
        currentStyleLabel.textProperty().bind(
                LanguageManager.createStringBinding("label.currentStyle")
        );

        currentStyleLabel.getStyleClass().add("section-heading-styles");
        // New Code

        ImageView img = albumUtil.createAlbumImage(getClass());
        albumImageView = img;
        albumUtil.applyClip(img);
        HBox songsBox = albumUtil.createSongsBox();

        VBox leftAlbumVBox = albumUtil.createLeftAlbumVBox(albumHeading, img, songsBox);
        leftAlbumVBox.getChildren().add(0, currentStyleLabel);
        recomputeGlobalCountAndUpdateUI();

        List<String> tempList;
        try {
            PlaylistApiService playlistApiService = new PlaylistApiService();
            List<String> apiPlaylists = playlistApiService.fetchPlaylistTitles();
            if (apiPlaylists != null && !apiPlaylists.isEmpty()) {
                tempList = new ArrayList<>(apiPlaylists);
            } else {
                tempList = tempPlaylist;
                AppLogger.log("[PlayerController] API returned empty list, using fallback playlists.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            AppLogger.log("[PlayerController] API failed, checking offline cache...");
            List<String> cached = OfflineCache.loadPlaylistTitles();
            if (!cached.isEmpty()) {
                tempList = cached;
                AppLogger.log("[PlayerController] Using cached playlists: " + cached.size());
            } else {
                tempList = tempPlaylist; // hardcoded fallback
                AppLogger.log("[PlayerController] No cache, using hardcoded fallback.");
            }
        }

        final javafx.collections.ObservableList<String> playlistViewItems = FXCollections.observableArrayList();
        final String[] playlistCurrent = new String[1];
        final List<String> playlistMaster = tempList;

        playlistCurrent[0] = playlistMaster.get(0);
        playlistViewItems.setAll(playlistMaster.stream()
                .filter(s -> !s.equals(playlistCurrent[0]))
                .collect(Collectors.toList()));

        playlistPill = dropdownUtil.createPlaylistPill(playlistCurrent[0]);
        playlistDropdownCard =
                dropdownUtil.createDropdownCard(playlistViewItems, playlistCurrent, playlistMaster, playlistPill);

        HBox playlistHeaderBox = dropdownUtil.createPlaylistHeaderBox(playlistPill);

        // New Code
        Label sequencesLabel = new Label();
        sequencesLabel.textProperty().bind(
                LanguageManager.createStringBinding("label.sequencesTitle")
        );
        sequencesLabel.getStyleClass().add("section-heading-sequences");

        // New Code
        VBox rightColumn = new VBox(8);
        rightColumn.getChildren().addAll(sequencesLabel, playlistHeaderBox);

        HBox rightWrapper = new HBox(rightColumn);
        rightWrapper.setAlignment(Pos.TOP_RIGHT);

//        Label titleCentered = headerUtil.createPlayerTitle();
        globalTitleLabel = headerUtil.createPlayerTitle();
        Label titleCentered = globalTitleLabel;
        VBox centerContainer = headerUtil.createCenterContainer(titleCentered);
        HBox topRow = albumUtil.createTopRow(leftAlbumVBox, centerContainer, rightWrapper);

        Slider progressSlider = controlsUtil.createProgressSlider();
        globalProgressSlider = progressSlider;

        Label leftTime = controlsUtil.createTimeLabel(false);
        Label rightTime = controlsUtil.createTimeLabel(true);
        HBox timesRow = controlsUtil.createTimesRow(leftTime, rightTime);
        HBox progressRow = controlsUtil.createProgressRow(progressSlider);
        VBox sliderContainer = controlsUtil.createSliderContainer(titleCentered, timesRow, progressRow);
        HBox controlsWrapper = controlsUtil.createControls(progressSlider, playlistPill);
        globalControlsWrapper = controlsWrapper;

        HBox bottomBar = controlsUtil.createBottomBar();
        globalBottomBar = bottomBar;

        Label downloadLabel = controlsUtil.getDownloadLabel(bottomBar);

        if (downloadLabel != null) {
            bottomBar.getChildren().remove(downloadLabel);
            downloadLabel.setPadding(new javafx.geometry.Insets(6, 0, 0, 6));
            downloadLabel.getStyleClass().remove("download-label");
            downloadLabel.getStyleClass().add("download-label-left");
            leftAlbumVBox.getChildren().add(downloadLabel);
        }

        VBox contentVBox = new VBox();
        contentVBox.setSpacing(0);
        contentVBox.getChildren().addAll(header, topRow, sliderContainer, controlsWrapper, bottomBar);
        VBox.setVgrow(topRow, Priority.ALWAYS);

        BorderPane mainPane = new BorderPane();
        mainPane.getStyleClass().add("root");
        mainPane.setLeft(sidebar);
        mainPane.setCenter(contentVBox);

        Pane rootOverlay = new Pane();
        mainPane.setPrefSize(1200, 760);
        mainPane.prefWidthProperty().bind(rootOverlay.widthProperty());
        mainPane.prefHeightProperty().bind(rootOverlay.heightProperty());

        rootOverlay.getChildren().addAll(mainPane, playlistDropdownCard);

        Scene scene = new Scene(rootOverlay, 1200, 820);
        scene.getStylesheets().add(getClass().getResource("/styles/style.css").toExternalForm());
        playlistDropdownCard.getStylesheets().add(scene.getStylesheets().get(0));

        headerUtil.loadFonts(getClass());
        scene.getRoot().setStyle("-fx-font-family: 'Poppins', 'Noto Sans', 'Noto Sans JP', 'Noto Sans SC', 'Noto Sans" +
                " Arabic', 'Noto Sans Devanagari';");
        albumUtil.bindImageSize(img, scene, progressRow, titleCentered);

        dropdownUtil.setupDropdownHandlers(
                playlistPill,
                playlistDropdownCard,
                playlistMaster,
                playlistViewItems,
                playlistCurrent,
                scene,
                rootOverlay,
                img,
                this::hideDropdown,
                selectedPlaylistName -> {
                    currentPlaylistName = selectedPlaylistName;
                    try {
                        loadPlaylistAndStart(
                                selectedPlaylistName,
                                albumHeading,
                                titleCentered,
                                progressSlider,
                                leftTime,
                                rightTime,
                                controlsWrapper,
                                bottomBar,
                                downloadLabel,
                                true
                        );
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        primaryStage.titleProperty().bind(
                LanguageManager.createStringBinding("app.title")
        );
        primaryStage.setScene(scene);

        primaryStage.setOnCloseRequest(event -> {
            AppLogger.log("[APP] Closing application");
            AppLogger.close();

            running = false;

            // ✅ FIX: Queue worker ko interrupt karo
            if (queueWorkerThread != null) {
                queueWorkerThread.interrupt();
            }

            if (schedular != null) {
                schedular.shutdownNow();
            }

            if (vlcPlayer != null) {
                try {
                    vlcPlayer.controls().stop();
                } catch (Exception ignored) {
                }
            }

            if (downloadManager != null) {
                try {
                    downloadManager.stop();
                } catch (Exception ignored) {
                }
            }

            // ✅ ADD THESE 4 LINES - Cleanup ad system
            if (adScheduler != null) {
                adScheduler.stop();
            }
            if (adPlayer != null) {
                adPlayer.stop();
            }

            NetworkMonitor.getInstance().stop();
            Platform.exit();

            System.exit(0);
        });

        primaryStage.show();

        Platform.runLater(() -> {
            controlsUtil.setupSliderFill(progressSlider);

            Slider volumeSlider = controlsUtil.getVolumeSlider(bottomBar);
            controlsUtil.setupVolumeSliderFill(controlsUtil.getVolumeSlider(bottomBar));

            // Saved volume load karo, default 85
            double savedVolume = prefs.getDouble(PREF_VOLUME, 85.0);
            volumeSlider.setValue(savedVolume);
            vlcPlayer.audio().setVolume((int) savedVolume);

            // Volume change hone par save karo
            volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                prefs.putDouble(PREF_VOLUME, newVal.doubleValue());
                vlcPlayer.audio().setVolume(newVal.intValue());
            });

            progressSlider.setOnMouseReleased(e -> {
                float pos = (float) (progressSlider.getValue() / 100.0);
                vlcPlayer.controls().setPosition(pos);
            });

            currentPlaylistName = playlistCurrent[0];
            try {
                loadPlaylistAndStart(
                        playlistCurrent[0],
                        albumHeading,
                        titleCentered,
                        progressSlider,
                        leftTime,
                        rightTime,
                        controlsWrapper,
                        bottomBar,
                        downloadLabel,
                        true
                );

                setupBigPlayBehaviour(
                        albumHeading,
                        titleCentered,
                        controlsWrapper,
                        progressSlider,
                        leftTime,
                        rightTime,
                        bottomBar,
                        downloadLabel
                );

            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            Button forwardBtn = (Button) controlsWrapper.lookup("#forwardButton");

            if (forwardBtn != null) {

//                forwardBtn.setOnAction(e -> {
//
//                    try {
//
//                        long current = vlcPlayer.status().time();
//
//                        long duration = vlcPlayer.status().length();
//
//                        long target = current + 10000;
//
//                        if (duration > 0 && target > duration) {
//                            target = duration;
//                        }
//
//                        vlcPlayer.controls().setTime(target);
//
//                    } catch (Exception ex) {
//                        ex.printStackTrace();
//                    }
//                });
                forwardBtn.setOnAction(e -> {
                    try {
                        playNextTrack(
                                albumHeading,
                                titleCentered,
                                progressSlider,
                                leftTime,
                                rightTime,
                                controlsWrapper,
                                bottomBar,
                                downloadLabel
                        );
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                });
            }
        });

        // 🔥 START QUEUE WORKER
        queueWorkerThread = new Thread(() -> {
            while (running) {
                try {
                    Runnable task = operationQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        queueWorkerThread.setDaemon(true);
        queueWorkerThread.start();


        // 🔥 START SCHEDULER
        schedular = java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

        schedular.scheduleAtFixedRate(() -> {
            operationQueue.add(() -> {
                try {
                    syncWithServer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }, 30, 60, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void syncWithServer() {
        AppLogger.log("[SYNC] Checking server updates for playlist: " + currentPlaylistName);
        try {
            PlaylistApiService api = new PlaylistApiService();

            String currentPlaylist = currentPlaylistName;
            if (currentPlaylist == null) return;

            List<PlaylistTrack> serverTracks = api.fetchTracksForGenre(currentPlaylist);
            AppLogger.log("[SYNC] Server tracks count: " + serverTracks.size());

            if (serverTracks == null) return;


            List<Integer> serverIds = serverTracks.stream()
                    .map(PlaylistTrack::getId)
                    .collect(Collectors.toList());

            if (serverIds.equals(lastServerIds)) {
                AppLogger.log("[SYNC] No changes detected");
                return; // no change
            }

            lastServerIds = new ArrayList<>(serverIds);

            List<Integer> localIds;
            synchronized (playQueue) {
                localIds = playQueue.stream()
                        .map(PlaylistTrack::getId)
                        .collect(Collectors.toList());
            }

            java.util.Set<Integer> toAdd = new java.util.HashSet<>(serverIds);
            toAdd.removeAll(localIds);

            java.util.Set<Integer> toDelete = new java.util.HashSet<>(localIds);
            toDelete.removeAll(serverIds);

            AppLogger.log("[SYNC] To Add: " + toAdd);
            AppLogger.log("[SYNC] To Delete: " + toDelete);

            // ✅ ADD
            for (PlaylistTrack t : serverTracks) {
                if (toAdd.contains(t.getId())) {
                    boolean exists;
                    synchronized (playQueue) {
                        exists = playQueue.stream()
                                .anyMatch(x -> x.getId() == t.getId());
                    }

                    if (!exists) {
                        playQueue.add(t);
                    }

                    if (downloadManager != null) {
                        downloadManager.queueDownload(t.getId());
                    }
                }
            }

            // ✅ DELETE
            for (Integer id : toDelete) {

                PlaylistTrack current = null;

                if (currentTrackIndex < playQueue.size()) {
                    current = playQueue.get(currentTrackIndex);
                }

                playQueue.removeIf(track -> track.getId() == id);

                deleteSongFile(id);

                if (current != null && current.getId() == id) {
                    Platform.runLater(() -> {
                        try {
                            playNextTrack(null, null, null, null, null, null, null, null);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }

            try {
                syncAdsFromServer();
            } catch (Exception e) {
                AppLogger.log("[SYNC] Ad sync failed: " + e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeAdSystem() {
        AppLogger.log("[PlayerController] Initializing Ad System");

        // 1. Create AdPlayer with listeners
        adPlayer = new AdPlayer(vlcPlayer, new AdPlayer.AdPlaybackListener() {
            @Override
            public void onAdPlaybackStarted(Ad ad) {

                AppLogger.log("[AdPlayer] Ad started: "
                        + ad.getCampaignName());

                Platform.runLater(() -> {
                    try {
                        globalTitleLabel.setText("ADVERTISEMENT");
                        globalAlbumHeading.setText(ad.getCampaignName());

                        // ✅ Global fields use karo
                        if (globalProgressSlider != null) {
                            globalProgressSlider.setDisable(true);
                            globalProgressSlider.setMouseTransparent(true);
                        }
                        if (globalControlsWrapper != null) {
                            globalControlsWrapper.setDisable(true);
                        }

                        setGenreSwitchEnabled(false);

                        // ✅ Volume slider alone enable rakho
                        if (globalBottomBar != null) {
                            Slider volumeSlider = controlsUtil.getVolumeSlider(globalBottomBar);
                            if (volumeSlider != null) {
                                volumeSlider.setDisable(false);
                                volumeSlider.setMouseTransparent(false);
                            }
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onAdPlaybackFinished(Ad ad) {

                AppLogger.log("[AdPlayer] Ad finished: "
                        + ad.getCampaignName());

                Platform.runLater(() -> {

                    try {

                        if (!playQueue.isEmpty() && currentTrackIndex < playQueue.size()) {

                            PlaylistTrack track = playQueue.get(currentTrackIndex);

                            globalTitleLabel.setText(track.getTitle());

                            globalAlbumHeading.setText(currentPlaylistName);
                        }

                        // ✅ Sab re-enable karo
                        if (globalProgressSlider != null) {
                            globalProgressSlider.setDisable(false);
                            globalProgressSlider.setMouseTransparent(false);
                        }
                        if (globalControlsWrapper != null) {
                            globalControlsWrapper.setDisable(false);
                        }

                        setGenreSwitchEnabled(true);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onSongPaused(String reason) {
                AppLogger.log("[AdPlayer] Song paused: " + reason);
            }

//            @Override
//            public void onSongResumed() {
//
//                AppLogger.log("[AdPlayer] Song resuming after ad");
//
//                Platform.runLater(() -> {
//
//                    try {
//
//                        if (!playQueue.isEmpty() && currentTrackIndex < playQueue.size()) {
//
//                            PlaylistTrack track = playQueue.get(currentTrackIndex);
//
//                            // 🔥 SAME SONG resume
//                            vlcPlayer.media().play(
//                                    encodeMediaUrl(track.getUrl())
//                            );
//
//                            // 🔥 SAME POSITION resume
//                            schedular.schedule(() -> {
//                                Platform.runLater(() -> {
//                                    try {
//                                        long savedTime = adPlayer.getSavedSongTime();
//                                        AppLogger.log("[PLAYER] Restoring position: " + savedTime);
//                                        vlcPlayer.controls().setTime(savedTime);
//                                    } catch (Exception e) {
//                                        e.printStackTrace();
//                                    }
//                                });
//                            }, 1500, TimeUnit.MILLISECONDS);
//                        }
//
//                    } catch (Exception e) {
//                        e.printStackTrace();
//                    }
//                });
//            }

            @Override
            public void onSongResumed() {
                AppLogger.log("[AdPlayer] Song resuming after ad");

                Platform.runLater(() -> {
                    try {
                        if (!playQueue.isEmpty() && currentTrackIndex < playQueue.size()) {

                            PlaylistTrack track = playQueue.get(currentTrackIndex);

                            // ✅ Pehle local file check karo, phir URL
                            String baseDownloadDir = System.getProperty("user.home")
                                    + File.separator + ".scamusica"
                                    + File.separator + "downloads";

                            String genreFolder = (currentPlaylistName != null)
                                    ? currentPlaylistName.replaceAll("\\s+", "_")
                                    : track.getFolderTitle().replaceAll("\\s+", "_");

                            File encryptedFile = new File(baseDownloadDir
                                    + File.separator + genreFolder,
                                    "song-" + track.getId() + ".dat");

                            if (encryptedFile.exists()) {
                                // ✅ Local file se resume karo
                                AppLogger.log("[AdPlayer] Resuming from local file: " + encryptedFile.getAbsolutePath());
                                new Thread(() -> {
                                    try {
                                        File tempFile = decryptToTemp(encryptedFile);
                                        currentTempFile = tempFile;
                                        Platform.runLater(() -> {
                                            vlcPlayer.media().play(tempFile.getAbsolutePath());
                                            // ✅ Position restore karo
                                            schedular.schedule(() -> {
                                                Platform.runLater(() -> {
                                                    try {
                                                        long savedTime = adPlayer.getSavedSongTime();
                                                        AppLogger.log("[PLAYER] Restoring position: " + savedTime);
                                                        vlcPlayer.controls().setTime(savedTime);
                                                    } catch (Exception e) { e.printStackTrace(); }
                                                });
                                            }, 1500, TimeUnit.MILLISECONDS);
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }).start();
                            } else if (NetworkMonitor.getInstance().isOnline()) {
                                // ✅ Online hai toh URL se stream karo
                                AppLogger.log("[AdPlayer] Resuming from URL: " + track.getUrl());
                                vlcPlayer.media().play(encodeMediaUrl(track.getUrl()));
                                schedular.schedule(() -> {
                                    Platform.runLater(() -> {
                                        try {
                                            long savedTime = adPlayer.getSavedSongTime();
                                            AppLogger.log("[PLAYER] Restoring position: " + savedTime);
                                            vlcPlayer.controls().setTime(savedTime);
                                        } catch (Exception e) { e.printStackTrace(); }
                                    });
                                }, 1500, TimeUnit.MILLISECONDS);
                            } else {
                                // ✅ Na local file na internet — next track play karo
                                AppLogger.log("[AdPlayer] Cannot resume — offline and no local file. Playing next.");
                                playNextTrack(globalAlbumHeading, globalTitleLabel,
                                        globalProgressSlider, null, null,
                                        globalControlsWrapper, globalBottomBar, null);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onPlaybackError(Exception ex) {
                AppLogger.log("[AdPlayer] Playback error: " + ex.getMessage());
            }
        });

        // 2. Fetch ads from server
        try {
            PlaylistApiService api = new PlaylistApiService();
            allAds = api.fetchAds();

            AppLogger.log("[PlayerController] Total ads = " + allAds.size());

            for (Ad ad : allAds) {

                AppLogger.log("==============");
                AppLogger.log("Campaign: " + ad.getCampaignName());
                AppLogger.log("ScheduleType: " + ad.getScheduleType());
                AppLogger.log("PlayTimes: " + ad.getPlayTimes());
                AppLogger.log("ActiveDays: " + ad.getActiveDays());
            }

            // ✅ Online hai to schedule cache karo aur download shuru karo
            if (allAds != null && !allAds.isEmpty()) {
                OfflineCache.saveAdSchedule(allAds);          // schedule save karo
                AdDownloadManager.downloadAllAds(allAds);      // files download karo background mein
            }

            if (allAds == null) {
                allAds = new ArrayList<>();
            }
            AppLogger.log("[AdSystem] Loaded " + allAds.size() + " ads");
        } catch (Exception e) {
            AppLogger.log("[AdSystem] Failed to load ads online, trying cache...");
            // ✅ Offline fallback — cache se load karo
            allAds = OfflineCache.loadAdSchedule();
            AppLogger.log("[AdSystem] Loaded " + allAds.size() + " ads from cache");
        }

        // 3. Create scheduler with listeners
        adScheduler = new AdScheduler(allAds, new AdScheduler.AdScheduleListener() {
            @Override
            public void onAdsReady(List<Ad> ads) {
                AppLogger.log("[AdScheduler] " + ads.size() + " ads due to play");
                // Queue them for playback
                if (adPlayer != null) {
                    adPlayer.queueAds(ads);
                }
            }

            @Override
            public void onScheduleError(Exception ex) {
                AppLogger.log("[AdScheduler] Error: " + ex.getMessage());
            }
        });

        // 4. Start the scheduler
        adScheduler.start();
    }

    private void syncAdsFromServer() throws Exception {
        PlaylistApiService api = new PlaylistApiService();
        List<Ad> serverAds = api.fetchAds();

        if (serverAds != null && !serverAds.isEmpty()) {
            allAds = new ArrayList<>(serverAds);
            if (adScheduler != null) {
                adScheduler.updateAds(allAds);
            }
            OfflineCache.saveAdSchedule(allAds);
            AdDownloadManager.downloadAllAds(allAds);
            AppLogger.log("[SYNC] Ads updated: " + allAds.size() + " ads");
        }
    }

    private void deleteSongFile(int id) {
        String baseDownloadDir = System.getProperty("user.home")
                + File.separator + ".scamusica"
                + File.separator + "downloads";

        File baseDir = new File(baseDownloadDir);

        File[] folders = baseDir.listFiles();
        if (folders == null) return;

        for (File folder : folders) {
            File file = new File(folder, "song-" + id + ".dat");
            if (file.exists()) {
                AppLogger.log("[DELETE] Removing file for ID: " + id);
                file.delete();
            }
        }
    }

    private void hideDropdown(VBox dropdownCard) {
        dropdownCard.setVisible(false);
        dropdownCard.setManaged(false);
    }

    private int countExistingInGenreFolder(String genreFolderPath) {
        File dir = new File(genreFolderPath);
        if (!dir.exists() || !dir.isDirectory()) return 0;
        File[] files = dir.listFiles();
        if (files == null) return 0;
        int c = 0;
        for (File f : files) {
            if (!f.isDirectory() && f.getName().startsWith("song-") && f.getName().endsWith(".dat") && f.length() > 0) {
                c++;
            }
        }
        return c;
    }

    private int countExistingDownloadedFiles(File root) {
        if (root == null || !root.exists()) return 0;
        int count = 0;
        File[] files = root.listFiles();
        if (files == null) return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count += countExistingDownloadedFiles(f);
            } else {
                String name = f.getName();
                if (name.startsWith("song-") && name.endsWith(".dat") && f.length() > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private void recomputeGlobalCountAndUpdateUI() {
        Platform.runLater(() -> {
            String baseDownloadDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "downloads";

            File baseDir = new File(baseDownloadDir);
            if (!baseDir.exists()) {
                boolean created = baseDir.mkdirs();
                AppLogger.log("[PlayerController] Base dir created: " + created);
            }

            // Mac installer permissions fix
            baseDir.setWritable(true, false);
            baseDir.setReadable(true, false);
            baseDir.setExecutable(true, false);

            int globalExisting = countExistingDownloadedFiles(new File(baseDownloadDir));
            totalDownloadedCounter.set(globalExisting);
            albumUtil.setSongCount(globalExisting);
        });
    }

    private Button getBigPlayButton(HBox controlsWrapper) {
        try {
            return (Button) ((StackPane) controlsWrapper.getChildren().get(1)).getChildren().get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void updatePlayButtonState(HBox controlsWrapper) {
        Button bigPlayBtn = getBigPlayButton(controlsWrapper);
        if (bigPlayBtn == null) return;

        int total = currentGenreTotalFiles;
        int downloaded = currentGenreDownloadedCount.get();

        boolean enable;
        if (total <= 2) {
            enable = true;
        } else {
            enable = downloaded >= 2;
        }

        final boolean shouldEnable = enable;
        Platform.runLater(() -> bigPlayBtn.setDisable(!shouldEnable));
    }

    private void setGenreSwitchEnabled(boolean enabled) {
        if (playlistPill != null) {
            Platform.runLater(() -> playlistPill.setDisable(!enabled));
        }
        if (playlistDropdownCard != null) {
            Platform.runLater(() -> playlistDropdownCard.setDisable(!enabled));
        }
    }

    private void loadPlaylistAndStart(String playlistName,
                                      Label albumHeading,
                                      Label titleLabel,
                                      Slider progressSlider,
                                      Label leftTime,
                                      Label rightTime,
                                      HBox controlsWrapper,
                                      HBox bottomBar,
                                      Label downloadLabel,
                                      boolean autoPlay) throws URISyntaxException {

        stopPlayback(progressSlider, leftTime, rightTime, controlsWrapper, downloadLabel);

        if (!playQueue.isEmpty() && albumImageView != null) {
            String firstImgUrl = playQueue.get(0).getAlbumImageUrl();
            if (firstImgUrl != null && !firstImgUrl.trim().isEmpty()) {
                Platform.runLater(() -> {
                    try {
                        albumImageView.setImage(null);
                        albumImageView.setImage(new Image(firstImgUrl, true));
                    } catch (Exception ignored) {
                    }
                });
            }
        }

        playQueue.clear();
        currentTrackIndex = 0;
        isFirstTrackStarted = false;

        try {
            PlaylistApiService playlistApiService = new PlaylistApiService();

            List<PlaylistTrack> fetchedTracks = playlistApiService.fetchTracksForGenre(playlistName);

            if (fetchedTracks != null && !fetchedTracks.isEmpty()) {
                playQueue.addAll(fetchedTracks);

                java.util.Collections.shuffle(playQueue);
            }

            recomputeGlobalCountAndUpdateUI();

            if (downloadManager != null) {
                downloadManager.stop();
                downloadManager = null;
            }

            List<Integer> downloadSeq = playlistApiService.fetchDownloadSequenceForGenre(playlistName);
            if (downloadSeq == null) downloadSeq = new ArrayList<>();

            currentGenreTotalFiles = downloadSeq.size();

            String baseDownloadDir =
                    System.getProperty("user.home") + File.separator + ".scamusica" + File.separator + "downloads";

            String genreFolderPath = baseDownloadDir + File.separator + playlistName.replaceAll("\\s+", "_");

            File genreDir = new File(genreFolderPath);
            if (!genreDir.exists()) {
                boolean created = genreDir.mkdirs();
                AppLogger.log("[PlayerController] Genre folder created: " + created + " at " + genreFolderPath);
            }
            genreDir.setWritable(true, false);

            int existingInGenre = countExistingInGenreFolder(genreFolderPath);
            currentGenreDownloadedCount.set(existingInGenre);

            updateGenreDownloadLabel(downloadLabel);

            updatePlayButtonState(controlsWrapper);

            boolean needDownload = false;
            if (downloadSeq.isEmpty()) {
                needDownload = false;
            } else {
                for (Integer id : downloadSeq) {
                    File candidate = new File(genreFolderPath, "song-" + id + ".dat");
                    if (!candidate.exists() || candidate.length() == 0) {
                        needDownload = true;
                        break;
                    }
                }
            }

            setGenreSwitchEnabled(true);

            if (!downloadSeq.isEmpty()) {
                downloadManager = new DownloadManager(genreFolderPath,
                        new DownloadManager.DownloadListener() {
                            @Override
                            public void onDownloadStarted(int songId, File outputFile) {
                                currentFileProgressFraction = 0.0;

                                updatePlayButtonState(controlsWrapper);

                                if (downloadLabel != null) {
                                    Platform.runLater(() -> {
                                        try {
                                            downloadLabel.textProperty().unbind();
                                        } catch (Exception ignored) {
                                        }
                                        updateGenreDownloadLabel(downloadLabel);
                                    });
                                }
                            }

                            @Override
                            public void onDownloadProgress(int songId, long bytesDownloaded, long contentLength) {
                                double frac = 0.0;
                                if (contentLength > 0) {
                                    frac = ((double) bytesDownloaded) / ((double) contentLength);
                                    if (frac < 0.0) frac = 0.0;
                                    if (frac > 1.0) frac = 1.0;
                                } else {
                                    frac = Math.min(1.0, bytesDownloaded / (1024.0 * 200));
                                }
                                currentFileProgressFraction = frac;
                                updateGenreDownloadLabel(downloadLabel);
                            }

//                            @Override
//                            public void onDownloadCompleted(int songId, File outputFile) {
//
//                                recomputeGlobalCountAndUpdateUI();
//
//                                Platform.runLater(() -> {
//
//                                    int newGenreCount = countExistingInGenreFolder(genreFolderPath);
//                                    currentGenreDownloadedCount.set(newGenreCount);
//
//                                    currentFileProgressFraction = 0.0;
//
//                                    updateGenreDownloadLabel(downloadLabel);
//                                    updatePlayButtonState(controlsWrapper);
//                                    AppLogger.log("[AUTO-PLAY] Downloaded count: " + newGenreCount);
//                                    if (newGenreCount >= 2) {
//                                        if (!vlcPlayer.status().isPlaying() && !userPaused) {
//                                            try {
//                                                AppLogger.log("[AutoPlay] 2 songs downloaded. Starting playback." +
//                                                        "..");
//                                                playTrack(
//                                                        albumHeading,
//                                                        titleLabel,
//                                                        progressSlider,
//                                                        leftTime,
//                                                        rightTime,
//                                                        controlsWrapper,
//                                                        bottomBar,
//                                                        downloadLabel,
//                                                        true
//                                                );
//                                            } catch (URISyntaxException e) {
//                                                e.printStackTrace();
//                                            }
//                                        }
//                                    }
//                                });
//                            }

                            @Override
                            public void onDownloadCompleted(int songId, File outputFile) {
                                recomputeGlobalCountAndUpdateUI();

                                Platform.runLater(() -> {
                                    int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                    currentGenreDownloadedCount.set(newGenreCount);
                                    currentFileProgressFraction = 0.0;

                                    updateGenreDownloadLabel(downloadLabel);
                                    updatePlayButtonState(controlsWrapper);
                                    AppLogger.log("[AUTO-PLAY] Downloaded: " + newGenreCount + "/" + currentGenreTotalFiles);

                                    // ✅ FIX: Add isFirstTrackStarted check
                                    if (newGenreCount >= 2
                                            && !isFirstTrackStarted      // ← ← ← NEW
                                            && !vlcPlayer.status().isPlaying()
                                            && !userPaused
                                            && (playQueue.isEmpty() || currentTrackIndex == 0)) {  // ← ← ← NEW

                                        try {
                                            if (playQueue.isEmpty()) {
                                                return;
                                            }

                                            AppLogger.log("[AutoPlay] 2 songs ready. Starting playback...");
                                            playTrack(
                                                    albumHeading,
                                                    titleLabel,
                                                    progressSlider,
                                                    leftTime,
                                                    rightTime,
                                                    controlsWrapper,
                                                    bottomBar,
                                                    downloadLabel,
                                                    true
                                            );

                                            isFirstTrackStarted = true;  // ← ← ← SET FLAG AFTER PLAY

                                        } catch (URISyntaxException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onDownloadSkipped(int songId, File existingFile) {
                                recomputeGlobalCountAndUpdateUI();

                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);

                                updateGenreDownloadLabel(downloadLabel);
                                updatePlayButtonState(controlsWrapper);
                            }

                            @Override
                            public void onDownloadFailed(int songId, Exception ex) {
                                AppLogger.log("[PlayerController] Download failed id=" + songId + " -> " + ex.getMessage());
                                currentFileProgressFraction = 0.0;
                                updateGenreDownloadLabel(downloadLabel);
                            }

                            @Override
                            public void onAllDownloadsFinished() {
                                AppLogger.log("[PlayerController] All downloads finished for genre: " + playlistName);
                                setGenreSwitchEnabled(true);

                                recomputeGlobalCountAndUpdateUI();
                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);

                                updatePlayButtonState(controlsWrapper);
                            }

                            @Override
                            public void onCancelled() {
                                AppLogger.log("[PlayerController] Downloads cancelled for genre: " + playlistName);
                                setGenreSwitchEnabled(true);
                                recomputeGlobalCountAndUpdateUI();
                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);
                                updatePlayButtonState(controlsWrapper);
                            }
                        });

                downloadManager.start();
                for (Integer id : downloadSeq) {
                    downloadManager.queueDownload(id);
                }
            } else {
                currentFileProgressFraction = 0.0;
                updateGenreDownloadLabel(downloadLabel);
                setGenreSwitchEnabled(true);
                updatePlayButtonState(controlsWrapper);
            }

        } catch (Exception e) {
            e.printStackTrace();
            setGenreSwitchEnabled(true);
            updatePlayButtonState(controlsWrapper);
        }

        albumHeading.textProperty().bind(LanguageManager.createStringBinding("label.loading"));
        if (!playQueue.isEmpty()) {
            playTrack(
                    albumHeading,
                    titleLabel,
                    progressSlider,
                    leftTime,
                    rightTime,
                    controlsWrapper,
                    bottomBar,
                    downloadLabel,
                    autoPlay
            );
        } else {
            albumHeading.textProperty().bind(LanguageManager.createStringBinding("label.noSong"));
        }
    }

    private void updateGenreDownloadLabel(Label downloadLabel) {
        if (downloadLabel == null) return;

        double percent = 0.0;
        if (currentGenreTotalFiles <= 0) {
            percent = 100.0;
        } else {
            double completed = currentGenreDownloadedCount.get();
            double frac = currentFileProgressFraction;
            percent = ((completed + frac) / (double) currentGenreTotalFiles) * 100.0;
            if (percent < 0.0) percent = 0.0;
            if (percent > 100.0) percent = 100.0;
        }

        final String text = String.format("%.0f%% %s (%d/%d)", percent, LanguageManager.createStringBinding("label" +
                        ".download").get(),
                currentGenreDownloadedCount.get(), currentGenreTotalFiles);

        boolean isDone = (currentGenreDownloadedCount.get() == currentGenreTotalFiles
                && currentGenreTotalFiles > 0);

        Platform.runLater(() -> {
            try {
                downloadLabel.textProperty().unbind();
            } catch (Exception ignored) {
            }
            downloadLabel.setText(text);

            if (isDone) {
                downloadLabel.setStyle("-fx-text-fill: #22c55e;"); // green
            } else {
                downloadLabel.setStyle("-fx-text-fill: #ef4444;"); // red
            }
        });
    }

    private void playTrack(Label albumHeading,
                           Label titleLabel,
                           Slider progressSlider,
                           Label leftTime,
                           Label rightTime,
                           HBox controlsWrapper,
                           HBox bottomBar,
                           Label downloadLabel,
                           boolean autoPlay) throws URISyntaxException {

        if (playQueue.isEmpty()) return;

        if (currentTrackIndex < 0 || currentTrackIndex >= playQueue.size()) {
            stopPlayback(progressSlider, leftTime, rightTime, controlsWrapper, downloadLabel);
            return;
        }

        PlaylistTrack track = playQueue.get(currentTrackIndex);
        //New Code for generating the file
        PlaybackHistoryLogger.logSong(track);
        // New Code for generating the file
        AppLogger.log("[PLAYER][PLAY] " + track.getTitle() + " (ID: " + track.getId() + ")");

//        if (albumImageView != null) {
//            String albumImgUrl = track.getAlbumImageUrl();
//            if (albumImgUrl != null && !albumImgUrl.trim().isEmpty()) {
//                try {
//                    albumImageView.setImage(new Image(albumImgUrl, true));
//                } catch (Exception ex) {
//                    ex.printStackTrace();
//                }
//            }
//        }

        if (albumImageView != null) {
            albumImageView.setImage(null);
            String albumImgUrl = track.getAlbumImageUrl();
            if (albumImgUrl != null && !albumImgUrl.trim().isEmpty()) {
                try {
                    albumImageView.setImage(new Image(albumImgUrl, true));
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }

        String folderTitle = track.getFolderTitle();
        if (folderTitle != null && !folderTitle.trim().isEmpty()) {
            albumHeading.textProperty().unbind();
            albumHeading.setText(folderTitle);
        } else {
            albumHeading.textProperty().bind(LanguageManager.createStringBinding("label.unknownFolder"));
        }

        titleLabel.setText(track.getTitle());

        if (vlcPlayer != null && vlcPlayer.status().isPlaying()) {
            try {
                vlcPlayer.controls().pause();
            } catch (Exception ignored) {
            }
        }

        String safeUrl = encodeMediaUrl(track.getUrl());

        if (!safeUrl.contains(".mp3")) {
            safeUrl += "?ext=.mp3";
        }

        AppLogger.log("FIXED MEDIA URL = " + safeUrl);

        try {
            String baseDownloadDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "downloads";

            //  String genreFolder = track.getFolderTitle().replaceAll("\\s+", "_");// folderTitle nahi,
            //  currentPlaylistName use karo
            // kyunki download is folder mein hota hai
            String genreFolder = (currentPlaylistName != null)
                    ? currentPlaylistName.replaceAll("\\s+", "_")
                    : track.getFolderTitle().replaceAll("\\s+", "_");

            File encryptedFile = new File(baseDownloadDir + File.separator + genreFolder,
                    "song-" + track.getId() + ".dat");

            AppLogger.log("Encrypted file path: " + encryptedFile.getAbsolutePath());
            AppLogger.log("File exists: " + encryptedFile.exists());

            if (encryptedFile.exists()) {
                AppLogger.log("[PLAYER] Playing from local file: " + encryptedFile.getAbsolutePath());
                final String fallbackUrl = safeUrl;
                Thread decryptThread = new Thread(() -> {
                    try {
                        File tempFile = decryptToTemp(encryptedFile);
                        currentTempFile = tempFile;
                        String localUrl = tempFile.toURI().toString();

                        if (!localUrl.contains(".mp3")) {
                            localUrl += "?ext=.mp3";
                        }

                        final String finalUrl = localUrl;

                        Platform.runLater(() -> {

                            vlcPlayer.media().play(tempFile.getAbsolutePath());

                            if (!vlcHandlersAttached) {

                                attachVlcHandlers(
                                        albumHeading,
                                        titleLabel,
                                        progressSlider,
                                        leftTime,
                                        rightTime,
                                        controlsWrapper,
                                        bottomBar,
                                        downloadLabel,
                                        autoPlay
                                );

                                vlcHandlersAttached = true;
                            }
                        });

                    } catch (Exception e) {
                    AppLogger.log("[PLAYER] Decryption failed for song-" + track.getId()
                            + ", file corrupted. Deleting and streaming from URL. Error: " + e.getMessage());
                    encryptedFile.delete();


                    if (NetworkMonitor.getInstance().isOnline()) {
                        Platform.runLater(() -> {
                            AppLogger.log("[PLAYER] Falling back to stream: " + fallbackUrl);
                            vlcPlayer.media().play(fallbackUrl);
                            if (!vlcHandlersAttached) {
                                attachVlcHandlers(albumHeading, titleLabel, progressSlider,
                                        leftTime, rightTime, controlsWrapper, bottomBar, downloadLabel, autoPlay);
                                vlcHandlersAttached = true;
                            }
                        });
                    } else {
                        AppLogger.log("[PLAYER] Offline — cannot stream fallback for song-" + track.getId() + ", skipping to next.");
                        Platform.runLater(() -> {
                            try {
                                playNextTrack(albumHeading, titleLabel, progressSlider,
                                        leftTime, rightTime, controlsWrapper, bottomBar, downloadLabel);
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        });
                    }
                }
                });
                decryptThread.setDaemon(true);
                decryptThread.setPriority(Thread.MAX_PRIORITY);
                decryptThread.start();

                return;

            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        AppLogger.log("[PLAYER] Streaming from URL: " + safeUrl);

        vlcPlayer.media().play(safeUrl);

        if (!vlcHandlersAttached) {

            attachVlcHandlers(
                    albumHeading,
                    titleLabel,
                    progressSlider,
                    leftTime,
                    rightTime,
                    controlsWrapper,
                    bottomBar,
                    downloadLabel,
                    autoPlay
            );

            vlcHandlersAttached = true;
        }
    }


    private void attachVlcHandlers(
            Label albumHeading,
            Label titleLabel,
            Slider progressSlider,
            Label leftTime,
            Label rightTime,
            HBox controlsWrapper,
            HBox bottomBar,
            Label downloadLabel,
            boolean autoPlay
    ) {

        if (currentVlcListener != null) {
            try {
                vlcPlayer.events().removeMediaPlayerEventListener(currentVlcListener);
            } catch (Exception ignored) {
            }
            currentVlcListener = null;
        }

        currentVlcListener = new MediaPlayerEventAdapter() {

            @Override
            public void playing(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
                    if (bigIcon != null) {
                        bigIcon.setIconLiteral("fas-pause");
                    }
                });
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
                    if (bigIcon != null) {
                        bigIcon.setIconLiteral("fas-play");
                    }
                });
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
                    if (bigIcon != null) {
                        bigIcon.setIconLiteral("fas-play");
                    }
                });
            }

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                Platform.runLater(() -> {

                    long duration = mediaPlayer.status().length();

                    if (duration > 0) {

                        double progress = (double) newTime / duration;

                        progressSlider.setValue(progress * 100);

                        leftTime.setText(formatTime(newTime / 1000));

                        rightTime.setText("-" + formatTime((duration - newTime) / 1000));
                    }
                });
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {

                if (adPlayer != null && adPlayer.isPlayingAd()) {
                    AppLogger.log("[PLAYER] Media finished but ad is active, ignoring");
                    return;
                }

                if (currentTempFile != null) {
                    try {
                        if (currentTempFile.exists()) currentTempFile.delete();
                        AppLogger.log("[TEMP] Deleted on finish: " + currentTempFile.getName());
                    } catch (Exception ignored) {
                    }
                    currentTempFile = null;
                }

                Platform.runLater(() -> {
                    try {

                        AppLogger.log("[PLAYER] Song finished, playing next track");

                        playNextTrack(
                                albumHeading,
                                titleLabel,
                                progressSlider,
                                leftTime,
                                rightTime,
                                controlsWrapper,
                                bottomBar,
                                downloadLabel
                        );

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        };
        vlcPlayer.events().addMediaPlayerEventListener(currentVlcListener);
    }

    private void playNextTrack(Label albumHeading,
                               Label titleLabel,
                               Slider progressSlider,
                               Label leftTime,
                               Label rightTime,
                               HBox controlsWrapper,
                               HBox bottomBar,
                               Label downloadLabel) throws URISyntaxException {

        currentTrackIndex++;
        AppLogger.log("[PLAYER] Next track index: " + currentTrackIndex);
        if (currentTrackIndex >= playQueue.size()) {
            AppLogger.log("[PlayerController] All tracks finished. Reshuffling and looping...");
            java.util.Collections.shuffle(playQueue);
            currentTrackIndex = 0;
        }

        playTrack(
                albumHeading,
                titleLabel,
                progressSlider,
                leftTime,
                rightTime,
                controlsWrapper,
                bottomBar,
                downloadLabel,
                true
        );
    }

    private void setupBigPlayBehaviour(Label albumHeading,
                                       Label titleLabel,
                                       HBox controlsWrapper,
                                       Slider progressSlider,
                                       Label leftTime,
                                       Label rightTime,
                                       HBox bottomBar,
                                       Label downloadLabel) {

        Button bigPlayBtn;
        try {
            bigPlayBtn = (Button) ((StackPane) controlsWrapper.getChildren().get(1))
                    .getChildren().get(0);
        } catch (Exception e) {
            bigPlayBtn = null;
        }

        FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);

        if (bigPlayBtn == null || bigIcon == null) {
            return;
        }

        bigPlayBtn.setOnAction(e -> {
            if (playQueue.isEmpty()) {
                return;
            }

            if (vlcPlayer == null || currentTrackIndex >= playQueue.size() || currentTrackIndex < 0) {
                currentTrackIndex = 0;
                try {
                    playTrack(
                            albumHeading,
                            titleLabel,
                            progressSlider,
                            leftTime,
                            rightTime,
                            controlsWrapper,
                            bottomBar,
                            downloadLabel,
                            true
                    );
                } catch (URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
                return;
            }

            if (vlcPlayer.status().isPlaying()) {
                vlcPlayer.controls().pause();
                userPaused = true;
                bigIcon.setIconLiteral("fas-play");
                bigIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            } else {
                vlcPlayer.controls().play();
                userPaused = false;
                bigIcon.setIconLiteral("fas-pause");
                bigIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            }
        });
    }

    private void stopPlayback(Slider progressSlider,
                              Label leftTime,
                              Label rightTime,
                              HBox controlsWrapper,
                              Label downloadLabel) {

        if (currentVlcListener != null) {
            try {
                vlcPlayer.events().removeMediaPlayerEventListener(currentVlcListener);
            } catch (Exception ignored) {
            }
            currentVlcListener = null;
        }
        vlcHandlersAttached = false;

        if (currentTempFile != null) {
            try {
                if (currentTempFile.exists()) currentTempFile.delete();
            } catch (Exception ignored) {
            }
            currentTempFile = null;
        }

        if (vlcPlayer != null) {
            try {
                vlcPlayer.controls().stop();
            } catch (Exception ignored) {
            }
        }

        if (leftTime != null) {
            try {
                leftTime.textProperty().unbind();
            } catch (Exception ignored) {
            }
        }
        if (rightTime != null) {
            try {
                rightTime.textProperty().unbind();
            } catch (Exception ignored) {
            }
        }
        if (downloadLabel != null) {
            try {
                downloadLabel.textProperty().unbind();
            } catch (Exception ignored) {
            }
        }

        if (progressSlider != null) {
            try {
                progressSlider.valueProperty().unbind();
            } catch (Exception ignored) {
            }
            progressSlider.setValue(0);
        }

        if (leftTime != null) {
            leftTime.setText("0:00");
        }
        if (rightTime != null) {
            rightTime.setText("-0:00");
        }

        if (downloadLabel != null) {
            downloadLabel.textProperty().bind(Bindings.concat(
                    "0% ",
                    LanguageManager.createStringBinding("label.download")));
        }

        if (downloadManager != null) {
            downloadManager.stop();
            downloadManager = null;
        }

        FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
        if (bigIcon != null) {
            bigIcon.setIconLiteral("fas-play");
            bigIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        }
    }

    private String encodeMediaUrl(String rawUrl) {
        try {
            URL url = new URL(rawUrl);

            String normalizedPath = Normalizer.normalize(url.getPath(), Normalizer.Form.NFC);

            String encodedPath = Arrays.stream(normalizedPath.split("/"))
                    .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8)
                            .replace("+", "%20"))
                    .collect(Collectors.joining("/"));

            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            String portStr = (port == -1) ? "" : ":" + port;

            return protocol + "://" + host + portStr + encodedPath;

        } catch (Exception e) {
            System.err.println("URL Encoding Error: " + e.getMessage());
            return rawUrl;
        }
    }

//    private File decryptToTemp(File encryptedFile) throws Exception {
//        File tempFile = File.createTempFile("play_", ".mp3");
//        tempFile.deleteOnExit();
//
//        try (FileInputStream fis = new FileInputStream(encryptedFile);
//             CipherInputStream cis = CryptoUtil.decrypt(fis);
//             FileOutputStream fos = new FileOutputStream(tempFile)) {
//
//            byte[] buffer = new byte[8192];
//            int read;
//
//            while ((read = cis.read(buffer)) != -1) {
//                fos.write(buffer, 0, read);
//            }
//        }
//
//        return tempFile;
//    }

    // For Windows
    private File decryptToTemp(File encryptedFile) throws Exception {
        File tempDir = new File(System.getProperty("user.home")
                + File.separator + ".scamusica"
                + File.separator + "temp");
        tempDir.mkdirs();
        File tempFile = new File(tempDir, "play_" + System.currentTimeMillis() + ".mp3");
        tempFile.deleteOnExit(); // ✅ Same as before

        try (FileInputStream fis = new FileInputStream(encryptedFile);
             CipherInputStream cis = CryptoUtil.decrypt(fis);
             FileOutputStream fos = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = cis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }

        return tempFile;
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    public static void main(String[] args) {
        launch(args);
    }
}
