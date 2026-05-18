package com.cringe.player.player;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;

public class AudioPlayerEngine {

    private MediaPlayer mediaPlayer;
    private double volume = 0.5;

    public void load(File file) {
        loadFromUri(file.toURI().toString());
    }

    /**
     * Загружает трек по URL (http:// стрим с бэкенда).
     */
    public void loadFromUrl(String url) {
        loadFromUri(url);
    }

    private void loadFromUri(String uri) {
        stop();
        Media media = new Media(uri);
        mediaPlayer = new MediaPlayer(media);
        mediaPlayer.setVolume(volume);
        mediaPlayer.setCycleCount(MediaPlayer.INDEFINITE);
    }

    public void play() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    public void setVolume(int volumePercent) {
        this.volume = volumePercent / 100.0;
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(this.volume);
        }
    }

    public int getVolume() {
        return (int) (volume * 100);
    }

    public boolean isLoaded() {
        return mediaPlayer != null;
    }

    public void dispose() {
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }
}
