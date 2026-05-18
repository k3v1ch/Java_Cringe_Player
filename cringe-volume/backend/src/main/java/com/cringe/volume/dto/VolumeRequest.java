package com.cringe.volume.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class VolumeRequest {

    @NotNull(message = "Громкость обязательна")
    @Min(value = 0, message = "Громкость не может быть меньше 0")
    @Max(value = 100, message = "Громкость не может быть больше 100")
    private Integer volume;

    public Integer getVolume() {
        return volume;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }
}
