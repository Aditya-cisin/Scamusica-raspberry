package com.musicplayer.scamusica.controller;

import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.model.PlaylistTrack;
import com.musicplayer.scamusica.service.DownloadManager;
import com.musicplayer.scamusica.service.PlaylistApiService;
import com.musicplayer.scamusica.ui.*;

import com.musicplayer.scamusica.util.CryptoUtil;
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
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import javax.crypto.CipherInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class PlayerController extends Application {

    private MediaPlayer mediaPlayer;

    private final PlayerSidebar sidebarUtil = new PlayerSidebar();
    private final PlayerHeader headerUtil = new PlayerHeader();
    private final PlayerDropdown dropdownUtil = new PlayerDropdown();
    private final PlayerControls controlsUtil = new PlayerControls();
    private final PlayerAlbum albumUtil = new PlayerAlbum();

    private VBox playlistDropdownCard;
    private HBox playlistPill;

    private volatile String currentPlaylistName = "";

    private final List<PlaylistTrack> playQueue = new ArrayList<>();
    private int currentTrackIndex = 0;

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

    @Override
    public void start(Stage primaryStage) {
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

        Label albumHeading = albumUtil.createAlbumHeading();
        ImageView img = albumUtil.createAlbumImage(getClass());
        albumImageView = img;
        albumUtil.applyClip(img);
        HBox songsBox = albumUtil.createSongsBox();

        VBox leftAlbumVBox = albumUtil.createLeftAlbumVBox(albumHeading, img, songsBox);

        recomputeGlobalCountAndUpdateUI();

        List<String> tempList;
        try {
            PlaylistApiService playlistApiService = new PlaylistApiService();
            List<String> apiPlaylists = playlistApiService.fetchPlaylistTitles();
            if (apiPlaylists != null && !apiPlaylists.isEmpty()) {
                tempList = new ArrayList<>(apiPlaylists);
            } else {
                tempList = tempPlaylist;
                System.out.println("[PlayerController] API returned empty list, using fallback playlists.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            tempList = tempPlaylist;
            System.out.println("[PlayerController] Error while fetching playlists, using fallback playlists.");
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

        VBox rightColumn = new VBox(8);
        rightColumn.getChildren().addAll(playlistHeaderBox);
        HBox rightWrapper = new HBox(rightColumn);
        rightWrapper.setAlignment(Pos.TOP_RIGHT);

        Label titleCentered = headerUtil.createPlayerTitle();
        VBox centerContainer = headerUtil.createCenterContainer(titleCentered);
        HBox topRow = albumUtil.createTopRow(leftAlbumVBox, centerContainer, rightWrapper);

        Slider progressSlider = controlsUtil.createProgressSlider();
        Label leftTime = controlsUtil.createTimeLabel(false);
        Label rightTime = controlsUtil.createTimeLabel(true);
        HBox timesRow = controlsUtil.createTimesRow(leftTime, rightTime);
        HBox progressRow = controlsUtil.createProgressRow(progressSlider);
        VBox sliderContainer = controlsUtil.createSliderContainer(titleCentered, timesRow, progressRow);
        HBox controlsWrapper = controlsUtil.createControls(progressSlider, playlistPill);
        HBox bottomBar = controlsUtil.createBottomBar();
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
            System.out.println("[PlayerController] Closing application...");

            if (mediaPlayer != null) {
                try {
                    mediaPlayer.stop();
                    mediaPlayer.dispose();
                } catch (Exception ignored) {
                }
            }

            if (downloadManager != null) {
                try {
                    downloadManager.stop();
                } catch (Exception ignored) {
                }
            }

            Platform.exit();

            System.exit(0);
        });

        primaryStage.show();

        Platform.runLater(() -> {
            controlsUtil.setupSliderFill(progressSlider);
            controlsUtil.setupVolumeSliderFill(controlsUtil.getVolumeSlider(bottomBar));

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
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        });
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
                System.out.println("[PlayerController] Base dir created: " + created);
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

        this.currentPlaylistName = playlistName;

        stopPlayback(progressSlider, leftTime, rightTime, controlsWrapper, downloadLabel);

        playQueue.clear();
        currentTrackIndex = 0;

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
                System.out.println("[PlayerController] Genre folder created: " + created + " at " + genreFolderPath);
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

            if (needDownload) {
                setGenreSwitchEnabled(false);
            } else {
                setGenreSwitchEnabled(true);
            }

            if (!downloadSeq.isEmpty()) {
                downloadManager = new DownloadManager(downloadSeq, genreFolderPath,
                        new DownloadManager.DownloadListener() {
                            @Override
                            public void onDownloadStarted(int songId, File outputFile) {
                                currentFileProgressFraction = 0.0;
                                setGenreSwitchEnabled(false);

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

                            @Override
                            public void onDownloadCompleted(int songId, File outputFile) {

                                recomputeGlobalCountAndUpdateUI();

                                Platform.runLater(() -> {

                                    int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                    currentGenreDownloadedCount.set(newGenreCount);

                                    currentFileProgressFraction = 0.0;

                                    updateGenreDownloadLabel(downloadLabel);
                                    updatePlayButtonState(controlsWrapper);

                                    if (newGenreCount >= 2) {
                                        if (mediaPlayer == null || mediaPlayer.getStatus() != MediaPlayer.Status.PLAYING) {
                                            try {
                                                System.out.println("[AutoPlay] 2 songs downloaded. Starting playback." +
                                                        "..");
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
                                            } catch (URISyntaxException e) {
                                                e.printStackTrace();
                                            }
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
                                System.out.println("[PlayerController] Download failed id=" + songId + " -> " + ex.getMessage());
                                currentFileProgressFraction = 0.0;
                                updateGenreDownloadLabel(downloadLabel);
                            }

                            @Override
                            public void onAllDownloadsFinished() {
                                System.out.println("[PlayerController] All downloads finished for genre: " + playlistName);
                                setGenreSwitchEnabled(true);

                                recomputeGlobalCountAndUpdateUI();
                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);

                                updatePlayButtonState(controlsWrapper);
                            }

                            @Override
                            public void onCancelled() {
                                System.out.println("[PlayerController] Downloads cancelled for genre: " + playlistName);
                                setGenreSwitchEnabled(true);
                                recomputeGlobalCountAndUpdateUI();
                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);
                                updatePlayButtonState(controlsWrapper);
                            }
                        });

                downloadManager.start();
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

        Platform.runLater(() -> {
            try {
                downloadLabel.textProperty().unbind();
            } catch (Exception ignored) {
            }
            downloadLabel.setText(text);
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

        if (albumImageView != null) {
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

        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }

        String safeUrl = encodeMediaUrl(track.getUrl());

        if (!safeUrl.contains(".mp3")) {
            safeUrl += "?ext=.mp3";
        }

        System.out.println("FIXED MEDIA URL = " + safeUrl);

        try {
            String baseDownloadDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "downloads";

//            String genreFolder = track.getFolderTitle().replaceAll("\\s+", "_");
            String genreFolder = currentPlaylistName.replaceAll("\\s+", "_");

            File encryptedFile = new File(baseDownloadDir + File.separator + genreFolder,
                    "song-" + track.getId() + ".dat");

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("[PlayTrack] Track index      = " + currentTrackIndex);
            System.out.println("[PlayTrack] Track ID         = " + track.getId());
            System.out.println("[PlayTrack] Track title      = " + track.getTitle());
            System.out.println("[PlayTrack] Track URL        = " + track.getUrl());
            System.out.println("[PlayTrack] folderTitle      = " + track.getFolderTitle());
            System.out.println("[PlayTrack] currentPlaylist  = " + currentPlaylistName);
            System.out.println("[PlayTrack] genreFolder      = " + genreFolder);
            System.out.println("[PlayTrack] Encrypted path   = " + encryptedFile.getAbsolutePath());
            System.out.println("[PlayTrack] File exists      = " + encryptedFile.exists());
            System.out.println("[PlayTrack] File size        = " + (encryptedFile.exists() ? encryptedFile.length() + " bytes" : "N/A"));

            if (encryptedFile.exists()) {
                System.out.println("[PlayTrack] ✅ Local file found — decrypting to temp...");
                new Thread(() -> {
                    try {
                        System.out.println("[PlayTrack][Thread] Starting decryption...");
                        File tempFile = decryptToTemp(encryptedFile);

                        System.out.println("[PlayTrack][Thread] Decryption done. Temp = " + tempFile.getAbsolutePath());
                        System.out.println("[PlayTrack][Thread] Temp file size = " + tempFile.length() + " bytes");

                        String localUrl = tempFile.toURI().toString();

                        if (!localUrl.contains(".mp3")) {
                            localUrl += "?ext=.mp3";
                        }

                        final String finalUrl = localUrl;

                        System.out.println("[PlayTrack][Thread] Final local URL = " + finalUrl);

                        Platform.runLater(() -> {
                            System.out.println("[PlayTrack][UI] Creating Media from local URL...");

                            Media mediaLocal = new Media(finalUrl);
                            System.out.println("[PlayTrack][UI] Creating Media from local URL...");
                            MediaPlayer newPlayer = new MediaPlayer(mediaLocal);
                            System.out.println("[PlayTrack][UI] MediaPlayer created OK");
                            attachMediaPlayerHandlers(newPlayer,
                                    albumHeading,
                                    titleLabel,
                                    progressSlider,
                                    leftTime,
                                    rightTime,
                                    controlsWrapper,
                                    bottomBar,
                                    downloadLabel,
                                    autoPlay);

                            mediaPlayer = newPlayer;
                        });

                    } catch (Exception e) {
                        System.out.println("[PlayTrack][UI] ❌ Exception creating Media/MediaPlayer: " + e.getMessage());
                        e.printStackTrace();
                    }
                }).start();

                return;

            }

        } catch (Exception e) {
            System.out.println("[PlayTrack][Thread] ❌ Decryption failed: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("[PlayTrack] ⚠️ Local file NOT found — falling back to stream URL");
        System.out.println("[PlayTrack] Stream URL = " + safeUrl);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        Media media = new Media(safeUrl);
        MediaPlayer newPlayer = new MediaPlayer(media);

        attachMediaPlayerHandlers(newPlayer,
                albumHeading,
                titleLabel,
                progressSlider,
                leftTime,
                rightTime,
                controlsWrapper,
                bottomBar,
                downloadLabel,
                autoPlay);

        mediaPlayer = newPlayer;
    }

    private void attachMediaPlayerHandlers(MediaPlayer mediaPlayer,
                                           Label albumHeading,
                                           Label titleLabel,
                                           Slider progressSlider,
                                           Label leftTime,
                                           Label rightTime,
                                           HBox controlsWrapper,
                                           HBox bottomBar,
                                           Label downloadLabel,
                                           boolean autoPlay) {

        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("[MediaPlayer] attachMediaPlayerHandlers() called");
        System.out.println("[MediaPlayer] autoPlay = " + autoPlay);


        Media media = mediaPlayer.getMedia();
        if (media != null) {
            System.out.println("[MediaPlayer] Media source URI = " + media.getSource());
        } else {
            System.out.println("[MediaPlayer] ⚠️ Media object is NULL");
        }

        // ── STATUS LISTENER ──────────────────────────────────────────
        mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
            System.out.println("[MediaPlayer][STATUS] " + oldStatus + " → " + newStatus);
        });

        mediaPlayer.setOnError(() -> {
            MediaException error = mediaPlayer.getError();
            if (error != null) {
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                System.out.println("[MediaPlayer][ERROR] ❌ Type    = " + error.getType());
                System.out.println("[MediaPlayer][ERROR] ❌ Message = " + error.getMessage());
                Throwable cause = error.getCause();
                if (cause != null) {
                    System.out.println("[MediaPlayer][ERROR] ❌ Cause   = " + cause.getMessage());
                    cause.printStackTrace();
                } else {
                    System.out.println("[MediaPlayer][ERROR] ❌ Cause   = (null)");
                }
                System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            } else {
                System.out.println("[MediaPlayer][ERROR] ❌ Error fired but getError() is NULL");
            }
        });

        // ── MEDIA ERROR LISTENER (Media object level) ─────────────────
        if (media != null) {
            media.setOnError(() -> {
                MediaException me = media.getError();
                if (me != null) {
                    System.out.println("[Media][ERROR] ❌ Media-level error: " + me.getType() + " — " + me.getMessage());
                    if (me.getCause() != null) {
                        System.out.println("[Media][ERROR] ❌ Cause: " + me.getCause().getMessage());
                    }
                }
            });
        }

        // ── STALLED ──────────────────────────────────────────────────
        mediaPlayer.setOnStalled(() -> {
            System.out.println("[MediaPlayer][STALLED] ⚠️ Playback stalled (buffering?)");
        });

        // ── BUFFERING ────────────────────────────────────────────────
        mediaPlayer.bufferProgressTimeProperty().addListener((obs, oldVal, newVal) -> {
            System.out.println("[MediaPlayer][BUFFER] Buffered up to = " + newVal);
        });

        mediaPlayer.setOnReady(() -> {

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            System.out.println("[MediaPlayer][READY] ✅ Media is READY");

            Duration mediaDuration = mediaPlayer.getMedia().getDuration();

            System.out.println("[MediaPlayer][READY] Duration (raw)     = " + mediaDuration);
            System.out.println("[MediaPlayer][READY] Duration (seconds) = " + mediaDuration.toSeconds());

            // Print all media metadata
            System.out.println("[MediaPlayer][READY] --- Metadata ---");
            mediaPlayer.getMedia().getMetadata().forEach((k, v) ->
                    System.out.println("[MediaPlayer][READY] Meta: " + k + " = " + v)
            );
            System.out.println("[MediaPlayer][READY] --- End Metadata ---");

            int durationSeconds = (int) Math.max(1, mediaDuration.toSeconds());

            controlsUtil.setupMediaBindingsWithDuration(
                    mediaPlayer,
                    progressSlider,
                    leftTime,
                    rightTime,
                    durationSeconds
            );

            controlsUtil.setupControlEvents(
                    controlsWrapper,
                    mediaPlayer,
                    progressSlider,
                    leftTime,
                    rightTime,
                    controlsUtil.getVolumeSlider(bottomBar),
                    durationSeconds,
                    downloadLabel
            );

            setupBigPlayBehaviour(
                    albumHeading,
                    titleLabel,
                    controlsWrapper,
                    progressSlider,
                    leftTime,
                    rightTime,
                    bottomBar,
                    downloadLabel
            );

            mediaPlayer.setOnEndOfMedia(() -> {
                System.out.println("[MediaPlayer][END] Track ended. Moving to next...");
                try {
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
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            });

            FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);

            if (autoPlay) {
                System.out.println("[MediaPlayer][READY] ▶ autoPlay=true → calling play()");
                if (bigIcon != null) {
                    bigIcon.setIconLiteral("fas-pause");
                }
                mediaPlayer.play();
            } else {
                System.out.println("[MediaPlayer][READY] ⏸ autoPlay=false → calling pause()");
                if (bigIcon != null) {
                    bigIcon.setIconLiteral("fas-play");
                }
                mediaPlayer.pause();
                mediaPlayer.seek(Duration.ZERO);
            }

            System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        });
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

        if (currentTrackIndex >= playQueue.size()) {
            System.out.println("[PlayerController] All tracks finished. Stopping playback.");
            currentTrackIndex = playQueue.size();
            stopPlayback(progressSlider, leftTime, rightTime, controlsWrapper, downloadLabel);
            return;
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

            if (mediaPlayer == null || currentTrackIndex >= playQueue.size() || currentTrackIndex < 0) {
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

            MediaPlayer.Status status = mediaPlayer.getStatus();
            if (status == MediaPlayer.Status.PLAYING) {
                mediaPlayer.pause();
                bigIcon.setIconLiteral("fas-play");
                bigIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            } else {
                mediaPlayer.play();
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

        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
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

    private File decryptToTemp(File encryptedFile) throws Exception {
        File tempFile = File.createTempFile("play_", ".mp3");
        tempFile.deleteOnExit();

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


    public static void main(String[] args) {
        launch(args);
    }
}
