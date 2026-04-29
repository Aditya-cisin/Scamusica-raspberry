package com.musicplayer.scamusica;

import com.musicplayer.scamusica.controller.CodeVerificationController;
import com.musicplayer.scamusica.controller.PlayerController;
import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.manager.SessionManager;
import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    private static void setupVlc() {
        try {
            String basePath = new java.io.File(
                    Main.class.getProtectionDomain()
                            .getCodeSource()
                            .getLocation()
                            .toURI()
            ).getParent();

            String vlcPath = basePath + "/lib/vlc";
            String pluginPath = vlcPath + "/plugins";

            System.setProperty("jna.library.path", vlcPath);
            System.setProperty("VLC_PLUGIN_PATH", pluginPath);

            System.out.println("✅ VLC Path: " + vlcPath);
            System.out.println("✅ Plugin Path: " + pluginPath);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void start(Stage primaryStage) {
         // ✅ Set proxy BEFORE anything else
        System.setProperty("java.net.useSystemProxies", "true");
        //set prefer language from the session
        String savedLang = SessionManager.getLanguage();
        LanguageManager.setLanguage(savedLang!=null? savedLang:"en");
        if (SessionManager.isUserLoggedIn()) {
            // User already has valid token → skip login screen
            System.out.println("Auto-login using saved token");
            new PlayerController().start(primaryStage);
            return;
        }else{
            CodeVerificationController codeVerificationController = new CodeVerificationController();
            codeVerificationController.start(primaryStage);
        }

    }

    public static void main(String[] args) {
        setupVlc();
        launch(args);
    }
}
