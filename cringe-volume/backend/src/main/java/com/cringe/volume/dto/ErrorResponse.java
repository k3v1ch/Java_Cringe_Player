package com.cringe.volume.dto;

import com.cringe.volume.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Унифицированный формат ошибки для всех REST-ответов.
 *
 * <pre>
 * {
 *   "error": true,
 *   "code": "AUDIO_003",
 *   "status": 400,
 *   "message": "Формат файла не поддерживается (только mp3, wav)",
 *   "details": "uploaded: track.ogg"
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private final boolean error = true;
    private final String code;
    private final int status;
    private final String message;
    private final String details;

    public ErrorResponse(ErrorCode errorCode) {
        this(errorCode, null);
    }

    public ErrorResponse(ErrorCode errorCode, String details) {
        this.code = errorCode.getCode();
        this.status = errorCode.getStatus().value();
        this.message = errorCode.getMessage();
        this.details = details;
    }

    /** Legacy-конструктор — для случаев когда ErrorCode ещё не подобран. */
    public ErrorResponse(int status, String message) {
        this.code = "SRV_001";
        this.status = status;
        this.message = message;
        this.details = null;
    }

    public boolean isError() { return error; }
    public String getCode() { return code; }
    public int getStatus() { return status; }
    public String getMessage() { return message; }
    public String getDetails() { return details; }
}
