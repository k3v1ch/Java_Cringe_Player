package com.cringe.player.player;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.File;

public class AudioPlayerEngine {

    private MediaPlayer mediaPlayer;
    private double volume = 0.5;

    public void load(File file) {
        loadFromUri(file.toURI().toString());
    }

    /**
     * Loads a track from URL (http:// stream from backend).
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

    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    /**
     * Seek to given position in seconds.
     */
    public void seek(double seconds) {
        if (mediaPlayer != null) {
            mediaPlayer.seek(Duration.seconds(seconds));
        }
    }

    /**
     * Returns the underlying MediaPlayer (for wiring UI listeners).
     * May be null if no track is loaded.
     */
    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
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

    public boolean isPlaying() {
        return mediaPlayer != null
                && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
    }

    public void dispose() {
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
    }
}
