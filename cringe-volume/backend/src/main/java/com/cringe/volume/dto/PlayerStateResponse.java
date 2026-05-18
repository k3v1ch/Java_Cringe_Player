package com.cringe.volume.dto;

public class PlayerStateResponse {
    private String fileName;
    private boolean playing;
    private int volume;

    public PlayerStateResponse(String fileName, boolean playing, int volume) {
        this.fileName = fileName;
        this.playing = playing;
        this.volume = volume;
    }

    public String getFileName() {
        return fileName;
    }

    public boolean isPlaying() {
        return playing;
    }

    public int getVolume() {
        return volume;
    }
}
