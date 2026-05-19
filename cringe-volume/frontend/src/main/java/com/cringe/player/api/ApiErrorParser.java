package com.cringe.player.api;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.net.http.HttpResponse;

/**
 * Разбирает унифицированный JSON-ответ бэкенда:
 * <pre>
 * { "error": true, "code": "AUDIO_003", "status": 400,
 *   "message": "...", "details": "..." }
 * </pre>
 * Бросает {@link ApiException}.
 */
final class ApiErrorParser {

    private static final Gson GSON = new Gson();

    private ApiErrorParser() {}

    static void throwAsApiException(HttpResponse<String> response, String backendUrl) {
        int status = response.statusCode();
        String body = response.body();

        String code = "HTTP_" + status;
        String message = "Сервер вернул " + status;
        String details = null;

        try {
            JsonObject err = GSON.fromJson(body, JsonObject.class);
            if (err != null) {
                if (err.has("code"))    code    = err.get("code").getAsString();
                if (err.has("message")) message = err.get("message").getAsString();
                if (err.has("details") && !err.get("details").isJsonNull()) {
                    details = err.get("details").getAsString();
                }
            }
        } catch (Exception ignore) {
            // body — не JSON; используем дефолты + кладём в details сырое тело
            if (body != null && !body.isBlank()) {
                details = body.length() > 200 ? body.substring(0, 200) + "…" : body;
            }
        }

        if (message == null || message.isBlank()) {
            message = "Сервер " + backendUrl + " вернул " + status;
        }
        throw new ApiException(code, message, details, status);
    }
}
