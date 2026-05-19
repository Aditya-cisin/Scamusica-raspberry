package com.musicplayer.scamusica.service;

import com.google.gson.*;
import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.model.PlaylistTrack;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.OfflineCache;
import com.musicplayer.scamusica.util.Utility;

import java.util.*;

public class PlaylistApiService {

    private static final String SONGS_URL =
            Utility.BASE_URL.get() + Utility.API_SONGS_ENDPOINT.get();

    private JsonObject fetchRootJson() throws Exception {
        String token = SessionManager.loadToken();

        if (token == null || token.trim().isEmpty()) {
            System.err.println("[PlaylistApiService] Token is null or empty");
            throw new IllegalStateException("Bearer token is missing");
        }

        System.out.println("[PlaylistApiService] Using token: " + token);

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Accept", "application/json");

        String response = ApiClient.get(SONGS_URL, headers);
        System.out.println("[PlaylistApiService] Raw response : " + response);

        if (response == null || response.isEmpty()) {
            throw new IllegalStateException("Empty response from API");
        }

        return JsonParser.parseString(response).getAsJsonObject();
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHANGE 1 — fetchPlaylistTitles: success pe cache save, fail pe cache load
    // ════════════════════════════════════════════════════════════════════════
    public List<String> fetchPlaylistTitles() throws Exception {
        try {
            JsonObject root = fetchRootJson();
            List<String> titles = new ArrayList<>();

            if (!root.has("data") || root.get("data").isJsonNull() || !root.get("data").isJsonObject()) {
                return titles;
            }

            JsonObject dataObj = root.getAsJsonObject("data");

            if (!dataObj.has("sequences") || !dataObj.get("sequences").isJsonArray()) {
                return titles;
            }

            JsonArray sequences = dataObj.getAsJsonArray("sequences");

            for (JsonElement seqEl : sequences) {
                if (!seqEl.isJsonObject()) continue;
                JsonObject seqObj = seqEl.getAsJsonObject();
                if (seqObj.has("title") && !seqObj.get("title").isJsonNull()) {
                    String seqTitle = seqObj.get("title").getAsString();
                    if (seqTitle != null && !seqTitle.trim().isEmpty()) {
                        titles.add(seqTitle);
                    }
                }
            }

            System.out.println("[PlaylistApiService] Playlists from API: " + titles);

            // ✅ SUCCESS — cache mein save karo
            if (!titles.isEmpty()) {
                OfflineCache.savePlaylistTitles(titles);
            }

            return titles;

        } catch (Exception e) {
            // ❌ FAIL — cache se load karo
            AppLogger.log("[PlaylistApiService] fetchPlaylistTitles failed, loading from cache: " + e.getMessage());
            List<String> cached = OfflineCache.loadPlaylistTitles();
            if (!cached.isEmpty()) {
                AppLogger.log("[PlaylistApiService] Using cached titles: " + cached.size());
                return cached;
            }
            throw e; // cache bhi nahi hai toh exception do
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHANGE 2 — fetchTracksForGenre: success pe cache save, fail pe cache load
    // ════════════════════════════════════════════════════════════════════════
    public List<PlaylistTrack> fetchTracksForGenre(String genreTitle) throws Exception {
        try {
            List<PlaylistTrack> result = new ArrayList<>();

            JsonObject root = fetchRootJson();

            if (!root.has("data") || root.get("data").isJsonNull() || !root.get("data").isJsonObject()) {
                System.out.println("[PlaylistApiService] 'data' field missing or not object in response");
                return result;
            }

            JsonObject dataObj = root.getAsJsonObject("data");

            if (!dataObj.has("sequences") || !dataObj.get("sequences").isJsonArray()) {
                System.out.println("[PlaylistApiService] 'sequences' missing or not array");
                return result;
            }

            JsonArray sequences = dataObj.getAsJsonArray("sequences");

            String commonPath = null;
            if (root.has("filePath") && !root.get("filePath").isJsonNull()) {
                commonPath = root.get("filePath").getAsString();
            }

            for (JsonElement seqEl : sequences) {
                if (!seqEl.isJsonObject()) continue;
                JsonObject seqObj = seqEl.getAsJsonObject();

                if (!seqObj.has("title") || seqObj.get("title").isJsonNull()) continue;
                String seqTitle = seqObj.get("title").getAsString();
                if (!genreTitle.equals(seqTitle)) continue;

                if (!seqObj.has("styles") || !seqObj.get("styles").isJsonArray()) continue;
                JsonArray styles = seqObj.getAsJsonArray("styles");

                for (JsonElement styleEl : styles) {
                    if (!styleEl.isJsonObject()) continue;
                    JsonObject styleObj = styleEl.getAsJsonObject();

                    String folderTitle = null;
                    if (styleObj.has("title") && !styleObj.get("title").isJsonNull()) {
                        folderTitle = styleObj.get("title").getAsString();
                    }

                    if (!styleObj.has("songs") || !styleObj.get("songs").isJsonArray()) continue;
                    JsonArray songsArr = styleObj.getAsJsonArray("songs");

                    List<PlaylistTrack> folderTracks = new ArrayList<>();
                    for (JsonElement songEl : songsArr) {
                        if (!songEl.isJsonObject()) continue;
                        PlaylistTrack track = parseSongToTrack(songEl.getAsJsonObject(), commonPath, folderTitle);
                        if (track != null) {
                            folderTracks.add(track);
                        }
                    }

                    Collections.shuffle(folderTracks);
                    result.addAll(folderTracks);
                }
                break;
            }

            System.out.println("[PlaylistApiService] Final Tracks with folder titles → " + result);

            // ✅ SUCCESS — cache mein save karo
            if (!result.isEmpty()) {
                OfflineCache.saveTracks(genreTitle, result);
            }

            return result;

        } catch (Exception e) {
            // ❌ FAIL — cache se load karo
            AppLogger.log("[PlaylistApiService] fetchTracksForGenre failed, loading from cache: " + e.getMessage());
            List<PlaylistTrack> cached = OfflineCache.loadTracks(genreTitle);
            if (!cached.isEmpty()) {
                AppLogger.log("[PlaylistApiService] Using cached tracks for: " + genreTitle);
                return cached;
            }
            return new ArrayList<>(); // empty return karo, crash mat karo
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // CHANGE 3 — fetchDownloadSequenceForGenre: success pe cache save, fail pe cache load
    // ════════════════════════════════════════════════════════════════════════
    public List<Integer> fetchDownloadSequenceForGenre(String genreTitle) throws Exception {
        try {
            List<Integer> downloadSequence = new ArrayList<>();

            JsonObject root = fetchRootJson();

            if (!root.has("data") || root.get("data").isJsonNull() || !root.get("data").isJsonObject()) {
                return downloadSequence;
            }

            JsonObject dataObj = root.getAsJsonObject("data");

            if (!dataObj.has("sequences") || !dataObj.get("sequences").isJsonArray()) {
                return downloadSequence;
            }

            JsonArray sequences = dataObj.getAsJsonArray("sequences");

            String commonPath = null;
            if (root.has("filePath") && !root.get("filePath").isJsonNull()) {
                commonPath = root.get("filePath").getAsString();
            }

            Set<Integer> seenIds = new HashSet<>();

            for (JsonElement seqEl : sequences) {
                if (!seqEl.isJsonObject()) continue;
                JsonObject seqObj = seqEl.getAsJsonObject();

                if (!seqObj.has("title") || seqObj.get("title").isJsonNull()) continue;
                String seqTitle = seqObj.get("title").getAsString();
                if (!genreTitle.equals(seqTitle)) continue;

                if (!seqObj.has("styles") || !seqObj.get("styles").isJsonArray()) continue;
                JsonArray styles = seqObj.getAsJsonArray("styles");

                for (JsonElement styleEl : styles) {
                    if (!styleEl.isJsonObject()) continue;
                    JsonObject styleObj = styleEl.getAsJsonObject();

                    if (!styleObj.has("songs") || !styleObj.get("songs").isJsonArray()) continue;
                    JsonArray songsArr = styleObj.getAsJsonArray("songs");

                    List<PlaylistTrack> tracks = new ArrayList<>();
                    for (JsonElement songEl : songsArr) {
                        if (!songEl.isJsonObject()) continue;
                        PlaylistTrack track = parseSongToTrack(songEl.getAsJsonObject(), commonPath, null);
                        if (track != null && track.getId() != null && seenIds.add(track.getId())) {
                            tracks.add(track);
                        }
                    }

                    Collections.shuffle(tracks);
                    for (PlaylistTrack t : tracks) {
                        if (t.getId() != null) {
                            downloadSequence.add(t.getId());
                        }
                    }
                }
                break;
            }

            System.out.println("[PlaylistApiService] Download sequence for '" + genreTitle + "': " + downloadSequence);

            //  SUCCESS — cache mein save karo
            if (!downloadSequence.isEmpty()) {
                OfflineCache.saveDownloadSequence(genreTitle, downloadSequence);
            }

            return downloadSequence;

        } catch (Exception e) {
            // ❌ FAIL — cache se load karo
            AppLogger.log("[PlaylistApiService] fetchDownloadSequence failed, loading from cache: " + e.getMessage());
            List<Integer> cached = OfflineCache.loadDownloadSequence(genreTitle);
            AppLogger.log("[PlaylistApiService] Using cached sequence: " + cached.size() + " items");
            return cached; // empty bhi theek hai
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // parseSongToTrack — BILKUL SAME, kuch nahi badla
    // ════════════════════════════════════════════════════════════════════════
    private PlaylistTrack parseSongToTrack(JsonObject songObj,
                                           String commonPath,
                                           String folderTitle) {
        if (!songObj.has("file")) {
            System.out.println("[PlaylistApiService] Song missing 'file' field, skipping.");
            return null;
        }

        Integer songId = null;
        if (songObj.has("id") && !songObj.get("id").isJsonNull()) {
            try {
                songId = songObj.get("id").getAsInt();
            } catch (Exception ignored) {
            }
        }

        String title;
        if (songObj.has("file") && !songObj.get("file").isJsonNull()) {
            String fileName = songObj.get("file").getAsString();
            title = fileName.endsWith(".mp3")
                    ? fileName.substring(0, fileName.length() - 4)
                    : fileName;
        } else if (songObj.has("title") && !songObj.get("title").isJsonNull()) {
            title = songObj.get("title").getAsString();
        } else {
            title = "Unknown Title";
        }

        String filePath;
        if (songObj.has("filePath") && !songObj.get("filePath").isJsonNull()) {
            filePath = songObj.get("filePath").getAsString();
        } else {
            String fileName = songObj.get("file").getAsString();
            if (commonPath != null) {
                if (!commonPath.endsWith("/")) {
                    commonPath = commonPath + "/";
                }
                filePath = commonPath + fileName;
            } else {
                filePath = "/public/music/" + fileName;
            }
        }

        String fullUrl;
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            fullUrl = filePath;
        } else {
            fullUrl = Utility.BASE_URL.get() + filePath;
        }

        int durationSeconds = 0;
        if (songObj.has("duration") && !songObj.get("duration").isJsonNull()) {
            try {
                String durationStr = songObj.get("duration").getAsString();
                String[] parts = durationStr.split(":");
                durationSeconds = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } catch (Exception ignored) {
            }
        }

        String albumImgPath = null;
        if (songObj.has("album_img") && !songObj.get("album_img").isJsonNull()) {
            albumImgPath = songObj.get("album_img").getAsString();
            if (albumImgPath.endsWith(".webp")) {
                albumImgPath = albumImgPath.substring(0, albumImgPath.length() - 5) + ".png";
            }
        }

        String fullAlbumImgUrl = null;
        if (albumImgPath != null && !albumImgPath.trim().isEmpty()) {
            if (albumImgPath.startsWith("http://") || albumImgPath.startsWith("https://")) {
                fullAlbumImgUrl = albumImgPath;
            } else {
                fullAlbumImgUrl = Utility.BASE_URL.get() + albumImgPath;
            }
        }

        return new PlaylistTrack(songId, title, fullUrl, durationSeconds, folderTitle, fullAlbumImgUrl);
    }
}