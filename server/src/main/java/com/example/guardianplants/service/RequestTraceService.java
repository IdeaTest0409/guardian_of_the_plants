package com.example.guardianplants.service;

import com.example.guardianplants.RequestTraceRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RequestTraceService {

    private final RequestTraceRepository traceRepository;

    public RequestTraceService(RequestTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    public String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    public void recordReceived(String traceId, String requestType, String detail) {
        traceRepository.insertStep(traceId, requestType, "received", "ok", detail, null);
    }

    public void recordAiCall(String traceId, String model) {
        traceRepository.insertStep(traceId, "chat", "ai_call", "ok", "model=" + model, null);
    }

    public void recordAiResponse(String traceId, int chars, long durationMs) {
        traceRepository.insertStep(traceId, "chat", "ai_response", "ok", chars + " chars", (int) durationMs);
    }

    public void recordDbSaved(String traceId, String role) {
        traceRepository.insertStep(traceId, "chat", "db_saved", "ok", "role=" + role, null);
    }

    public void recordVoiceVoxCall(String traceId, int speaker) {
        traceRepository.insertStep(traceId, "tts", "voicevox_call", "ok", "speaker=" + speaker, null);
    }

    public void recordVoiceVoxResponse(String traceId, int sizeBytes, long durationMs) {
        traceRepository.insertStep(traceId, "tts", "voicevox_response", "ok", sizeBytes + " bytes", (int) durationMs);
    }

    public void recordComplete(String traceId, String requestType) {
        traceRepository.insertStep(traceId, requestType, "complete", "ok", null, null);
    }

    public void recordError(String traceId, String requestType, String step, String error) {
        String detail = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        traceRepository.insertStep(traceId, requestType, "error", "error", step + ": " + detail, null);
    }
}
