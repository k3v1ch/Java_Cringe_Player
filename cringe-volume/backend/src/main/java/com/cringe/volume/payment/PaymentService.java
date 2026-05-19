package com.cringe.volume.payment;

import com.cringe.volume.mail.ReceiptMailService;
import com.cringe.volume.service.AudioService;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    /**
     * URL для self-вебхука внутри инфраструктуры (НЕ через публичный домен).
     * В Docker — это localhost внутри контейнера. По умолчанию вычисляем сами.
     */
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

    /* ---------- создание ---------- */

    public Payment createPayment(int targetVolume) {
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
    }

    /* ---------- получение ---------- */

    public Payment getPayment(String token) {
        Payment p = store.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Платёж не найден: " + token));
        refreshExpiry(p);
        return p;
    }

    /* ---------- обработка (mock-шлюз → self-webhook) ---------- */

    public void processPayment(String token, String email) {
        Payment p = getPayment(token);

        if (p.getStatus() == PaymentStatus.EXPIRED) {
            throw new IllegalStateException("Время оплаты истекло");
        }
        if (p.getStatus() == PaymentStatus.PAID) {
            throw new IllegalStateException("Платёж уже обработан");
        }
        // PROCESSING — позволяем повторный вызов (повторим self-webhook)
        if (p.getStatus() != PaymentStatus.PENDING && p.getStatus() != PaymentStatus.PROCESSING) {
            throw new IllegalStateException("Платёж в недопустимом статусе: " + p.getStatus());
        }

        if (email != null && !email.isBlank()) {
            p.setEmail(email);
        }
        p.setStatus(PaymentStatus.PROCESSING);

        if ("mock".equalsIgnoreCase(paymentMode)) {
            // отправляем self-webhook СИНХРОННО, чтобы клиент сразу мог увидеть paid
            sendSelfWebhook(p);
        }
    }

    /* ---------- webhook-обработчик ---------- */

    public void handleWebhook(String signature, String timestamp, String rawBody) {
        String expected = hmacSha256(webhookSecret, timestamp + "." + rawBody);
        if (!expected.equals(signature)) {
            log.warn("Webhook: signature mismatch. expected={}, got={}", expected, signature);
            throw new SecurityException("Неверная подпись вебхука");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(rawBody, Map.class);

            // поддерживаем оба варианта: event_type (новый) и event (старый)
            String eventType = (String) payload.getOrDefault("event_type", payload.get("event"));
            String token = (String) payload.get("token");

            if (!"payment.paid".equals(eventType) && !"payment.completed".equals(eventType)) {
                log.info("Webhook: skipping event_type={}", eventType);
                return;
            }

            Payment p = store.findByToken(token)
                    .orElseThrow(() -> new IllegalArgumentException("Платёж не найден"));

            if (p.getStatus() == PaymentStatus.PAID) {
                log.info("Webhook: платёж {} уже paid, пропускаем", token);
                return;
            }

            p.setStatus(PaymentStatus.PAID);
            audioService.setVolume(p.getTargetVolume());
            log.info("Webhook: платёж {} помечен paid, громкость → {}",
                    token, p.getTargetVolume());

            // чек — сбой SMTP не влияет на статус (метод @Async внутри)
            receiptMailService.sendReceipt(p.getEmail(), p);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            log.error("Webhook: ошибка обработки", e);
            throw new RuntimeException("Ошибка обработки вебхука", e);
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
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            // LinkedHashMap чтобы порядок полей был детерминирован при сериализации
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_type", "payment.paid");
            payload.put("token", p.getToken());
            payload.put("amount", p.getTotalAmount());
            payload.put("targetVolume", p.getTargetVolume());
            payload.put("timestamp", timestamp);

            String body = mapper.writeValueAsString(payload);
            String sig = hmacSha256(webhookSecret, timestamp + "." + body);

            String selfUrl = resolveSelfWebhookUrl();
            log.info("Self-webhook → {} (token={})", selfUrl, p.getToken());

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
                log.error("Self-webhook вернул {}: {}",
                        response.statusCode(), response.body());
                throw new RuntimeException("Webhook вернул "
                        + response.statusCode() + ": " + response.body());
            }
        } catch (RuntimeException re) {
            throw re;
        } catch (Exception e) {
            throw new RuntimeException("Ошибка отправки self-webhook: " + e.getMessage(), e);
        }
    }

    private String hmacSha256(String secret, String data) {
        // Явная валидация — даёт понятную ошибку вместо "Empty key" от JCA.
        if (secret == null || secret.isEmpty()) {
            log.error("HMAC: secret is null/empty. "
                    + "Установите ROLLYPAY_SIGNING_SECRET в .env (минимум 1 символ).");
            throw new RuntimeException(
                    "HMAC-SHA256 error: webhook-secret пустой. "
                    + "Установите ROLLYPAY_SIGNING_SECRET в .env");
        }
        if (data == null) {
            throw new RuntimeException("HMAC-SHA256 error: data is null");
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
            // Включаем тип исключения и его message в текст,
            // т.к. GlobalExceptionHandler покажет только наш message.
            String detail = e.getClass().getSimpleName();
            if (e.getMessage() != null) detail += ": " + e.getMessage();
            log.error("HMAC-SHA256 error: {}", detail, e);
            throw new RuntimeException("HMAC-SHA256 error: " + detail, e);
        }
    }

    /**
     * Диагностика секрета при старте — длина без раскрытия значения.
     */
    @jakarta.annotation.PostConstruct
    void logWebhookSecretInfo() {
        if (webhookSecret == null || webhookSecret.isEmpty()) {
            log.error("⚠ payment.webhook-secret ПУСТОЙ! "
                    + "Платежи не будут работать. Проверьте ROLLYPAY_SIGNING_SECRET в .env");
        } else {
            log.info("payment.webhook-secret загружен ({} симв.)", webhookSecret.length());
        }
        log.info("payment.callback-internal-url = {}", callbackInternalUrl);
        log.info("payment.pay-base-url = {}", payBaseUrl);
    }
}
