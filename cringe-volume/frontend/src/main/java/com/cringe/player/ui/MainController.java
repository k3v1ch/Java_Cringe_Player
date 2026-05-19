package com.cringe.player.ui;

import com.cringe.player.api.AudioApiClient;
import com.cringe.player.payment.PaymentDialogController;
import com.cringe.player.player.AudioPlayerEngine;
import com.cringe.player.update.AppUpdater;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.media.MediaPlayer;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.util.List;

public class MainController {

    @FXML private Label fileLabel;
    @FXML private Button chooseFileButton;
    @FXML private Button playButton;
    @FXML private Button stopButton;
    @FXML private TextField volumeField;
    @FXML private Button applyVolumeButton;
    @FXML private Label volumeLabel;
    @FXML private Label statusLabel;
    @FXML private ComboBox<String> trackCombo;
    @FXML private Button refreshTracksButton;

    /* seek bar */
    @FXML private Slider seekSlider;
    @FXML private Label currentTimeLabel;
    @FXML private Label durationLabel;

    private final AudioPlayerEngine playerEngine = new AudioPlayerEngine();
    private final AudioApiClient apiClient = new AudioApiClient();
    private File currentFile;
    private boolean seeking = false;

    @FXML
    public void initialize() {
        playButton.setDisable(true);
        stopButton.setDisable(true);
        applyVolumeButton.setDisable(true);
        volumeField.setDisable(true);
        seekSlider.setDisable(true);
        volumeLabel.setText("Громкость: 50%");
        statusLabel.setText("Загрузите или выберите трек");

        // Seek slider: when user presses mouse — stop auto-updating
        seekSlider.setOnMousePressed(e -> seeking = true);
        seekSlider.setOnMouseReleased(e -> {
            seeking = false;
            playerEngine.seek(seekSlider.getValue());
        });

        refreshTracks();

        // Auto-update check (background, non-blocking)
        AppUpdater.checkOnStartup();
    }

    /* ========== Tracks from server ========== */

    @FXML
    private void onRefreshTracks() {
        refreshTracks();
    }

    private void refreshTracks() {
        Thread t = new Thread(() -> {
            try {
                List<String> tracks = apiClient.listTracks();
                Platform.runLater(() -> {
                    trackCombo.setItems(FXCollections.observableArrayList(tracks));
                    if (!tracks.isEmpty()) {
                        statusLabel.setText("Треков на сервере: " + tracks.size());
                    } else {
                        statusLabel.setText("На сервере нет треков. Загрузите файл.");
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                String msg = describeError(e);
                Platform.runLater(() ->
                        statusLabel.setText("Ошибка загрузки треков: " + msg));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onSelectTrack() {
        String selected = trackCombo.getValue();
        if (selected == null || selected.isBlank()) return;

        statusLabel.setText("Загрузка трека: " + selected);
        String url = apiClient.getStreamUrl(selected);

        try {
            playerEngine.loadFromUrl(url);
            fileLabel.setText(selected);
            playButton.setDisable(false);
            applyVolumeButton.setDisable(false);
            volumeField.setDisable(false);
            seekSlider.setDisable(false);
            statusLabel.setText("Трек готов: " + selected);
            wireSeekSlider();
        } catch (Exception e) {
            e.printStackTrace();
            String msg = describeError(e);
            showError("Ошибка", "Не удалось загрузить трек: " + msg);
            statusLabel.setText("Ошибка загрузки трека: " + msg);
        }
    }

    /* ========== Upload from disk ========== */

    @FXML
    private void onChooseFile() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Выберите аудиофайл");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Аудио файлы", "*.mp3", "*.wav"),
                new FileChooser.ExtensionFilter("Все файлы", "*.*")
        );

        Stage stage = (Stage) chooseFileButton.getScene().getWindow();
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;

        currentFile = file;
        fileLabel.setText(file.getName());
        statusLabel.setText("Загрузка на сервер...");
        chooseFileButton.setDisable(true);

        Thread uploadThread = new Thread(() -> {
            try {
                apiClient.uploadFile(file.toPath());
                playerEngine.load(file);
                Platform.runLater(() -> {
                    playButton.setDisable(false);
                    applyVolumeButton.setDisable(false);
                    volumeField.setDisable(false);
                    seekSlider.setDisable(false);
                    chooseFileButton.setDisable(false);
                    statusLabel.setText("Файл загружен. Готов к воспроизведению");
                    wireSeekSlider();
                    refreshTracks();
                });
            } catch (Exception e) {
                e.printStackTrace();
                String msg = describeError(e);
                Platform.runLater(() -> {
                    chooseFileButton.setDisable(false);
                    showError("Ошибка загрузки", msg);
                    statusLabel.setText("Ошибка загрузки: " + msg);
                });
            }
        });
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /**
     * Turns any exception into a readable message.
     */
    private String describeError(Throwable e) {
        if (e instanceof com.cringe.player.api.ApiException ae) {
            return ae.toUserMessage();
        }
        StringBuilder sb = new StringBuilder();
        Throwable cur = e;
        int depth = 0;
        while (cur != null && depth < 5) {
            String m = cur.getMessage();
            if (m == null || m.isBlank()) m = cur.getClass().getSimpleName();
            if (!sb.isEmpty()) sb.append(" → ");
            sb.append(m);
            Throwable next = cur.getCause();
            if (next == cur) break;
            cur = next;
            depth++;
        }
        return sb.toString();
    }

    /* ========== Playback ========== */

    @FXML
    private void onPlay() {
        try {
            if (playerEngine.isPlaying()) {
                playerEngine.pause();
                playButton.setText("▶ Play");
                statusLabel.setText("Пауза");
            } else {
                playerEngine.play();
                playButton.setText("⏸ Pause");
                stopButton.setDisable(false);
                statusLabel.setText("Воспроизведение...");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Ошибка", describeError(e));
        }
    }

    @FXML
    private void onStop() {
        try {
            playerEngine.stop();
            playButton.setText("▶ Play");
            stopButton.setDisable(true);
            seekSlider.setValue(0);
            currentTimeLabel.setText("0:00");
            statusLabel.setText("Остановлено");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Ошибка", describeError(e));
        }
    }

    /* ========== Seek slider wiring ========== */

    /**
     * Called after loading a new track to wire MediaPlayer listeners
     * to the seek slider and time labels.
     */
    private void wireSeekSlider() {
        MediaPlayer mp = playerEngine.getMediaPlayer();
        if (mp == null) return;

        // Reset slider
        seekSlider.setValue(0);
        currentTimeLabel.setText("0:00");
        durationLabel.setText("0:00");

        // When media is ready — set slider max and duration label
        mp.setOnReady(() -> {
            double totalSec = mp.getTotalDuration().toSeconds();
            seekSlider.setMax(totalSec);
            durationLabel.setText(formatTime(mp.getTotalDuration()));
        });

        // Update slider and time label as track plays
        mp.currentTimeProperty().addListener((obs, oldVal, newVal) -> {
            if (!seeking) {
                seekSlider.setValue(newVal.toSeconds());
            }
            currentTimeLabel.setText(formatTime(newVal));
        });

        // When track ends (cycle restarts)
        mp.setOnEndOfMedia(() -> {
            // INDEFINITE cycle — will auto-restart, slider resets via currentTime listener
        });

        // Sync play/pause button text
        mp.setOnPlaying(() -> playButton.setText("⏸ Pause"));
        mp.setOnPaused(() -> playButton.setText("▶ Play"));
        mp.setOnStopped(() -> playButton.setText("▶ Play"));
    }

    private String formatTime(Duration d) {
        if (d == null || d.isUnknown() || d.isIndefinite()) return "0:00";
        int totalSec = (int) d.toSeconds();
        int min = totalSec / 60;
        int sec = totalSec % 60;
        return min + ":" + String.format("%02d", sec);
    }

    /* ========== Volume (via payment) ========== */

    @FXML
    private void onApplyVolume() {
        String text = volumeField.getText().trim();
        int newVolume;
        try {
            newVolume = Integer.parseInt(text);
        } catch (NumberFormatException e) {
            showError("Ошибка", "Введите число от 0 до 100");
            return;
        }
        if (newVolume < 0 || newVolume > 100) {
            showError("Ошибка", "Громкость должна быть от 0 до 100");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/payment.fxml")
            );
            Parent root = loader.load();

            PaymentDialogController controller = loader.getController();
            controller.setTargetVolume(newVolume);

            Stage dialog = new Stage();
            dialog.setTitle("CringePay — Оплата громкости");
            dialog.setScene(new Scene(root, 400, 380));
            dialog.setResizable(false);
            dialog.initModality(Modality.APPLICATION_MODAL);
            dialog.initOwner(applyVolumeButton.getScene().getWindow());

            controller.startPaymentFlow();
            dialog.showAndWait();

            if (controller.isPaymentCompleted()) {
                applyVolumeAfterPayment(controller.getTargetVolume());
            }
        } catch (Exception e) {
            showError("Ошибка", "Не удалось открыть окно оплаты: " + e.getMessage());
        }
    }

    private void applyVolumeAfterPayment(int volume) {
        playerEngine.setVolume(volume);
        volumeLabel.setText("Громкость: " + volume + "%");
        statusLabel.setText("Громкость изменена на " + volume + "%");
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message == null || message.isBlank()
                ? "Неизвестная ошибка (см. консоль)" : message);
        alert.showAndWait();
    }
}
