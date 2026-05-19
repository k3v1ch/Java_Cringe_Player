package com.cringe.player.api;

/**
 * Конфигурация URL бэкенда. Порядок приоритетов:
 *   1) -DPUBLIC_BACKEND_URL=https://...     (system property, передаётся jpackage)
 *   2) -Dbackend.url=https://...            (system property, legacy)
 *   3) PUBLIC_BACKEND_URL=https://...       (environment variable)
 *   4) DEFAULT_BACKEND                      (дефолт)
 */
public final class ApiConfig {

    private static final String DEFAULT_BACKEND = "https://pay.vernovpn.com";

    // Один resolve, возвращает [url, source]. Это избегает forward-reference
    // ошибки между двумя финальными статическими полями.
    private static final String[] RESOLVED = resolve();

    public static final String BACKEND_URL = RESOLVED[0];
    public static final String SOURCE      = RESOLVED[1];

    public static final String BASE_URL     = BACKEND_URL + "/api/audio";
    public static final String PAYMENTS_URL = BACKEND_URL + "/api/payments";
    public static final String PAY_PAGE_URL = BACKEND_URL + "/pay";

    static {
        System.out.println("[ApiConfig] Backend URL: " + BACKEND_URL
                + "  (source: " + SOURCE + ")");
    }

    private ApiConfig() {}

    /** Полный URL бэкенда без trailing slash. */
    public static String getBackendUrl() {
        return BACKEND_URL;
    }

    /** Источник, из которого получен URL (для логов/ошибок). */
    public static String getSource() {
        return SOURCE;
    }

    private static String[] resolve() {
        String url;

        // 1) системное свойство PUBLIC_BACKEND_URL (для jpackage --java-options)
        url = System.getProperty("PUBLIC_BACKEND_URL");
        if (notBlank(url)) return new String[] { strip(url), "-DPUBLIC_BACKEND_URL" };

        // 2) системное свойство backend.url (legacy)
        url = System.getProperty("backend.url");
        if (notBlank(url)) return new String[] { strip(url), "-Dbackend.url" };

        // 3) переменная окружения
        url = System.getenv("PUBLIC_BACKEND_URL");
        if (notBlank(url)) return new String[] { strip(url), "env PUBLIC_BACKEND_URL" };

        // 4) дефолт
        return new String[] { DEFAULT_BACKEND, "default" };
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String strip(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
