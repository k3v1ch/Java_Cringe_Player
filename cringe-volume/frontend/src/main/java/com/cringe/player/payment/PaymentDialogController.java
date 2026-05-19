package com.cringe.player.payment;

import com.cringe.player.CringePlayerApp;
import com.cringe.player.api.ApiConfig;
import com.cringe.player.api.PaymentApiClient;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;

public class PaymentDialogController {

    @FXML private Label amountLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label statusLabel;
    @FXML private ProgressIndicator spinner;
    @FXML private Button openBrowserButton;
    @FXML private Button cancelButton;

    private final PaymentApiClient apiClient = new PaymentApiClient();

    private String paymentToken;
    private int targetVolume;
    private boolean paymentCompleted = false;
    private volatile boolean polling = false;

    public void setTargetVolume(int volume) {
        this.targetVolume = volume;
    }

    @FXML
    public void initialize() {
        statusLabel.setText("Создание платежа...");
    }

    /**
     * Вызывается из MainController после загрузки FXML, чтобы запустить
     * создание платежа и открытие браузера.
     */
    public void startPaymentFlow() {
        Thread t = new Thread(() -> {
            try {
                JsonObject resp = apiClient.createPayment(targetVolume);
                paymentToken = resp.get("token").getAsString();
                String payUrl = resp.get("payUrl").getAsString();
                int total = resp.get("totalAmount").getAsInt();
                String desc = resp.get("description").getAsString();

                Platform.runLater(() -> {
                    amountLabel.setText("Сумма: " + total + " ₽");
                    descriptionLabel.setText(desc);
                    statusLabel.setText("Ожидание оплаты...");
                    openBrowserButton.setDisable(false);
                });

                CringePlayerApp.hostServices().showDocument(payUrl);
                startPolling();
            } catch (Exception e) {
                e.printStackTrace();
                String msg = e.getMessage();
                if (msg == null || msg.isBlank()) msg = e.getClass().getSimpleName();
                final String finalMsg = msg;
                Platform.runLater(() -> {
                    statusLabel.setText("Ошибка: " + finalMsg);
                    spinner.setVisible(false);
                });
            }
        });
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void onOpenBrowser() {
        if (paymentToken != null) {
            String url = ApiConfig.PAY_PAGE_URL + "/" + paymentToken;
            CringePlayerApp.hostServices().showDocument(url);
        }
    }

    @FXML
    private void onCancel() {
        polling = false;
        paymentCompleted = false;
        close();
    }

    private void startPolling() {
        polling = true;
        Thread t = new Thread(() -> {
            while (polling) {
                try {
                    Thread.sleep(2000);
                    if (!polling) break;

                    JsonObject status = apiClient.getPaymentStatus(paymentToken);
                    String st = status.get("status").getAsString();

                    if ("paid".equals(st)) {
                        polling = false;
                        paymentCompleted = true;
                        Platform.runLater(() -> {
                            statusLabel.setText("Платёж проведён ✅");
                            spinner.setVisible(false);
                            new Thread(() -> {
                                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                                Platform.runLater(this::close);
                            }).start();
                        });
                    } else if ("expired".equals(st)) {
                        polling = false;
                        Platform.runLater(() -> {
                            statusLabel.setText("Время оплаты истекло ⏰");
                            spinner.setVisible(false);
                        });
                    }
                } catch (Exception e) {
                    // продолжаем опрос даже при временных ошибках
                }
            }
        });
        t.setDaemon(true);
        t.start();
    }

    private void close() {
        Stage stage = (Stage) cancelButton.getScene().getWindow();
        stage.close();
    }

    public boolean isPaymentCompleted() {
        return paymentCompleted;
    }

    public int getTargetVolume() {
        return targetVolume;
    }
}
