package com.cringe.volume.payment;

import com.cringe.volume.dto.ErrorResponse;
import com.cringe.volume.dto.MessageResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
public class PaymentController {

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

    @PostMapping("/api/payments/{token}/process")
    @ResponseBody
    public ResponseEntity<?> process(
            @PathVariable String token,
            @RequestBody(required = false) ProcessPaymentRequest request) {
        try {
            String email = (request != null) ? request.getEmail() : null;
            paymentService.processPayment(token, email);
            return ResponseEntity.ok(new MessageResponse("Платёж обработан"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse(409, e.getMessage()));
        }
    }

    @PostMapping("/api/payments/webhook")
    @ResponseBody
    public ResponseEntity<?> webhook(
            @RequestHeader("X-Signature") String signature,
            @RequestHeader("X-Timestamp") String timestamp,
            @RequestBody String rawBody) {
        try {
            paymentService.handleWebhook(signature, timestamp, rawBody);
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new ErrorResponse(403, e.getMessage()));
        }
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
}
