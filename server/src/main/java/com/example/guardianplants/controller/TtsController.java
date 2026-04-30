package com.example.guardianplants.controller;

import com.example.guardianplants.dto.TtsRequest;
import com.example.guardianplants.service.TtsHealthService;
import com.example.guardianplants.service.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/tts")
public class TtsController {

    private static final Logger log = LoggerFactory.getLogger(TtsController.class);

    private final TtsService ttsService;
    private final TtsHealthService ttsHealthService;

    public TtsController(TtsService ttsService, TtsHealthService ttsHealthService) {
        this.ttsService = ttsService;
        this.ttsHealthService = ttsHealthService;
    }

    @PostMapping(value = "/synthesize", produces = "audio/wav")
    public ResponseEntity<?> synthesize(@RequestBody TtsRequest request) {
        if (request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Text is required"));
        }

        try {
            byte[] wavData = ttsService.synthesize(request.text(), request.speaker());
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "audio/wav")
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(wavData.length))
                .body(wavData);
        } catch (IllegalStateException e) {
            log.warn("TTS request when disabled");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("TTS synthesis failed", e);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "VoiceVOX unavailable: " + e.getMessage()));
        }
    }

    @GetMapping("/speakers")
    public ResponseEntity<?> getSpeakers() {
        try {
            String speakersJson = ttsService.getSpeakers();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .body(speakersJson);
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", "VoiceVOX unavailable: " + e.getMessage()));
        }
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        boolean voiceVoxHealthy = ttsHealthService.checkVoiceVoxHealth();
        Map<String, Object> result = Map.of(
            "enabled", true,
            "voicevox", voiceVoxHealthy ? "ok" : "unreachable",
            "voicevoxHealthy", voiceVoxHealthy
        );
        return ResponseEntity.ok(result);
    }
}
