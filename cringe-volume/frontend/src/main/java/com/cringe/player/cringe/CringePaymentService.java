package com.cringe.player.cringe;

import java.util.Random;

public class CringePaymentService {

    private static final Random RNG = new Random();

    private static final String[] COMMISSION_REASONS = {
            "Комиссия за обработку звука",
            "Сервисный сбор (НДС включён)",
            "Плата за использование ползунка",
            "Экологический сбор за децибелы",
            "Комиссия за изменение амплитуды",
            "Абонентская плата за частоты"
    };

    public int generateBasePrice() {
        return 49 + RNG.nextInt(952);
    }

    public int generateCommission() {
        return 5 + RNG.nextInt(96);
    }

    public String getCommissionReason() {
        return COMMISSION_REASONS[RNG.nextInt(COMMISSION_REASONS.length)];
    }

    public int getProcessingTimeMs() {
        return 2000 + RNG.nextInt(1500);
    }
}
