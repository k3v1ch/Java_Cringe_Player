package com.cringe.volume.payment;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public class ProcessPaymentRequest {

    @NotBlank(message = "Email обязателен для получения чека")
    @Email(message = "Некорректный формат email")
    private String email;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
