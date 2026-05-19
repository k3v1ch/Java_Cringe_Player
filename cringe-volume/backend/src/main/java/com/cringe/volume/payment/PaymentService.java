package com.cringe.volume.payment;

import com.cringe.volume.exception.AppException;
import com.cringe.volume.exception.ErrorCode;
import com.cringe.volume.mail.ReceiptMailService;
import com.cringe.volume.service.AudioService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentStore store;
    private final PaymentPricingService pricing;
    private final AudioService audioService;
    private final ReceiptMailService receiptMailService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Value("${payment.webhook-secret}")
    private String webhookSecret;

    @Value("${payment.pay-base-url}")
    private String payBaseUrl;

    @Value("${payment.mode}")
    private String paymentMode;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${payment.callback-internal-url:}")
    private String callbackInternalUrl;

    public PaymentService(PaymentStore store,
                          PaymentPricingService pricing,
                          AudioService audioService,
                          ReceiptMailService receiptMailService) {
        this.store = store;
        this.pricing = pricing;
        this.audioService = audioService;
        this.receiptMailService = receiptMailService;
    }

    @PostConstruct
    void logConfig() {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.error("⚠ {}: webhook-secret ПУСТОЙ. Установите ROLLYPAY_SIGNING_SECRET в .env",
                    ErrorCode.PAYMENT_SECRET_MISSING.getCode());
        } else {
            log.info("PAY: webhook-secret загружен ({} симв.)", webhookSecret.length());
        }
        log.info("PAY: mode = {}", paymentMode);
        log.info("PAY: callback-internal-url = {}", resolveSelfWebhookUrl());
        log.info("PAY: pay-base-url = {}", payBaseUrl);
    }

    /* ---------- создание ---------- */

    public Payment createPayment(int targetVolume) {
        if (targetVolume < 0 || targetVolume > 100) {
            throw new AppException(ErrorCode.VOLUME_INVALID,
                    "получено: " + targetVolume);
        }
        try {
            Payment p = new Payment();
            p.setToken(UUID.randomUUID().toString());
            p.setTargetVolume(targetVolume);
            p.setBasePrice(pricing.calculateBasePrice(targetVolume));
            p.setCommission(pricing.getCommission());
            p.setTotalAmount(p.getBasePrice() + p.getCommission());
            p.setDescription(pricing.generateDescription(targetVolume));
            p.setStatus(PaymentStatus.PENDING);
            p.setCreatedAt(Instant.now());
            p.setExpiresAt(Instant.now().plusSeconds(300));
            store.save(p);
            return p;
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("{}: ошибка создания платежа",
                    ErrorCode.PAYMENT_CREATION_FAILED.getCode(), e);
            throw new AppException(ErrorCode.PAYMENT_CREATION_FAILED, e.getMessage(), e);
        }
    }

    /* ---------- получение ---------- */

    public Payment getPayment(String token) {
        Payment p = store.findByToken(token)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND, token));
        refreshExpiry(p);
        return p;
    }

    /* ---------- обработка (mock-шлюз → self-webhook) ---------- */

    public void processPayment(String token, String email) {
        Payment p = getPayment(token);

        if (p.getStatus() == PaymentStatus.EXPIRED) {
            throw new AppException(ErrorCode.PAYMENT_EXPIRED, token);
        }
        if (p.getStatus() == PaymentStatus.PAID) {
            throw new AppException(ErrorCode.PAYMENT_ALREADY_PAID, token);
        }
        if (p.getStatus() != PaymentStatus.PENDING && p.getStatus() != PaymentStatus.PROCESSING) {
            throw new AppException(ErrorCode.PAYMENT_INVALID_STATE,
                    "status=" + p.getStatus());
        }

        if (email != null && !email.isBlank()) {
            p.setEmail(email);
        }
        p.setStatus(PaymentStatus.PROCESSING);
        log.info("PAY: платёж {} → PROCESSING", token);

        if ("mock".equalsIgnoreCase(paymentMode)) {
            sendSelfWebhook(p);
        }
    }

    /* ---------- webhook-обработчик ---------- */

    public void handleWebhook(String signature, String timestamp, String rawBody) {
        String expected = hmacSha256(webhookSecret, timestamp + "." + rawBody);
        if (!expected.equals(signature)) {
            log.warn("{}: ожидалось {}, получено {}",
                    ErrorCode.PAYMENT_SIGNATURE_INVALID.getCode(),
                    expected, signature);
            throw new AppException(ErrorCode.PAYMENT_SIGNATURE_INVALID,
                    "expected=" + expected + ", got=" + signature);
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(rawBody, Map.class);

            String eventType = (String) payload.getOrDefault("event_type",
                    payload.get("event"));
            String token = (String) payload.get("token");

            if (!"payment.paid".equals(eventType) && !"payment.completed".equals(eventType)) {
                log.info("PAY: webhook skipped event_type={}", eventType);
                return;
            }

            Payment p = store.findByToken(token)
                    .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND, token));

            if (p.getStatus() == PaymentStatus.PAID) {
                log.info("PAY: webhook {} уже paid, idempotent skip", token);
                return;
            }

            p.setStatus(PaymentStatus.PAID);
            audioService.setVolume(p.getTargetVolume());
            log.info("PAY: webhook {} → PAID, громкость → {}", token, p.getTargetVolume());

            // email гарантирован (валидация в контроллере)
            receiptMailService.sendReceipt(p.getEmail(), p);
        } catch (AppException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("{}: ошибка обработки webhook",
                    ErrorCode.PAYMENT_WEBHOOK_FAILED.getCode(), e);
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_FAILED, e.getMessage(), e);
        }
    }

    /* ---------- helpers ---------- */

    public String getPayBaseUrl() {
        return payBaseUrl;
    }

    private void refreshExpiry(Payment p) {
        if (p.getStatus() == PaymentStatus.PENDING
                && Instant.now().isAfter(p.getExpiresAt())) {
            p.setStatus(PaymentStatus.EXPIRED);
        }
    }

    private String resolveSelfWebhookUrl() {
        if (callbackInternalUrl != null && !callbackInternalUrl.isBlank()) {
            return callbackInternalUrl;
        }
        return "http://localhost:" + serverPort + "/api/payments/webhook";
    }

    private void sendSelfWebhook(Payment p) {
        String selfUrl = resolveSelfWebhookUrl();
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_type", "payment.paid");
            payload.put("token", p.getToken());
            payload.put("amount", p.getTotalAmount());
            payload.put("targetVolume", p.getTargetVolume());
            payload.put("timestamp", timestamp);

            String body = mapper.writeValueAsString(payload);
            String sig = hmacSha256(webhookSecret, timestamp + "." + body);

            log.info("PAY: self-webhook → {} (token={})", selfUrl, p.getToken());

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(selfUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Signature", sig)
                    .header("X-Timestamp", timestamp)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 400) {
                log.error("{}: self-webhook {} вернул {}: {}",
                        ErrorCode.PAYMENT_WEBHOOK_FAILED.getCode(),
                        selfUrl, response.statusCode(), response.body());
                throw new AppException(ErrorCode.PAYMENT_WEBHOOK_FAILED,
                        "self-webhook " + response.statusCode() + ": " + response.body());
            }
        } catch (AppException ae) {
            throw ae;
        } catch (Exception e) {
            log.error("{}: не удалось отправить self-webhook на {}",
                    ErrorCode.PAYMENT_WEBHOOK_FAILED.getCode(), selfUrl, e);
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_FAILED,
                    e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private String hmacSha256(String secret, String data) {
        if (secret == null || secret.isEmpty()) {
            log.error("{}: webhook-secret пустой/null",
                    ErrorCode.PAYMENT_SECRET_MISSING.getCode());
            throw new AppException(ErrorCode.PAYMENT_SECRET_MISSING);
        }
        if (data == null) {
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_FAILED, "data is null");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            log.error("{}: HMAC compute error",
                    ErrorCode.PAYMENT_WEBHOOK_FAILED.getCode(), e);
            throw new AppException(ErrorCode.PAYMENT_WEBHOOK_FAILED,
                    "HMAC " + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }
}
