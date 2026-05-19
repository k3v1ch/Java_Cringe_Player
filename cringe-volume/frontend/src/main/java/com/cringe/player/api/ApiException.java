package com.cringe.player.api;

/**
 * Разобранная ошибка от бэкенда. Содержит code/message/details
 * — для показа пользователю с понятным текстом.
 */
public class ApiException extends RuntimeException {

    private final String code;
    private final String details;
    private final int httpStatus;

    public ApiException(String code, String message, String details, int httpStatus) {
        super(message);
        this.code = code;
        this.details = details;
        this.httpStatus = httpStatus;
    }

    public ApiException(String code, String message, String details, int httpStatus,
                        Throwable cause) {
        super(message, cause);
        this.code = code;
        this.details = details;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public String getDetails() { return details; }
    public int getHttpStatus() { return httpStatus; }

    /** Готовое к показу сообщение: "Ошибка CODE_001: текст\n(details)" */
    public String toUserMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("Ошибка ").append(code).append(": ").append(getMessage());
        if (details != null && !details.isBlank()) {
            sb.append("\n").append(details);
        }
        return sb.toString();
    }
}
