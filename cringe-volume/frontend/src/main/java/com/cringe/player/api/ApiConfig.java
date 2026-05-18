package com.cringe.player.api;

public final class ApiConfig {

    private static final String DEFAULT_BACKEND = "http://localhost:8080";

    /**
     * Берёт URL бэкенда из системного свойства / env-переменной,
     * иначе localhost:8080.
     */
    private static final String BACKEND_URL = resolve();

    public static final String BASE_URL      = BACKEND_URL + "/api/audio";
    public static final String PAYMENTS_URL  = BACKEND_URL + "/api/payments";
    public static final String PAY_PAGE_URL  = BACKEND_URL + "/pay";

    private ApiConfig() {}

    private static String resolve() {
        String url = System.getProperty("backend.url");
        if (url == null || url.isBlank()) {
            url = System.getenv("PUBLIC_BACKEND_URL");
        }
        if (url == null || url.isBlank()) {
            url = DEFAULT_BACKEND;
        }
        // убрать trailing slash
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
