package com.cringe.volume.exception;

import com.cringe.volume.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /* ====== Доменные ошибки ====== */

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ErrorResponse> handleApp(AppException e) {
        ErrorCode code = e.getCode();
        // 5xx — error, 4xx — warn (это ожидаемые клиентские ошибки)
        if (code.getStatus().is5xxServerError()) {
            log.error("{}: {} (details: {})", code.getCode(), code.getMessage(), e.getDetails(), e);
        } else {
            log.warn("{}: {} (details: {})", code.getCode(), code.getMessage(), e.getDetails());
        }
        return ResponseEntity.status(code.getStatus())
                .body(new ErrorResponse(code, e.getDetails()));
    }

    /* ====== Системные / Spring ошибки → маппим на ErrorCode ====== */

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String details = e.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse(null);
        log.warn("{}: {}", ErrorCode.VALIDATION_FAILED.getCode(), details);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(new ErrorResponse(ErrorCode.VALIDATION_FAILED, details));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxSize(MaxUploadSizeExceededException e) {
        log.warn("{}: {}", ErrorCode.AUDIO_FILE_TOO_LARGE.getCode(), e.getMessage());
        return ResponseEntity.status(ErrorCode.AUDIO_FILE_TOO_LARGE.getStatus())
                .body(new ErrorResponse(ErrorCode.AUDIO_FILE_TOO_LARGE));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ErrorResponse> handleSecurity(SecurityException e) {
        log.warn("{}: {}", ErrorCode.PAYMENT_SIGNATURE_INVALID.getCode(), e.getMessage());
        return ResponseEntity.status(ErrorCode.PAYMENT_SIGNATURE_INVALID.getStatus())
                .body(new ErrorResponse(ErrorCode.PAYMENT_SIGNATURE_INVALID, e.getMessage()));
    }

    /* ====== Legacy mapping для совместимости ====== */

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        log.warn("{}: {}", ErrorCode.VALIDATION_FAILED.getCode(), e.getMessage());
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.getStatus())
                .body(new ErrorResponse(ErrorCode.VALIDATION_FAILED, e.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        log.warn("{}: {}", ErrorCode.PAYMENT_INVALID_STATE.getCode(), e.getMessage());
        return ResponseEntity.status(ErrorCode.PAYMENT_INVALID_STATE.getStatus())
                .body(new ErrorResponse(ErrorCode.PAYMENT_INVALID_STATE, e.getMessage()));
    }

    /* ====== Последний резерв ====== */

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        log.error("{}: необработанное исключение", ErrorCode.SERVER_INTERNAL.getCode(), e);
        String details = e.getClass().getSimpleName()
                + (e.getMessage() != null ? ": " + e.getMessage() : "");
        return ResponseEntity.status(ErrorCode.SERVER_INTERNAL.getStatus())
                .body(new ErrorResponse(ErrorCode.SERVER_INTERNAL, details));
    }
}
