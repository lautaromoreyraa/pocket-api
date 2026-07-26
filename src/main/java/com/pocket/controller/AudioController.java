package com.pocket.controller;

import com.pocket.dto.audio.AudioResponse;
import com.pocket.service.audio.AudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/audio")
@RequiredArgsConstructor
public class AudioController {

    private final AudioService audioService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AudioResponse> procesar(@RequestParam("audio") MultipartFile audio) {
        return ResponseEntity.ok(audioService.procesar(audio));
    }
}
