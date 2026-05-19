package com.cringe.player.update;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

/**
 * Auto-updater that checks GitHub Releases for new versions.
 *
 * HOW TO SET UP:
 * 1. Create a GitHub repo for your project
 * 2. Set GITHUB_OWNER and GITHUB_REPO below
 * 3. When you build a new version, create a GitHub Release with tag "v1.0.1"
 *    and attach the .exe installer as a release asset
 * 4. The app will auto-check on startup and offer to download/install
 */
public class AppUpdater {

    // ====== CONFIGURE THESE ======
    private static final String GITHUB_OWNER = "k3v1ch";
    private static final String GITHUB_REPO  = "Java_Cringe_Player";
    // =============================

    public static final String CURRENT_VERSION = "1.0.0";

    private static final String API_URL = "https://api.github.com/repos/"
            + GITHUB_OWNER + "/" + GITHUB_REPO + "/releases/latest";

    private static final Gson GSON = new Gson();

    /**
     * Check for updates in background. Call from initialize().
     * Never blocks UI. Silently does nothing if check fails.
     */
    public static void checkOnStartup() {
        // Skip if disabled via system property
        String disabled = System.getProperty("update.disabled");
        if ("true".equalsIgnoreCase(disabled)) {
            System.out.println("[Updater] Disabled via -Dupdate.disabled=true");
            return;
        }

        Thread t = new Thread(() -> {
            try {
                System.out.println("[Updater] Checking " + API_URL);
                UpdateInfo info = fetchLatestRelease();

                if (info == null) {
                    System.out.println("[Updater] No release found or no .exe asset");
                    return;
                }

                System.out.println("[Updater] Latest: " + info.version
                        + ", current: " + CURRENT_VERSION);

                if (isNewer(info.version, CURRENT_VERSION)) {
                    System.out.println("[Updater] Update available! " + info.downloadUrl);
                    Platform.runLater(() -> showUpdateDialog(info));
                } else {
                    System.out.println("[Updater] Up to date");
                }
            } catch (Exception e) {
                // Silent fail — don't bother the user if GitHub is unreachable
                System.out.println("[Updater] Check failed: " + e.getMessage());
            }
        }, "updater-check");
        t.setDaemon(true);
        t.start();
    }

    /* ---------- GitHub API ---------- */

    private static UpdateInfo fetchLatestRelease() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "CringeVolumePlayer/" + CURRENT_VERSION)
                .timeout(Duration.ofSeconds(15))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            System.out.println("[Updater] GitHub API returned " + response.statusCode());
            return null;
        }

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        String tagName = json.get("tag_name").getAsString();
        String version = tagName.startsWith("v") ? tagName.substring(1) : tagName;

        String changelog = "";
        if (json.has("body") && !json.get("body").isJsonNull()) {
            changelog = json.get("body").getAsString();
        }

        // Find .exe asset
        String downloadUrl = null;
        if (json.has("assets")) {
            JsonArray assets = json.getAsJsonArray("assets");
            for (int i = 0; i < assets.size(); i++) {
                JsonObject asset = assets.get(i).getAsJsonObject();
                String name = asset.get("name").getAsString();
                if (name.toLowerCase().endsWith(".exe")) {
                    downloadUrl = asset.get("browser_download_url").getAsString();
                    break;
                }
            }
        }

        if (downloadUrl == null) return null;
        return new UpdateInfo(version, downloadUrl, changelog);
    }

    /* ---------- Version comparison ---------- */

    /**
     * Returns true if remote version is newer than local.
     * Compares numerically: "1.0.1" > "1.0.0", "1.2.0" > "1.1.9"
     */
    static boolean isNewer(String remote, String local) {
        try {
            String[] r = remote.split("\\.");
            String[] l = local.split("\\.");
            int len = Math.max(r.length, l.length);
            for (int i = 0; i < len; i++) {
                int rv = i < r.length ? Integer.parseInt(r[i].trim()) : 0;
                int lv = i < l.length ? Integer.parseInt(l[i].trim()) : 0;
                if (rv > lv) return true;
                if (rv < lv) return false;
            }
        } catch (NumberFormatException e) {
            // Non-numeric version — just compare as strings
            return remote.compareTo(local) > 0;
        }
        return false;
    }

    /* ---------- UI ---------- */

    private static void showUpdateDialog(UpdateInfo info) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Update available");
        alert.setHeaderText("New version: " + info.version
                + "  (current: " + CURRENT_VERSION + ")");

        String text = "Download and install update?";
        if (info.changelog != null && !info.changelog.isBlank()) {
            // Show first 200 chars of changelog
            String log = info.changelog.length() > 200
                    ? info.changelog.substring(0, 200) + "..."
                    : info.changelog;
            text += "\n\n" + log;
        }
        alert.setContentText(text);

        ButtonType btnDownload = new ButtonType("Download", ButtonBar.ButtonData.OK_DONE);
        ButtonType btnSkip = new ButtonType("Later", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(btnDownload, btnSkip);

        alert.showAndWait().ifPresent(btn -> {
            if (btn == btnDownload) {
                downloadAndInstall(info);
            }
        });
    }

    private static void downloadAndInstall(UpdateInfo info) {
        Stage progressStage = new Stage();
        progressStage.initModality(Modality.APPLICATION_MODAL);
        progressStage.setTitle("Updating Cringe Volume Player");
        progressStage.setResizable(false);

        Label label = new Label("Downloading v" + info.version + "...");
        label.setStyle("-fx-text-fill: white;");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setPrefSize(40, 40);

        Label hint = new Label("This may take a minute");
        hint.setStyle("-fx-text-fill: #a0a0b0; -fx-font-size: 11px;");

        VBox vbox = new VBox(12, label, spinner, hint);
        vbox.setAlignment(Pos.CENTER);
        vbox.setPadding(new Insets(24));
        vbox.setStyle("-fx-background-color: #1a1a2e;");

        Scene scene = new Scene(vbox, 300, 160);
        progressStage.setScene(scene);
        progressStage.show();

        Thread t = new Thread(() -> {
            try {
                Path tempFile = Files.createTempFile("cringe-update-", ".exe");

                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.NORMAL)
                        .connectTimeout(Duration.ofSeconds(30))
                        .build();

                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(info.downloadUrl))
                        .timeout(Duration.ofMinutes(10))
                        .GET()
                        .build();

                HttpResponse<InputStream> resp = client.send(req,
                        HttpResponse.BodyHandlers.ofInputStream());

                if (resp.statusCode() != 200) {
                    throw new RuntimeException("Download failed: HTTP " + resp.statusCode());
                }

                // Copy to temp file
                try (InputStream in = resp.body()) {
                    Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
                }

                long sizeMB = Files.size(tempFile) / (1024 * 1024);
                System.out.println("[Updater] Downloaded " + sizeMB + " MB to " + tempFile);

                Platform.runLater(() -> {
                    progressStage.close();
                    launchInstaller(tempFile);
                });

            } catch (Exception e) {
                System.err.println("[Updater] Download failed: " + e.getMessage());
                Platform.runLater(() -> {
                    progressStage.close();
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Update failed");
                    error.setContentText("Could not download update:\n" + e.getMessage());
                    error.showAndWait();
                });
            }
        }, "updater-download");
        t.setDaemon(true);
        t.start();
    }

    private static void launchInstaller(Path installerExe) {
        try {
            System.out.println("[Updater] Launching installer: " + installerExe);
            new ProcessBuilder("cmd", "/c", "start", "",
                    installerExe.toAbsolutePath().toString())
                    .start();

            // Give the installer a moment to start, then exit
            Platform.exit();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[Updater] Failed to launch installer: " + e.getMessage());
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Update error");
            error.setContentText("Downloaded but could not launch installer:\n"
                    + installerExe + "\n\nRun it manually.");
            error.showAndWait();
        }
    }

    /* ---------- DTO ---------- */

    private record UpdateInfo(String version, String downloadUrl, String changelog) {}
}
