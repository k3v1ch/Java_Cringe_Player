package com.cringe.volume.payment;

public class CreatePaymentResponse {
    private String token;
    private String payUrl;
    private int basePrice;
    private int commission;
    private int totalAmount;
    private String description;
    private long expiresAtMs;

    public CreatePaymentResponse(Payment payment, String payBaseUrl) {
        this.token = payment.getToken();
        this.payUrl = payBaseUrl + "/pay/" + payment.getToken();
        this.basePrice = payment.getBasePrice();
        this.commission = payment.getCommission();
        this.totalAmount = payment.getTotalAmount();
        this.description = payment.getDescription();
        this.expiresAtMs = payment.getExpiresAt().toEpochMilli();
    }

    public String getToken() { return token; }
    public String getPayUrl() { return payUrl; }
    public int getBasePrice() { return basePrice; }
    public int getCommission() { return commission; }
    public int getTotalAmount() { return totalAmount; }
    public String getDescription() { return description; }
    public long getExpiresAtMs() { return expiresAtMs; }
}
