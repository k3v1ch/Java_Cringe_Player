package com.cringe.volume.service;

import com.cringe.volume.dto.PlayerStateResponse;
import com.cringe.volume.dto.TrackInfo;
import com.cringe.volume.exception.AppException;
import com.cringe.volume.exception.ErrorCode;
import com.cringe.volume.model.PlayerState;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AudioService.class);

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "audio/mpeg", "audio/wav", "audio/x-wav", "audio/mp3",
            "application/octet-stream"   // Windows иногда не определяет тип mp3
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

        // Диагностика на старте — пользователь сразу видит куда смотрит бэкенд
        log.info("AudioService: MUSIC_DIR = {}", musicDir);
        log.info("AudioService: existsReadable = {}/{}",
                Files.exists(musicDir), Files.isReadable(musicDir));
        try (Stream<Path> files = Files.list(musicDir)) {
            long total = files.count();
            log.info("AudioService: всего файлов в директории: {}", total);
        }
        long audioCount = listTracks().size();
        log.info("AudioService: аудио-треков (.mp3/.wav): {}", audioCount);
    }

    /* ---------- загрузка ---------- */

    public String upload(MultipartFile file) {
        if (file.isEmpty()) {
            throw new AppException(ErrorCode.AUDIO_FILE_EMPTY);
        }
        String contentType = file.getContentType();
        String originalName = file.getOriginalFilename();
        String lowerName = originalName != null ? originalName.toLowerCase() : "";

        // Принимаем либо по content-type, либо по расширению (Win браузеры
        // часто шлют application/octet-stream для .mp3)
        boolean typeOk = contentType != null && ALLOWED_TYPES.contains(contentType);
        boolean extOk = AUDIO_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!typeOk && !extOk) {
            log.warn("{}: contentType={}, filename={}",
                    ErrorCode.AUDIO_FORMAT_UNSUPPORTED.getCode(), contentType, originalName);
            throw new AppException(ErrorCode.AUDIO_FORMAT_UNSUPPORTED,
                    "contentType=" + contentType + ", filename=" + originalName);
        }

        if (originalName == null || originalName.isBlank()) {
            originalName = "audio_" + System.currentTimeMillis();
        }

        Path target = musicDir.resolve(originalName).normalize();
        if (!target.startsWith(musicDir)) {
            log.warn("{}: попытка path traversal: {}",
                    ErrorCode.AUDIO_PATH_INVALID.getCode(), originalName);
            throw new AppException(ErrorCode.AUDIO_PATH_INVALID, originalName);
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.error("{}: не удалось сохранить {}",
                    ErrorCode.AUDIO_UPLOAD_FAILED.getCode(), originalName, e);
            throw new AppException(ErrorCode.AUDIO_UPLOAD_FAILED, originalName, e);
        }

        state.setFileName(originalName);
        state.setPlaying(false);
        log.info("AUDIO: загружен файл {} ({}b)", originalName, file.getSize());
        return originalName;
    }

    /* ---------- список треков ---------- */

    public List<TrackInfo> listTracks() {
        if (!Files.isDirectory(musicDir)) {
            log.warn("{}: {} не директория",
                    ErrorCode.AUDIO_DIR_NOT_FOUND.getCode(), musicDir);
            throw new AppException(ErrorCode.AUDIO_DIR_NOT_FOUND, musicDir.toString());
        }

        try (Stream<Path> files = Files.list(musicDir)) {
            List<TrackInfo> tracks = files
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString().toLowerCase();
                        return AUDIO_EXTENSIONS.stream().anyMatch(name::endsWith);
                    })
                    .map(p -> {
                        try {
                            return new TrackInfo(p.getFileName().toString(), Files.size(p));
                        } catch (IOException e) {
                            log.warn("AUDIO: не удалось прочитать размер {}: {}",
                                    p, e.getMessage());
                            return new TrackInfo(p.getFileName().toString(), 0);
                        }
                    })
                    .sorted((a, b) -> a.getFilename().compareToIgnoreCase(b.getFilename()))
                    .toList();
            log.debug("AUDIO: listTracks() → {} треков", tracks.size());
            return tracks;
        } catch (IOException e) {
            log.error("{}: ошибка чтения {}",
                    ErrorCode.AUDIO_DIR_NOT_FOUND.getCode(), musicDir, e);
            throw new AppException(ErrorCode.AUDIO_DIR_NOT_FOUND, musicDir.toString(), e);
        }
    }

    /* ---------- стриминг ---------- */

    public Resource getTrackResource(String filename) {
        Path path = musicDir.resolve(filename).normalize();
        if (!path.startsWith(musicDir)) {
            log.warn("{}: path traversal в getTrackResource: {}",
                    ErrorCode.AUDIO_PATH_INVALID.getCode(), filename);
            throw new AppException(ErrorCode.AUDIO_PATH_INVALID, filename);
        }
        if (!Files.exists(path) || !Files.isRegularFile(path)) {
            throw new AppException(ErrorCode.AUDIO_FILE_NOT_FOUND, filename);
        }
        try {
            return new UrlResource(path.toUri());
        } catch (Exception e) {
            log.error("{}: ошибка построения UrlResource для {}",
                    ErrorCode.AUDIO_STREAM_FAILED.getCode(), filename, e);
            throw new AppException(ErrorCode.AUDIO_STREAM_FAILED, filename, e);
        }
    }

    public long getTrackSize(String filename) {
        Path path = musicDir.resolve(filename).normalize();
        try {
            return Files.size(path);
        } catch (IOException e) {
            log.error("{}: не получилось узнать размер {}",
                    ErrorCode.AUDIO_STREAM_FAILED.getCode(), filename, e);
            throw new AppException(ErrorCode.AUDIO_STREAM_FAILED, filename, e);
        }
    }

    /* ---------- управление (state на сервере) ---------- */

    public void play() {
        if (state.getFileName() == null) {
            throw new AppException(ErrorCode.PLAYER_NO_TRACK);
        }
        state.setPlaying(true);
    }

    public void stop() {
        state.setPlaying(false);
    }

    public void setVolume(int volume) {
        if (volume < 0 || volume > 100) {
            throw new AppException(ErrorCode.VOLUME_INVALID, "получено: " + volume);
        }
        state.setVolume(volume);
        log.info("AUDIO: громкость установлена → {}%", volume);
    }

    public PlayerStateResponse getState() {
        return new PlayerStateResponse(
                state.getFileName(), state.isPlaying(), state.getVolume()
        );
    }
}
