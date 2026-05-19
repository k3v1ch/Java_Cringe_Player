package com.cringe.volume.payment;

import com.cringe.volume.dto.MessageResponse;
import com.cringe.volume.exception.AppException;
import com.cringe.volume.exception.ErrorCode;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@Controller
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    /** Простая проверка формата email (RFC 5322 lite). */
    private static final Pattern EMAIL_RE =
            Pattern.compile("^[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}$");

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /* ========== REST API ========== */

    @PostMapping("/api/payments/create")
    @ResponseBody
    public ResponseEntity<CreatePaymentResponse> create(
            @Valid @RequestBody CreatePaymentRequest request) {
        Payment p = paymentService.createPayment(request.getTargetVolume());
        log.info("PAY: создан платёж token={}, targetVolume={}, total={}₽",
                p.getToken(), p.getTargetVolume(), p.getTotalAmount());
        return ResponseEntity.ok(
                new CreatePaymentResponse(p, paymentService.getPayBaseUrl())
        );
    }

    @GetMapping("/api/payments/{token}/status")
    @ResponseBody
    public ResponseEntity<PaymentStatusResponse> getStatus(@PathVariable String token) {
        Payment p = paymentService.getPayment(token);
        return ResponseEntity.ok(new PaymentStatusResponse(p));
    }

    /**
     * Обработка платежа. Email обязателен — иначе 400 EMAIL_REQUIRED / EMAIL_INVALID.
     */
    @PostMapping("/api/payments/{token}/process")
    @ResponseBody
    public ResponseEntity<MessageResponse> process(
            @PathVariable String token,
            @RequestBody(required = false) ProcessPaymentRequest request) {

        String email = (request != null) ? request.getEmail() : null;
        validateEmail(email);

        paymentService.processPayment(token, email.trim());
        log.info("PAY: платёж {} обработан, email={}", token, email);
        return ResponseEntity.ok(new MessageResponse("Платёж обработан"));
    }

    @PostMapping("/api/payments/webhook")
    @ResponseBody
    public ResponseEntity<Map<String, String>> webhook(
            @RequestHeader("X-Signature") String signature,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestBody String rawBody) {
        paymentService.handleWebhook(signature, timestamp, rawBody);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    /* ========== Mock-страница оплаты ========== */

    @GetMapping("/pay/{token}")
    public String paymentPage(@PathVariable String token, Model model) {
        Payment p = paymentService.getPayment(token);

        model.addAttribute("token", p.getToken());
        model.addAttribute("basePrice", p.getBasePrice());
        model.addAttribute("commission", p.getCommission());
        model.addAttribute("totalAmount", p.getTotalAmount());
        model.addAttribute("description", p.getDescription());
        model.addAttribute("expiresAtMs", p.getExpiresAt().toEpochMilli());
        model.addAttribute("status", p.getStatus().name().toLowerCase());

        return "pay";
    }

    /* ========== validation ========== */

    private void validateEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.EMAIL_REQUIRED);
        }
        if (!EMAIL_RE.matcher(email.trim()).matches()) {
            throw new AppException(ErrorCode.EMAIL_INVALID,
                    "получено: " + email);
        }
    }
}
