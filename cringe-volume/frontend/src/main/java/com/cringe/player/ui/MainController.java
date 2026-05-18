package com.cringe.player.ui;

import com.cringe.player.api.AudioApiClient;
import com.cringe.player.payment.PaymentDialogController;
import com.cringe.player.player.AudioPlayerEngine;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;

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

    private final AudioPlayerEngine playerEngine = new AudioPlayerEngine();
    private final AudioApiClient apiClient = new AudioApiClient();
    private File currentFile;

    @FXML
    public void initialize() {
        playButton.setDisable(true);
        stopButton.setDisable(true);
        applyVolumeButton.setDisable(true);
        volumeField.setDisable(true);
        volumeLabel.setText("Громкость: 50%");
        statusLabel.setText("Загрузите или выберите трек");

        // загрузить список треков с сервера
        refreshTracks();
    }

    /* ========== Треки с сервера ========== */

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
                Platform.runLater(() ->
                        statusLabel.setText("Ошибка загрузки треков"));
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
            statusLabel.setText("Трек готов: " + selected);
        } catch (Exception e) {
            showError("Ошибка", "Не удалось загрузить трек: " + e.getMessage());
            statusLabel.setText("Ошибка загрузки трека");
        }
    }

    /* ========== Загрузка с диска ========== */

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
                    chooseFileButton.setDisable(false);
                    statusLabel.setText("Файл загружен. Готов к воспроизведению");
                    refreshTracks(); // обновить список
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    chooseFileButton.setDisable(false);
                    showError("Ошибка загрузки", e.getMessage());
                    statusLabel.setText("Ошибка загрузки");
                });
            }
        });
        uploadThread.setDaemon(true);
        uploadThread.start();
    }

    /* ========== Воспроизведение ========== */

    @FXML
    private void onPlay() {
        Thread t = new Thread(() -> {
            try {
                apiClient.play();
                Platform.runLater(() -> {
                    playerEngine.play();
                    playButton.setDisable(true);
                    stopButton.setDisable(false);
                    statusLabel.setText("Воспроизведение...");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Ошибка", e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onStop() {
        Thread t = new Thread(() -> {
            try {
                apiClient.stop();
                Platform.runLater(() -> {
                    playerEngine.stop();
                    playButton.setDisable(false);
                    stopButton.setDisable(true);
                    statusLabel.setText("Остановлено");
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Ошибка", e.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
    }

    /* ========== Громкость (через платёж) ========== */

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
        alert.setContentText(message);
        alert.showAndWait();
    }
}
