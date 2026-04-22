package com.musicplayer.scamusica.util;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class ApiClient {

    private static final int TIMEOUT = 15000;
    private static final int BUFFER_SIZE = 8192;

    public interface ProgressCallback {
        void onProgress(long bytesRead, long contentLength);
    }

    public static String get(String urlString, Map<String, String> headers) throws Exception {
        System.out.println("[ApiClient][GET] URL = " + urlString);
        HttpsURLConnection connection = createConnection(urlString, "GET", headers);
        return getResponse(connection);
    }

    public static String post(String urlString, String jsonBody, Map<String, String> headers) throws Exception {
        System.out.println("[ApiClient][POST] URL = " + urlString);
        System.out.println("[ApiClient][POST] Body = " + jsonBody);
        HttpsURLConnection connection = createConnection(urlString, "POST", headers);
        writeBody(connection, jsonBody);
        return getResponse(connection);
    }

    public static String put(String urlString, String jsonBody, Map<String, String> headers) throws Exception {
        System.out.println("[ApiClient][PUT] URL = " + urlString);
        System.out.println("[ApiClient][PUT] Body = " + jsonBody);
        HttpsURLConnection connection = createConnection(urlString, "PUT", headers);
        writeBody(connection, jsonBody);
        return getResponse(connection);
    }

    public static String delete(String urlString, Map<String, String> headers) throws Exception {
        System.out.println("[ApiClient][DELETE] URL = " + urlString);
        HttpsURLConnection connection = createConnection(urlString, "DELETE", headers);
        return getResponse(connection);
    }

    private static HttpsURLConnection createConnection(String urlString,
                                                       String method,
                                                       Map<String, String> headers) throws Exception {
        URL url = new URL(urlString);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();

        connection.setReadTimeout(TIMEOUT);
        connection.setConnectTimeout(TIMEOUT);
        connection.setRequestMethod(method);
        connection.setDoInput(true);

        if ("POST".equals(method) || "PUT".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
        }

        if (headers != null) {
            for (String key : headers.keySet()) {
                connection.setRequestProperty(key, headers.get(key));
            }
        }

        System.out.println("[ApiClient] Method = " + method);
        System.out.println("[ApiClient] Headers = " + headers);

        return connection;
    }


    private static String getResponse(HttpsURLConnection connection) throws Exception {
        int status = connection.getResponseCode();
        System.out.println("[ApiClient] HTTP Status = " + status);

        InputStream is = (status < HttpsURLConnection.HTTP_BAD_REQUEST)
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (is == null) {
            System.out.println("[ApiClient] Response stream is NULL");
            connection.disconnect();
            return "";
        }

        BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = br.readLine()) != null) {
            response.append(line);
        }

        br.close();
        connection.disconnect();

        System.out.println("[ApiClient] Raw Response = " + response);
        return response.toString();
    }

    public static boolean downloadToFile(String urlString,
                                         Map<String, String> headers,
                                         File outputFile,
                                         ProgressCallback progressCallback) throws Exception {

        System.out.println("[ApiClient][DOWNLOAD] URL = " + urlString);
        System.out.println("[ApiClient][DOWNLOAD] Output = " + outputFile.getAbsolutePath());

        HttpsURLConnection connection = createConnection(urlString, "GET", headers);
        connection.setDoInput(true);
        connection.setConnectTimeout(TIMEOUT);
        connection.setReadTimeout(TIMEOUT);

        int status = connection.getResponseCode();
        System.out.println("[ApiClient][DOWNLOAD] HTTP Status = " + status);

        if (status >= HttpsURLConnection.HTTP_BAD_REQUEST) {
            InputStream err = connection.getErrorStream();
            if (err != null) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(err))) {
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    System.out.println("[ApiClient][DOWNLOAD] Error Response = " + sb);
                }
            }
            connection.disconnect();
            return false;
        }

        long contentLength = connection.getContentLengthLong();
        System.out.println("[ApiClient][DOWNLOAD] Content-Length = " + contentLength);

        InputStream is = connection.getInputStream();

        try (BufferedInputStream in = new BufferedInputStream(is);
             FileOutputStream fos = new FileOutputStream(outputFile);
             BufferedOutputStream bout = new BufferedOutputStream(fos, BUFFER_SIZE)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalRead = 0L;

            while ((bytesRead = in.read(buffer)) != -1) {
                bout.write(buffer, 0, bytesRead);
                totalRead += bytesRead;

                if (progressCallback != null) {
                    try {
                        progressCallback.onProgress(totalRead, contentLength);
                    } catch (Exception ignored) {
                    }
                }
            }
            bout.flush();
        } finally {
            connection.disconnect();
        }

        System.out.println("[ApiClient][DOWNLOAD] Completed successfully");
        return true;
    }

    private static void writeBody(HttpsURLConnection connection, String jsonBody) throws IOException {
        if (jsonBody != null && !jsonBody.isEmpty()) {
            System.out.println("[ApiClient] Writing request body");
            try (OutputStream os = connection.getOutputStream()) {
                os.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } else {
            System.out.println("[ApiClient] No request body to write");
        }
    }

}
