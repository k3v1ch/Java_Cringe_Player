package com.cringe.volume.payment;

import com.cringe.volume.mail.ReceiptMailService;
import com.cringe.volume.service.AudioService;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentService {

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
        if (p.getStatus() != PaymentStatus.PENDING) {
            throw new IllegalStateException("Платёж уже обработан");
        }

        p.setEmail(email);
        p.setStatus(PaymentStatus.PROCESSING);

        if ("mock".equalsIgnoreCase(paymentMode)) {
            sendSelfWebhook(p);
        }
    }

    /* ---------- webhook-обработчик ---------- */

    public void handleWebhook(String signature, String timestamp, String rawBody) {
        String expected = hmacSha256(webhookSecret, timestamp + "." + rawBody);
        if (!expected.equals(signature)) {
            throw new SecurityException("Неверная подпись вебхука");
        }

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.readValue(rawBody, Map.class);
            String event = (String) payload.get("event");
            String token = (String) payload.get("token");

            if (!"payment.completed".equals(event)) return;

            Payment p = store.findByToken(token)
                    .orElseThrow(() -> new IllegalArgumentException("Платёж не найден"));
            p.setStatus(PaymentStatus.PAID);
            audioService.setVolume(p.getTargetVolume());

            // чек — сбой SMTP не влияет на статус
            receiptMailService.sendReceipt(p.getEmail(), p);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
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

    private void sendSelfWebhook(Payment p) {
        try {
            String timestamp = String.valueOf(Instant.now().getEpochSecond());
            Map<String, Object> payload = Map.of(
                    "event", "payment.completed",
                    "token", p.getToken(),
                    "amount", p.getTotalAmount(),
                    "targetVolume", p.getTargetVolume()
            );
            String body = mapper.writeValueAsString(payload);
            String sig = hmacSha256(webhookSecret, timestamp + "." + body);

            String selfUrl = "http://localhost:" + serverPort + "/api/payments/webhook";

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(selfUrl))
                    .header("Content-Type", "application/json")
                    .header("X-Signature", sig)
                    .header("X-Timestamp", timestamp)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new RuntimeException("Ошибка отправки self-webhook", e);
        }
    }

    private String hmacSha256(String secret, String data) {
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
            throw new RuntimeException("HMAC-SHA256 error", e);
        }
    }
}
