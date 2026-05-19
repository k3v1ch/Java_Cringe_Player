package com.cringe.player.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP-клиент для платежей. Использует URL из {@link ApiConfig}.
 */
public class PaymentApiClient {

    private final HttpClient client;
    private final Gson gson = new Gson();

    public PaymentApiClient() {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /** POST /api/payments/create */
    public JsonObject createPayment(int targetVolume) throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("targetVolume", targetVolume);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.PAYMENTS_URL + "/create"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(20))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = send(request);
        checkResponse(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    /** GET /api/payments/{token}/status */
    public JsonObject getPaymentStatus(String token) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ApiConfig.PAYMENTS_URL + "/" + token + "/status"))
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = send(request);
        checkResponse(response);
        return gson.fromJson(response.body(), JsonObject.class);
    }

    /* ---------- helpers ---------- */

    private HttpResponse<String> send(HttpRequest req) throws IOException, InterruptedException {
        try {
            return client.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (ConnectException ce) {
            throw new IOException("Не удалось подключиться к " + ApiConfig.getBackendUrl()
                    + " (источник URL: " + ApiConfig.getSource() + "). "
                    + "Проверьте PUBLIC_BACKEND_URL.", ce);
        }
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
}
