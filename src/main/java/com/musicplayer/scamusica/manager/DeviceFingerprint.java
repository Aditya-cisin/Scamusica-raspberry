package com.musicplayer.scamusica.manager;

import com.musicplayer.scamusica.util.DeviceUtil;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public class DeviceFingerprint {
    public static String getFingerprint() {
        try {
            String mac = DeviceUtil.getDeviceId();
            String os = System.getProperty("os.name");
            String cpu = System.getenv("PROCESSOR_IDENTIFIER"); // Windows
            if (cpu == null) cpu = System.getenv("HOSTTYPE");   // Linux / Mac
            if (cpu == null) cpu = System.getenv("MACHTYPE");   //Linux / Mac
            if (cpu == null) cpu = "UNKNOWN_CPU"; // Fallback

            String raw = mac + os + cpu;
            // Hash everything into a unique fingerprint
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "UNKNOWN";
        }
    }
}
