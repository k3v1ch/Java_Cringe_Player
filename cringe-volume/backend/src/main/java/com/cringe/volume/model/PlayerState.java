package com.cringe.volume.model;

import org.springframework.stereotype.Component;

@Component
public class PlayerState {
    private String fileName;
    private boolean playing;
    private int volume = 50;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }
}
