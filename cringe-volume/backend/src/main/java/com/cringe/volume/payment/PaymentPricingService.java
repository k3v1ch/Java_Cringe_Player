package com.cringe.volume.payment;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class PaymentPricingService {

    private static final Random RNG = new Random();
    private static final int COMMISSION = 15;

    public int calculateBasePrice(int targetVolume) {
        int base = 19 + RNG.nextInt(81);   // 19..99 ₽
        if (targetVolume > 50) {
            base *= 3;                      // тариф «громкий режим»
        }
        return base;
    }

    public int getCommission() {
        return COMMISSION;
    }

    public String generateDescription(int targetVolume) {
        String desc = "Изменение громкости на " + targetVolume + "%";
        if (targetVolume > 50) {
            desc += " (громкий режим — тариф ×3)";
        }
        return desc;
    }
}
