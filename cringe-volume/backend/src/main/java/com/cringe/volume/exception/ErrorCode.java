package com.cringe.volume.exception;

import org.springframework.http.HttpStatus;

/**
 * Все коды ошибок проекта. Каждый — стабильный идентификатор + читаемое сообщение
 * + HTTP-статус. Используется в {@link AppException} и {@link GlobalExceptionHandler}.
 */
public enum ErrorCode {

    /* ===== AUDIO ===== */
    AUDIO_FILE_NOT_FOUND      ("AUDIO_001", HttpStatus.NOT_FOUND,
            "Аудиофайл не найден"),
    AUDIO_FILE_EMPTY          ("AUDIO_002", HttpStatus.BAD_REQUEST,
            "Загружен пустой файл"),
    AUDIO_FORMAT_UNSUPPORTED  ("AUDIO_003", HttpStatus.BAD_REQUEST,
            "Формат файла не поддерживается (только mp3, wav)"),
    AUDIO_UPLOAD_FAILED       ("AUDIO_004", HttpStatus.INTERNAL_SERVER_ERROR,
            "Не удалось сохранить аудиофайл"),
    AUDIO_STREAM_FAILED       ("AUDIO_005", HttpStatus.INTERNAL_SERVER_ERROR,
            "Ошибка при стриминге аудиофайла"),
    AUDIO_DIR_NOT_FOUND       ("AUDIO_006", HttpStatus.INTERNAL_SERVER_ERROR,
            "Директория с музыкой не найдена или недоступна"),
    AUDIO_FILE_TOO_LARGE      ("AUDIO_007", HttpStatus.PAYLOAD_TOO_LARGE,
            "Файл слишком большой (максимум 50 МБ)"),
    AUDIO_PATH_INVALID        ("AUDIO_008", HttpStatus.BAD_REQUEST,
            "Недопустимое имя файла (path traversal)"),

    /* ===== VOLUME ===== */
    VOLUME_INVALID            ("VOLUME_001", HttpStatus.BAD_REQUEST,
            "Громкость должна быть от 0 до 100"),
    VOLUME_NOT_CHANGED        ("VOLUME_002", HttpStatus.CONFLICT,
            "Громкость не изменена — платёж не подтверждён"),

    /* ===== PAYMENT ===== */
    PAYMENT_NOT_FOUND         ("PAY_001", HttpStatus.NOT_FOUND,
            "Платёж не найден"),
    PAYMENT_EXPIRED           ("PAY_002", HttpStatus.CONFLICT,
            "Время платежа истекло"),
    PAYMENT_ALREADY_PAID      ("PAY_003", HttpStatus.CONFLICT,
            "Платёж уже оплачен"),
    PAYMENT_SIGNATURE_INVALID ("PAY_004", HttpStatus.FORBIDDEN,
            "Подпись вебхука невалидна"),
    PAYMENT_WEBHOOK_FAILED    ("PAY_005", HttpStatus.INTERNAL_SERVER_ERROR,
            "Ошибка обработки вебхука"),
    PAYMENT_CREATION_FAILED   ("PAY_006", HttpStatus.INTERNAL_SERVER_ERROR,
            "Не удалось создать платёж"),
    PAYMENT_INVALID_STATE     ("PAY_007", HttpStatus.CONFLICT,
            "Платёж в недопустимом статусе"),
    PAYMENT_SECRET_MISSING    ("PAY_008", HttpStatus.INTERNAL_SERVER_ERROR,
            "Webhook-секрет не задан (ROLLYPAY_SIGNING_SECRET)"),

    /* ===== EMAIL ===== */
    EMAIL_REQUIRED            ("EMAIL_001", HttpStatus.BAD_REQUEST,
            "Email обязателен для получения чека"),
    EMAIL_INVALID             ("EMAIL_002", HttpStatus.BAD_REQUEST,
            "Некорректный формат email"),
    EMAIL_SEND_FAILED         ("EMAIL_003", HttpStatus.INTERNAL_SERVER_ERROR,
            "Не удалось отправить чек на email"),

    /* ===== PLAYER ===== */
    PLAYER_NO_TRACK           ("PLAYER_001", HttpStatus.BAD_REQUEST,
            "Трек не выбран"),
    PLAYER_ALREADY_PLAYING    ("PLAYER_002", HttpStatus.CONFLICT,
            "Воспроизведение уже запущено"),
    PLAYER_NOT_PLAYING        ("PLAYER_003", HttpStatus.CONFLICT,
            "Воспроизведение не запущено"),

    /* ===== GENERIC ===== */
    VALIDATION_FAILED         ("VAL_001", HttpStatus.BAD_REQUEST,
            "Ошибка валидации запроса"),
    SERVER_INTERNAL           ("SRV_001", HttpStatus.INTERNAL_SERVER_ERROR,
            "Внутренняя ошибка сервера");

    private final String code;
    private final HttpStatus status;
    private final String message;

    ErrorCode(String code, HttpStatus status, String message) {
        this.code = code;
        this.status = status;
        this.message = message;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
    public String getMessage() { return message; }
}
