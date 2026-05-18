package com.cringe.volume.payment;

public class PaymentStatusResponse {
    private String token;
    private String status;
    private int targetVolume;
    private int totalAmount;

    public PaymentStatusResponse(Payment payment) {
        this.token = payment.getToken();
        this.status = payment.getStatus().name().toLowerCase();
        this.targetVolume = payment.getTargetVolume();
        this.totalAmount = payment.getTotalAmount();
    }

    public String getToken() { return token; }
    public String getStatus() { return status; }
    public int getTargetVolume() { return targetVolume; }
    public int getTotalAmount() { return totalAmount; }
}
