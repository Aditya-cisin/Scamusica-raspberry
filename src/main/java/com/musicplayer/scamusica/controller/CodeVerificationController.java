package com.musicplayer.scamusica.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicplayer.scamusica.manager.DeviceFingerprint;
import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.service.ConnectivityMonitor;
import com.musicplayer.scamusica.ui.LangItem;
import com.musicplayer.scamusica.util.Utility;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class CodeVerificationController extends Application {


    private static HttpClient client;

    private BorderPane root;
    private HBox header;

    private Image logo;

    private boolean onlineStatus= true;

    @Override
    public void start(Stage primaryStage) {
        ConnectivityMonitor monitor = new ConnectivityMonitor(status -> {
            setOnlineStatus(status);
        });
        monitor.start();

        root = new BorderPane();

        Image background = new Image(getClass().getResource("/images/background.jpg").toExternalForm()); // Path to background image

        BackgroundSize backgroundSize = new BackgroundSize(
                BackgroundSize.AUTO, BackgroundSize.AUTO, // Width and height
                true, false, // Contain and Cover
                true, // Proportional
                true // Fill width
        );
        BackgroundImage backgroundImage = new BackgroundImage(
                background,
                BackgroundRepeat.NO_REPEAT,
                BackgroundRepeat.NO_REPEAT,
                BackgroundPosition.CENTER,
                backgroundSize
        );

        root.setBackground(new Background(backgroundImage));

        header = new HBox(15);
        header.setPadding(new Insets(15, 50, 15, 50));
        header.setStyle("-fx-background-color: transparent;");

        ImageView imgLogo;
        try {
            logo = new Image(getClass().getResource("/images/logo.png").toExternalForm());
            imgLogo = new ImageView(logo);
        } catch (Exception e) {
            imgLogo = new ImageView();
        }
        imgLogo.setFitHeight(41);
        imgLogo.setFitWidth(178);
        header.getChildren().add(imgLogo);
        header.setAlignment(Pos.TOP_LEFT);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        ComboBox<LangItem> languageBox =LanguageManager.createLanguageSelector();
        languageBox.setStyle("  -fx-background-color: transparent;\n" +
                "    -fx-border-color: #6E68A5;\n" +
                "    -fx-border-width: 1px;\n" +
                "    -fx-background-radius: 18;\n" +
                "    -fx-border-radius: 18;\n" +
                "    -fx-padding: 4 10 4 10;\n" +
                "    -fx-font-size: 12px;\n" +
                "    -fx-font-family: \"Poppins\";\n" +
                "    -fx-text-fill: white;\n" +
                "    -fx-pref-width: 150;\n" +
                "    -fx-cursor: hand;");
        header.getChildren().addAll(spacer, languageBox);

        root.setTop(header);

        Label passwordLabel = new Label();
        passwordLabel.textProperty().bind(LanguageManager.createStringBinding("label.code"));
        passwordLabel.setTextFill(Color.BLACK);
        passwordLabel.setStyle("-fx-font-size: 15px;"+
                "-fx-font-weight: bold;");
        TextField passwordField = new TextField();
        passwordField.promptTextProperty().bind(
                LanguageManager.createStringBinding("text.example")
                        .concat(" 134654")
        );
        passwordField.setPrefWidth(300);
        passwordField.setPrefHeight(30);
        passwordField.setStyle( "-fx-background-radius: 10;");
        passwordField.setMinWidth(Region.USE_PREF_SIZE);
        passwordField.setMaxWidth(Region.USE_PREF_SIZE);

        Button loginButton = new Button();
        loginButton.textProperty().bind(LanguageManager.createStringBinding("button.start"));
        loginButton.setStyle("-fx-background-color: #6E68A5; " +
                "-fx-text-fill: white; " +
                "-fx-font-size: 12px; " +
                "-fx-font-weight: bold;"+
                "-fx-background-radius: 50;");
        loginButton.setPrefWidth(300);
        loginButton.setPrefHeight(30);

        Text messageText = new Text();
        messageText.setFill(Color.RED);

        VBox loginBox = new VBox(10);
        loginBox.setAlignment(Pos.CENTER_LEFT);
        loginBox.setPrefSize(150, 200);
        loginBox.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        loginBox.setStyle("-fx-background-color: white;"+
                "-fx-text-fill: black; " +
                "-fx-background-radius: 20;");
        loginBox.setPadding(new Insets(20));
        loginBox.getChildren().addAll(
                passwordLabel, passwordField,
                loginButton, messageText);

        root.setCenter(loginBox);

        loginButton.setOnAction(event -> {
            if(!SessionManager.isUserLoggedIn()) {
                if(onlineStatus) {
                    String enteredPassword = passwordField.getText();

                    if (enteredPassword.isEmpty()) {
                        messageText.textProperty().bind(LanguageManager.createStringBinding("text.codeError"));
                        return;
                    }

                    loginButton.setDisable(true);
                    messageText.textProperty().bind(LanguageManager.createStringBinding("text.verify"));
                    messageText.setFill(Color.GREEN);

                    new Thread(() -> {
                        try {
                            client = HttpClient.newHttpClient();

                            String deviceId = DeviceFingerprint.getFingerprint();

                            String requestBody = "{"
                                    + "\"licenseCode\": \"" + enteredPassword + "\","
                                    + "\"deviceId\": \"" + deviceId + "\""
                                    + "}";

                            HttpRequest request = HttpRequest.newBuilder()
                                    .uri(URI.create(Utility.BASE_URL.get() + Utility.VERIFY_LICENSE_CODE.get()))
                                    .timeout(Duration.ofSeconds(8))
                                    .header("Content-Type", "application/json")
                                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                                    .build();

                            HttpResponse<String> response =
                                    client.send(request, HttpResponse.BodyHandlers.ofString());

                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode jsonNode = mapper.readTree(response.body());
                            boolean success = jsonNode.get("success").asBoolean();
                            String message = jsonNode.get("message").asText();
                            if (success) {
                                Integer userId = jsonNode.get("playerId").asInt();
                                SessionManager.saveToken(jsonNode.get("token").asText(), userId, LanguageManager.getLangCode());

                                Platform.runLater(() -> {
                                    PlayerController controller = new PlayerController();
                                    controller.start(primaryStage);
                                });

                            } else {
                                Platform.runLater(() -> {
                                    messageText.setFill(Color.RED);
                                    if(message.contains("This license is already registered to another device")){
                                        messageText.textProperty().bind(LanguageManager.createStringBinding("text.codeAlreadyActivated"));
                                    }else{
                                    messageText.textProperty().bind(LanguageManager.createStringBinding("text.activationError"));
                                    }
                                });
                            }

                        } catch (Exception e) {
                            Platform.runLater(() -> {
                                messageText.setFill(Color.RED);
                                System.err.println("error: " + e.getMessage());
                                messageText.textProperty().bind(LanguageManager.createStringBinding("text.activationError"));
                            });

                        } finally {
                            Platform.runLater(() -> loginButton.setDisable(false));
                        }

                    }).start();
                }else{
                    Platform.runLater(() -> {
                        messageText.setFill(Color.RED);
                        messageText.textProperty().bind(LanguageManager.createStringBinding("text.internetError"));
                    });
                }
            }else{
                PlayerController controller = new PlayerController();
                controller.start(primaryStage);
            }
        });

        Scene scene = new Scene(root, 960, 600);
        primaryStage.setScene(scene);
        primaryStage.titleProperty().bind(
                LanguageManager.createStringBinding("app.title")
        );
        primaryStage.setMinHeight(600);
        primaryStage.setMinWidth(960);
        primaryStage.show();

    }
    private void setOnlineStatus(ConnectivityMonitor.Status status) {
        if (status == ConnectivityMonitor.Status.ONLINE) {
            onlineStatus=true;
        } else {
            onlineStatus=false;
        }
    }
    public static void reloadUI() {
        Platform.runLater(() -> {
            Stage stage = (Stage) Stage.getWindows().filtered(Window::isShowing).get(0);

            CodeVerificationController controller = new CodeVerificationController();
            try {
                controller.start(stage);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
    public static void main(String[] args) {
        launch(args);
    }
}
