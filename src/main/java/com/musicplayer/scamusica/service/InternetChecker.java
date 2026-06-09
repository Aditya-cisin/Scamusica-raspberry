package com.musicplayer.scamusica.service;

import java.net.HttpURLConnection;
import java.net.URL;

public final class InternetChecker {

    private InternetChecker() {}

    public static boolean hasInternet() {
        HttpURLConnection con = null;
        try {
            con = (HttpURLConnection) new URL("https://www.google.com/generate_204").openConnection();
            con.setConnectTimeout(3000);
            con.setReadTimeout(3000);
            con.setRequestMethod("GET");
            con.connect();
            int code = con.getResponseCode();
            return code == 204 || code == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (con != null) {
                try {
                    con.disconnect();
                } catch (Exception ignored) {}
            }
        }
    }
}
