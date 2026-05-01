package com.example.guardianplants.controller;

import com.example.guardianplants.ApiValidation;
import com.example.guardianplants.dto.TtsRequest;
import com.example.guardianplants.service.RequestTraceService;
import com.example.guardianplants.service.TtsHealthService;
import com.example.guardianplants.service.TtsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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
    private final RequestTraceService traceService;

    public TtsController(TtsService ttsService, TtsHealthService ttsHealthService, RequestTraceService traceService) {
        this.ttsService = ttsService;
        this.ttsHealthService = ttsHealthService;
        this.traceService = traceService;
    }

    @PostMapping("/synthesize")
    public ResponseEntity<?> synthesize(@RequestBody TtsRequest request) {
        var validationError = ApiValidation.validateTtsRequest(request);
        if (validationError.isPresent()) {
            return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", validationError.get()));
        }

        String traceId = traceService.generateTraceId();
        String format = normalizeFormat(request.format());
        traceService.recordReceived(traceId, "tts", "speaker=" + request.speaker() + " format=" + format + " text=" + request.text().substring(0, Math.min(50, request.text().length())));

        long startTime = System.currentTimeMillis();
        try {
            traceService.recordVoiceVoxCall(traceId, request.speaker());
            byte[] wavData = ttsService.synthesize(request.text(), request.speaker());
            long voiceVoxDuration = System.currentTimeMillis() - startTime;
            traceService.recordVoiceVoxResponse(traceId, wavData.length, voiceVoxDuration);
            byte[] responseData = wavData;
            String contentType = "audio/wav";
            String extension = "wav";
            if ("aac".equals(format)) {
                long encodeStart = System.currentTimeMillis();
                var encoded = ttsService.encodeAac(wavData);
                long encodeDuration = System.currentTimeMillis() - encodeStart;
                responseData = encoded.data();
                contentType = encoded.contentType();
                extension = encoded.extension();
                traceService.recordAudioEncode(traceId, encoded.format(), responseData.length, encodeDuration);
            }
            traceService.recordComplete(traceId, "tts");
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(responseData.length))
                .header("X-Audio-Format", format)
                .header("X-Audio-Extension", extension)
                .body(responseData);
        } catch (IllegalStateException e) {
            log.warn("TTS request when disabled");
            traceService.recordError(traceId, "tts", "voicevox_call", "TTS disabled");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("TTS synthesis failed", e);
            traceService.recordError(traceId, "tts", "voicevox_synthesis", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("error", "VoiceVOX unavailable: " + e.getMessage()));
        }
    }

    private String normalizeFormat(String format) {
        if (format == null || format.isBlank()) {
            return "wav";
        }
        return format.trim().toLowerCase();
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
