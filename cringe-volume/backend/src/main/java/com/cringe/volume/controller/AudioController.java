package com.cringe.volume.controller;

import com.cringe.volume.dto.*;
import com.cringe.volume.service.AudioService;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
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

    @GetMapping("/tracks/{filename}/stream")
    public ResponseEntity<Resource> streamTrack(
            @PathVariable String filename,
            @RequestHeader HttpHeaders headers) throws IOException {

        Resource resource = audioService.getTrackResource(filename);
        long fileSize = audioService.getTrackSize(filename);

        String contentType = filename.toLowerCase().endsWith(".wav")
                ? "audio/wav" : "audio/mpeg";

        List<HttpRange> ranges = headers.getRange();

        if (ranges.isEmpty()) {
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, contentType)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .contentLength(fileSize)
                    .body(resource);
        }

        HttpRange range = ranges.get(0);
        long start = range.getRangeStart(fileSize);
        long end = range.getRangeEnd(fileSize);
        long rangeLength = end - start + 1;

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT)
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header(HttpHeaders.CONTENT_RANGE,
                        "bytes " + start + "-" + end + "/" + fileSize)
                .contentLength(rangeLength)
                .body(resource);
    }
}
