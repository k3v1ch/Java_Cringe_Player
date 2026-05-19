package com.cringe.player.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * HTTP-клиент для аудио-API. URL читается из {@link ApiConfig}.
 * Платёжные методы — см. {@link PaymentApiClient}.
 */
public class AudioApiClient {

    private final HttpClient client;
    private final Gson gson = new Gson();

    public AudioApiClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String uploadFile(Path filePath) throws IOException, InterruptedException {
        if (filePath == null) {
            throw new IOException("Файл не выбран");
        }
        if (!java.nio.file.Files.exists(filePath)) {
            throw new IOException("Файл не существует: " + filePath);
        }

        String boundary = UUID.randomUUID().toString();
        byte[] fileBytes = java.nio.file.Files.readAllBytes(filePath);
        String fileName = filePath.getFileName().toString();
        String lowerName = fileName.toLowerCase();

        String mimeType = lowerName.endsWith(".wav") ? "audio/wav" : "audio/mpeg";

        byte[] body = buildMultipartBody(boundary, fileName, mimeType, fileBytes);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.BASE_URL + "/upload"))
                .timeout(Duration.ofMinutes(2))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();

        HttpResponse<String> response = send(request);
        checkResponse(response);

        JsonObject json = gson.fromJson(response.body(), JsonObject.class);
        return json.get("fileName").getAsString();
    }

    public void play() throws IOException, InterruptedException {
        post("/play", null);
    }

    public void stop() throws IOException, InterruptedException {
        post("/stop", null);
    }

    public void setVolume(int volume) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("volume", volume);
        post("/volume", body.toString());
    }

    public JsonObject getState() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.BASE_URL + "/state"))
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        checkResponse(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    /* ========== Tracks API ========== */

    /**
     * GET /api/audio/tracks — список треков на сервере.
     * Возвращает List имён файлов.
     */
    public List<String> listTracks() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.BASE_URL + "/tracks"))
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        checkResponse(response);

        JsonArray arr = gson.fromJson(response.body(), JsonArray.class);
        List<String> tracks = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) {
            tracks.add(arr.get(i).getAsJsonObject().get("filename").getAsString());
        }
        return tracks;
    }

    /**
     * URL для стриминга трека — используется для MediaPlayer.
     * Имя файла URL-кодируется (для кириллицы / пробелов).
     */
    public String getStreamUrl(String filename) {
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ApiConfig.BASE_URL + "/tracks/" + encoded + "/stream";
    }

    /* ========== helpers ========== */

    private void post(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.BASE_URL + path));

        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = send(builder.build());
        checkResponse(response);
    }

    private HttpResponse<String> send(HttpRequest req) throws IOException, InterruptedException {
        try {
            return client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException ce) {
            throw new ApiException(
                    "SRV_001",
                    "Сервер недоступен: " + ApiConfig.getBackendUrl(),
                    "источник URL: " + ApiConfig.getSource(),
                    0, ce);
        }
    }

    /** Парсит {error,code,message,details} → бросает ApiException. */
    private void checkResponse(HttpResponse<String> response) {
        if (response.statusCode() < 400) return;
        ApiErrorParser.throwAsApiException(response, ApiConfig.getBackendUrl());
    }

    private byte[] buildMultipartBody(String boundary, String fileName,
                                      String mimeType, byte[] fileBytes) {
        String CRLF = "\r\n";
        StringBuilder header = new StringBuilder();
        header.append("--").append(boundary).append(CRLF);
        header.append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(fileName).append("\"").append(CRLF);
        header.append("Content-Type: ").append(mimeType).append(CRLF);
        header.append(CRLF);

        byte[] headerBytes = header.toString().getBytes(StandardCharsets.UTF_8);
        byte[] footerBytes = (CRLF + "--" + boundary + "--" + CRLF)
                .getBytes(StandardCharsets.UTF_8);

        byte[] result = new byte[headerBytes.length + fileBytes.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, result, 0, headerBytes.length);
        System.arraycopy(fileBytes, 0, result, headerBytes.length, fileBytes.length);
        System.arraycopy(footerBytes, 0, result, headerBytes.length + fileBytes.length,
                footerBytes.length);
        return result;
    }
}
