package com.musicplayer.scamusica.ui;

import com.musicplayer.scamusica.manager.LanguageManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

public class PlayerControls {

    private boolean seeking = false;

    public Slider createProgressSlider() {
        Slider slider = new Slider(0, 100, 0);
        slider.getStyleClass().add("player-progress");
        slider.setMinWidth(300);
        return slider;
    }

    public Label createTimeLabel(boolean isRemaining) {
        Label lbl = new Label(isRemaining ? "-0:00" : "0:00");
        lbl.getStyleClass().add("time-label");
        return lbl;
    }

    public HBox createTimesRow(Label leftTime, Label rightTime) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox timesRow = new HBox(leftTime, spacer, rightTime);
        timesRow.setAlignment(javafx.geometry.Pos.CENTER);
        timesRow.setPadding(new Insets(0, 12, 6, 12));
        return timesRow;
    }

    public HBox createProgressRow(Slider progressSlider) {
        HBox progressRow = new HBox(progressSlider);
        progressRow.setAlignment(javafx.geometry.Pos.CENTER);
        progressRow.setPadding(new Insets(0, 18, 0, 18));
        HBox.setHgrow(progressSlider, Priority.ALWAYS);
        return progressRow;
    }

    public VBox createSliderContainer(Label titleCentered, HBox timesRow, HBox progressRow) {
        VBox sliderContainer = new VBox(4, titleCentered, timesRow, progressRow);
        sliderContainer.setAlignment(javafx.geometry.Pos.CENTER);
        sliderContainer.setPadding(new Insets(4, 24, 2, 24));
        return sliderContainer;
    }

    public HBox createControls(Slider progressSlider, Node pill) {
        FontIcon dislikeIcon = new FontIcon("fas-thumbs-down");
        dislikeIcon.setIconSize(32);
        Button dislikeBtn = new Button("", dislikeIcon);
        dislikeBtn.getStyleClass().add("control-icon");
        dislikeBtn.getStyleClass().add("small-control");

        FontIcon likeIcon = new FontIcon("fas-thumbs-up");
        likeIcon.setIconSize(32);
        Button likeBtn = new Button("", likeIcon);
        likeBtn.getStyleClass().add("control-icon");
        likeBtn.getStyleClass().add("small-control");

        FontIcon bigPlayIcon = new FontIcon("fas-play");
        bigPlayIcon.setIconSize(35);
        bigPlayIcon.setIconColor(Color.web("#fff"));
        Button bigPlayBtn = new Button("", bigPlayIcon);
        bigPlayBtn.setGraphic(bigPlayIcon);

        bigPlayBtn.setMinSize(80, 80);
        bigPlayBtn.setPrefSize(80, 80);
        bigPlayBtn.setMaxSize(80, 80);
        bigPlayBtn.setPadding(new Insets(0, -2, 0, 0));
        bigPlayBtn.setStyle(
                "-fx-background-color: #6E68A5; " +
                        "-fx-text-fill: white;" +
                        "-fx-background-radius: 999px; " +
                        "-fx-effect: dropshadow(gaussian, rgba(126,97,204,0.28), 22, 0, 0, 8); " +
                        "-fx-alignment: center;"
        );
        bigPlayBtn.getStyleClass().add("big-play-button");

        FontIcon forwardIcon = new FontIcon("fas-forward");
        forwardIcon.setIconSize(26);
        Button forwardBtn = new Button("", forwardIcon);
        forwardBtn.setId("forwardButton");
        forwardBtn.getStyleClass().add("control-icon");
        forwardBtn.getStyleClass().add("small-control");

        dislikeBtn.setPrefSize(46, 46);
        likeBtn.setPrefSize(46, 46);
        forwardBtn.setPrefSize(46, 46);

        dislikeBtn.setPadding(new Insets(0));
        likeBtn.setPadding(new Insets(0));
        forwardBtn.setPadding(new Insets(0));

        HBox likeDislikeBox = new HBox();
        likeDislikeBox.getStyleClass().add("small-control-box");
        likeDislikeBox.setAlignment(javafx.geometry.Pos.CENTER);
        likeDislikeBox.setSpacing(4);
        likeDislikeBox.getChildren().addAll(dislikeBtn, likeBtn);

        StackPane playContainer = new StackPane();
        playContainer.getStyleClass().add("play-control-box");
        playContainer.setMinSize(100, 100);
        playContainer.setPrefSize(120, 100);
        playContainer.setMaxSize(160, 100);
        playContainer.setAlignment(javafx.geometry.Pos.CENTER);
        playContainer.setPadding(new Insets(0, 4, 0, 4));
        playContainer.getChildren().add(bigPlayBtn);

        HBox forwardBox = new HBox();
        forwardBox.getStyleClass().add("forward-control-box");
        forwardBox.setAlignment(javafx.geometry.Pos.CENTER);
        forwardBox.getChildren().add(forwardBtn);

        HBox controlsWrapper = new HBox();
        controlsWrapper.getStyleClass().add("controls-wrapper");
        controlsWrapper.setAlignment(javafx.geometry.Pos.CENTER);
        controlsWrapper.setSpacing(14);
        controlsWrapper.setPadding(new Insets(6, 0, 6, 0));
        controlsWrapper.getChildren().addAll(likeDislikeBox, playContainer, forwardBox);

        dislikeBtn.setOnAction(e -> {
            if (dislikeBtn.getStyleClass().contains("control-active"))
                dislikeBtn.getStyleClass().remove("control-active");
            else {
                dislikeBtn.getStyleClass().add("control-active");
                likeBtn.getStyleClass().remove("control-active");
            }
        });

        likeBtn.setOnAction(e -> {
            if (likeBtn.getStyleClass().contains("control-active")) likeBtn.getStyleClass().remove("control-active");
            else {
                likeBtn.getStyleClass().add("control-active");
                dislikeBtn.getStyleClass().remove("control-active");
            }
        });

        forwardBtn.setOnAction(e -> {
            forwardBtn.getStyleClass().add("control-active");
            Platform.runLater(() -> forwardBtn.getStyleClass().remove("control-active"));
        });
        return controlsWrapper;
    }

    public HBox createBottomBar() {
        FontIcon downloadIcon = new FontIcon("fas-download");
        downloadIcon.setIconSize(14);
        downloadIcon.getStyleClass().add("download-icon");
        Label downloadLabel = new Label();
        downloadLabel.textProperty().bind(
                Bindings.concat(
                        "0%",
                        " ",
                        LanguageManager.createStringBinding("label.download")
                ));
        downloadLabel.getStyleClass().add("download-label");
        downloadLabel.setGraphicTextGap(10);
        downloadLabel.setPadding(new Insets(0, 0, 0, 40));

        FontIcon volLow = new FontIcon("fas-volume-down");
        volLow.setIconSize(14);
        volLow.getStyleClass().add("volume-icon");
        Slider volumeSlider = new Slider(0, 100, 50);
        volumeSlider.setPrefWidth(320);
        volumeSlider.getStyleClass().add("volume-slider");
        FontIcon volHigh = new FontIcon("fas-volume-up");
        volHigh.setIconSize(14);
        volHigh.getStyleClass().add("volume-icon");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);

        HBox bottomBar = new HBox(12, downloadLabel, hSpacer, volLow, volumeSlider, volHigh);
        bottomBar.getStyleClass().add("bottom-bar");
        bottomBar.setPadding(new Insets(14, 24, 24, 24));
        bottomBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return bottomBar;
    }

    public void setupSliderFill(Slider progressSlider) {
        Node pTrackNode = progressSlider.lookup(".track");
        if (pTrackNode instanceof Region) {
            Region pTrack = (Region) pTrackNode;
            updateSliderFill(pTrack, 0, "#7e61cc", "#00000040");
            progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
                double max = progressSlider.getMax() <= 0 ? 100 : progressSlider.getMax();
                double pct = (max <= 0) ? 0 : (newV.doubleValue() / max * 100);
                updateSliderFill(pTrack, pct, "#7e61cc", "#00000040");
            });
        } else {
            progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
                double max = progressSlider.getMax() <= 0 ? 100 : progressSlider.getMax();
                double pct = (max <= 0) ? 0 : (newV.doubleValue() / max * 100);
                String pctStr = String.format(Locale.US, "%.4f", pct);
                progressSlider.setStyle("-fx-background-color: linear-gradient(to right, #7e61cc " + pctStr + "%, " +
                        "#00000040 " + pctStr + "%);");
            });
        }
    }

    public void setupVolumeSliderFill(Slider volumeSlider) {
        Node vTrackNode = volumeSlider.lookup(".track");
        if (vTrackNode instanceof Region) {
            Region vTrack = (Region) vTrackNode;
            updateSliderFill(vTrack, volumeSlider.getValue(), "#7e61cc", "#00000040");
            volumeSlider.valueProperty().addListener((obs, oldV, newV) -> updateSliderFill(vTrack,
                    newV.doubleValue(), "#7e61cc", "#00000040"));
        } else {
            volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
                double pct = newV.doubleValue();
                String pctStr = String.format(Locale.US, "%.4f", pct);
                volumeSlider.setStyle("-fx-background-color: linear-gradient(to right, #7e61cc " + pctStr + "%, " +
                        "#00000040 " + pctStr + "%);");
            });
        }
    }

    private void updateSliderFill(Region track, double pct, String fillColor, String bgColor) {
        double clamped = Math.max(0.0, Math.min(100.0, pct));
        String pctStr = String.format(Locale.US, "%.4f", clamped);
        String style = "-fx-background-color: linear-gradient(to right, " +
                fillColor + " 0%, " + fillColor + " " + pctStr + "%, " + bgColor + " " + pctStr + "%, " + bgColor +
                " 100%);" +
                "-fx-background-radius: 6;";
        track.setStyle(style);
    }

    public void setupBigPlayButtonDummy(HBox controlsWrapper) {
        Button bigPlayBtn = (Button) ((StackPane) controlsWrapper.getChildren().get(1)).getChildren().get(0);
        bigPlayBtn.setOnAction(e -> {
            FontIcon bigIcon = (FontIcon) bigPlayBtn.getGraphic();
            String cur = bigIcon.getIconLiteral();
            if ("fas-play".equals(cur) || cur == null) {
                bigIcon.setIconLiteral("fas-pause");
                bigIcon.setIconColor(Color.WHITE);
            } else {
                bigIcon.setIconLiteral("fas-play");
                bigIcon.setIconColor(Color.WHITE);
            }
        });
    }

    public void setupDummySlider(Slider progressSlider, Label leftTime, Label rightTime, int duration) {
        progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double max = progressSlider.getMax() <= 0 ? 100 : progressSlider.getMax();
            int curSec = (int) Math.round((newV.doubleValue() / max) * duration);
            if (curSec < 0) curSec = 0;
            int rem = duration - curSec;
            updateTimeLabel(leftTime, Duration.seconds(curSec), false);
            updateTimeLabel(rightTime, Duration.seconds(rem), true);
        });
    }

    private void updateTimeLabel(Label lbl, Duration dur, boolean isRemaining) {
        if (dur == null || dur.isUnknown()) {
            lbl.setText(isRemaining ? "-0:00" : "0:00");
            return;
        }
        int totalSeconds = (int) Math.floor(dur.toSeconds());
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String text = String.format("%d:%02d", minutes, seconds);
        if (isRemaining && !text.startsWith("-")) text = "-" + text;
        lbl.setText(text);
    }

    public Slider getVolumeSlider(HBox bottomBar) {
        for (Node n : bottomBar.getChildren()) {
            if (n instanceof Slider) return (Slider) n;
        }
        return null;
    }

    public Label getDownloadLabel(HBox bottomBar) {
        for (Node n : bottomBar.getChildren()) {
            if (n instanceof Label) {
                return (Label) n;
            }
        }
        return null;
    }

    public FontIcon getBigPlayIcon(HBox controlsWrapper) {
        try {
            Button bigPlayBtn =
                    (Button) ((StackPane) controlsWrapper.getChildren().get(1))
                            .getChildren().get(0);
            return (FontIcon) bigPlayBtn.getGraphic();
        } catch (Exception e) {
            return null;
        }
    }
}
