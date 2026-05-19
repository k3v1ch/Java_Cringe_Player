package com.cringe.volume.controller;

import com.cringe.volume.dto.*;
import com.cringe.volume.service.AudioService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/audio")
public class AudioController {

    private final AudioService audioService;

    public AudioController(AudioService audioService) {
        this.audioService = audioService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestParam("file") MultipartFile file)
            throws IOException {
        String fileName = audioService.upload(file);
        return ResponseEntity.ok(new UploadResponse(fileName));
    }

    @PostMapping("/play")
    public ResponseEntity<MessageResponse> play() {
        audioService.play();
        return ResponseEntity.ok(new MessageResponse("Воспроизведение запущено"));
    }

    @PostMapping("/stop")
    public ResponseEntity<MessageResponse> stop() {
        audioService.stop();
        return ResponseEntity.ok(new MessageResponse("Воспроизведение остановлено"));
    }

    @PostMapping("/volume")
    public ResponseEntity<MessageResponse> setVolume(@Valid @RequestBody VolumeRequest request) {
        audioService.setVolume(request.getVolume());
        return ResponseEntity.ok(new MessageResponse("Громкость установлена: " + request.getVolume()));
    }

    @GetMapping("/state")
    public ResponseEntity<PlayerStateResponse> getState() {
        return ResponseEntity.ok(audioService.getState());
    }

    /* ========== Треки ========== */

    @GetMapping("/tracks")
    public ResponseEntity<List<TrackInfo>> listTracks() throws IOException {
        return ResponseEntity.ok(audioService.listTracks());
    }

    /**
     * Стрим аудио с поддержкой HTTP Range.
     * Использует ResourceRegion — Spring сам сдвинется к нужному смещению
     * и отдаст ровно запрошенный кусок (без чтения всего файла в память).
     */
    @GetMapping("/tracks/{filename}/stream")
    public ResponseEntity<ResourceRegion> streamTrack(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = audioService.getTrackResource(filename);
        long fileSize = audioService.getTrackSize(filename);
        MediaType contentType = resolveContentType(filename);

        List<HttpRange> ranges = headers.getRange();

        if (ranges.isEmpty()) {
            // Без Range — отдаём весь файл, но всё равно как ResourceRegion,
            // чтобы Spring корректно прислал Accept-Ranges и поддержал seek.
            ResourceRegion full = new ResourceRegion(resource, 0, fileSize);
            return ResponseEntity.ok()
                    .contentType(contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(fileSize)
                    .body(full);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        // ограничиваем размер chunk-а, чтобы не отдавать гигантский кусок одним ответом
        long rangeLength = Math.min(end - start + 1, 1024L * 1024L);

        ResourceRegion region = new ResourceRegion(resource, start, rangeLength);

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .contentType(contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(region);
    }

    private MediaType resolveContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".wav")) return MediaType.parseMediaType("audio/wav");
        if (lower.endsWith(".ogg")) return MediaType.parseMediaType("audio/ogg");
        return MediaType.parseMediaType("audio/mpeg");
    }
}
