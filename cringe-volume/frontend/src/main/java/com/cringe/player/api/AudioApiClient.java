package com.cringe.player.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
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

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException ce) {
            throw new IOException("Не удалось подключиться к серверу " + ApiConfig.BASE_URL
                    + ". Проверьте, что бэкенд запущен и backend.url задан верно.", ce);
        }
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

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        checkResponse(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    /* ========== Tracks API ========== */

    /**
     * GET /api/audio/tracks — список треков на сервере.
     * Возвращает List строк (имена файлов).
     */
    public List<String> listTracks() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.BASE_URL + "/tracks"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
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
        // URLEncoder заменяет пробел на '+', что неверно в path-сегменте.
        // Делаем правильное path-encoding: '+' → '%20'.
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ApiConfig.BASE_URL + "/tracks/" + encoded + "/stream";
    }

    /* ========== Payments API ========== */

    public JsonObject createPayment(int targetVolume) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("targetVolume", targetVolume);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.PAYMENTS_URL + "/create"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        checkResponse(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    public JsonObject getPaymentStatus(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.PAYMENTS_URL + "/" + token + "/status"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());
        checkResponse(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    private void post(String path, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.BASE_URL + path));

        if (jsonBody != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = client.send(builder.build(),
                HttpResponse.BodyHandlers.ofString());
        checkResponse(response);
    }

    private void checkResponse(HttpResponse<String> response) throws IOException {
        if (response.statusCode() >= 400) {
            String msg;
            try {
                JsonObject err = gson.fromJson(response.body(), JsonObject.class);
                msg = err != null && err.has("message")
                        ? err.get("message").getAsString()
                        : response.body();
            } catch (Exception e) {
                msg = response.body();
            }
            if (msg == null || msg.isBlank()) {
                msg = "пустой ответ (status " + response.statusCode() + ")";
            }
            throw new IOException("Сервер вернул " + response.statusCode() + ": " + msg);
        }
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
