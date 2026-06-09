package com.musicplayer.scamusica.util;

import com.musicplayer.scamusica.model.PlaylistTrack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PlaybackHistoryLogger {

    private static final String BASE_DIR =
            System.getProperty("user.home")
                    + File.separator
                    + ".scamusica";

    private static final String LOG_FILE =
            BASE_DIR + File.separator + "playback-history.log";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a");

    static {
        try {

            File dir = new File(BASE_DIR);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(LOG_FILE);

            if (!file.exists()) {
                file.createNewFile();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static final long MAX_LOG_SIZE = 5 * 1024 * 1024; // 5 MB

    public static synchronized void logSong(PlaylistTrack track) {

        try {
            // Cap the log file size — if over 5MB, keep only the last ~2MB
            File logFile = new File(LOG_FILE);
            if (logFile.exists() && logFile.length() > MAX_LOG_SIZE) {
                try {
                    byte[] allBytes = java.nio.file.Files.readAllBytes(logFile.toPath());
                    int keepFrom = allBytes.length - (2 * 1024 * 1024);
                    if (keepFrom > 0) {
                        byte[] trimmed = java.util.Arrays.copyOfRange(allBytes, keepFrom, allBytes.length);
                        java.nio.file.Files.write(logFile.toPath(), trimmed);
                        AppLogger.log("[HISTORY] Log file trimmed from " + allBytes.length + " to " + trimmed.length + " bytes");
                    }
                } catch (Exception trimEx) {
                    AppLogger.log("[HISTORY] Failed to trim log: " + trimEx.getMessage());
                }
            }
        } catch (Exception ignored) {}

        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(LOG_FILE, true))) {

            String time = LocalDateTime.now().format(FORMATTER);

            String log =
                    "\n==================================================\n" +
                            "TIME       : " + time + "\n" +
                            "SONG ID    : " + track.getId() + "\n" +
                            "TITLE      : " + track.getTitle() + "\n" +
                            "PLAYLIST   : " + track.getFolderTitle() + "\n" +
                            "URL        : " + track.getUrl() + "\n" +
                            "==================================================\n";

            writer.write(log);

            AppLogger.log("[HISTORY] Logged -> " + track.getTitle());

        } catch (Exception e) {

            AppLogger.log("[HISTORY ERROR] " + e.getMessage());

            e.printStackTrace();
        }
    }
}