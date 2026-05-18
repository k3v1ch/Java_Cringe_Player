package com.cringe.volume.payment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class CreatePaymentRequest {

    @NotNull(message = "Целевая громкость обязательна")
    @Min(value = 0, message = "Громкость не может быть меньше 0")
    @Max(value = 100, message = "Громкость не может быть больше 100")
    private Integer targetVolume;

    public Integer getTargetVolume() { return targetVolume; }
    public void setTargetVolume(Integer targetVolume) { this.targetVolume = targetVolume; }
}
