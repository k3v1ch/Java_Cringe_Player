package com.cringe.volume.service;

import com.cringe.volume.dto.PlayerStateResponse;
import com.cringe.volume.dto.TrackInfo;
import com.cringe.volume.model.PlayerState;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

@Service
public class AudioService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp3"
    );
    private static final Set<String> AUDIO_EXTENSIONS = Set.of(
            ".mp3", ".wav"
    );

    @Value("${music.dir:./music}")
    private String musicDirPath;

    private Path musicDir;
    private final PlayerState state;

    public AudioService(PlayerState state) {
        this.state = state;
    }

    @PostConstruct
    void init() throws IOException {
        musicDir = Paths.get(musicDirPath).toAbsolutePath().normalize();
        Files.createDirectories(musicDir);
    }

    /* ---------- загрузка ---------- */

    public String upload(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Неподдерживаемый формат файла. Допустимы: mp3, wav");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "audio_" + System.currentTimeMillis();
        }

        Path target = musicDir.resolve(originalName).normalize();
        if (!target.startsWith(musicDir)) {
            throw new IllegalArgumentException("Недопустимое имя файла");
        }

        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        state.setFileName(originalName);
        state.setPlaying(false);
        return originalName;
    }

    /* ---------- список треков ---------- */

    public List<TrackInfo> listTracks() throws IOException {
        if (!Files.isDirectory(musicDir)) return List.of();

        try (Stream<Path> files = Files.list(musicDir)) {
            return files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return AUDIO_EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .map(p -> {
                        try {
                            return new TrackInfo(p.getFileName().toString(), Files.size(p));
                        } catch (IOException e) {
                            return new TrackInfo(p.getFileName().toString(), 0);
                        }
                    })
                    .sorted((a, b) -> a.getFilename().compareToIgnoreCase(b.getFilename()))
                    .toList();
        }
    }

    /* ---------- стриминг ---------- */

    public Resource getTrackResource(String filename) throws IOException {
        Path path = musicDir.resolve(filename).normalize();
        if (!path.startsWith(musicDir)) {
            throw new IllegalArgumentException("Недопустимое имя файла");
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Файл не найден: " + filename);
        }
        return new UrlResource(path.toUri());
    }

    public long getTrackSize(String filename) throws IOException {
        Path path = musicDir.resolve(filename).normalize();
        return Files.size(path);
    }

    /* ---------- управление ---------- */

    public void play() {
        if (state.getFileName() == null) {
            throw new IllegalStateException("Сначала загрузите аудиофайл");
        }
        state.setPlaying(true);
    }

    public void stop() {
        state.setPlaying(false);
    }

    public void setVolume(int volume) {
        state.setVolume(volume);
    }

    public PlayerStateResponse getState() {
        return new PlayerStateResponse(
                state.getFileName(), state.isPlaying(), state.getVolume()
        );
    }
}
