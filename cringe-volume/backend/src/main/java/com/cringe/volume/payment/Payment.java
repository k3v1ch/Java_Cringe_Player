package com.cringe.volume.payment;

import java.time.Instant;

public class Payment {
    private String token;
    private int targetVolume;
    private int basePrice;
    private int commission;
    private int totalAmount;
    private String description;
    private PaymentStatus status;
    private Instant createdAt;
    private Instant expiresAt;
    private String email;

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public int getTargetVolume() { return targetVolume; }
    public void setTargetVolume(int targetVolume) { this.targetVolume = targetVolume; }

    public int getBasePrice() { return basePrice; }
    public void setBasePrice(int basePrice) { this.basePrice = basePrice; }

    public int getCommission() { return commission; }
    public void setCommission(int commission) { this.commission = commission; }

    public int getTotalAmount() { return totalAmount; }
    public void setTotalAmount(int totalAmount) { this.totalAmount = totalAmount; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getExpiresAt() { return expiresAt; }
    public void setExpiresAt(Instant expiresAt) { this.expiresAt = expiresAt; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
