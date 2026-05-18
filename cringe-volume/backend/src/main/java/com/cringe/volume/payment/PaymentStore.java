package com.cringe.volume.payment;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PaymentStore {

    private final ConcurrentHashMap<String, Payment> payments = new ConcurrentHashMap<>();

    public void save(Payment payment) {
        payments.put(payment.getToken(), payment);
    }

    public Optional<Payment> findByToken(String token) {
        return Optional.ofNullable(payments.get(token));
    }
}
