package com.example.guardianplants.service;

import com.example.guardianplants.RequestTraceRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class RequestTraceService {

    private static final int PRUNE_EVERY_STEPS = 200;
    private static final int RETENTION_DAYS = 7;
    private static final int MAX_ROWS = 10_000;

    private final RequestTraceRepository traceRepository;
    private int stepsSincePrune = 0;

    public RequestTraceService(RequestTraceRepository traceRepository) {
        this.traceRepository = traceRepository;
    }

    public String generateTraceId() {
        return UUID.randomUUID().toString().substring(0, 12);
    }

    public void recordReceived(String traceId, String requestType, String detail) {
        insertStep(traceId, requestType, "received", "ok", detail, null);
    }

    public void recordAiCall(String traceId, String model) {
        insertStep(traceId, "chat", "ai_call", "ok", "model=" + model, null);
    }

    public void recordAiResponse(String traceId, int chars, long durationMs) {
        insertStep(traceId, "chat", "ai_response", "ok", chars + " chars", (int) durationMs);
    }

    public void recordDbSaved(String traceId, String role) {
        insertStep(traceId, "chat", "db_saved", "ok", "role=" + role, null);
    }

    public void recordVoiceVoxCall(String traceId, int speaker) {
        insertStep(traceId, "tts", "voicevox_call", "ok", "speaker=" + speaker, null);
    }

    public void recordVoiceVoxResponse(String traceId, int sizeBytes, long durationMs) {
        insertStep(traceId, "tts", "voicevox_response", "ok", sizeBytes + " bytes", (int) durationMs);
    }

    public void recordComplete(String traceId, String requestType) {
        insertStep(traceId, requestType, "complete", "ok", null, null);
    }

    public void recordError(String traceId, String requestType, String step, String error) {
        String detail = error != null && error.length() > 500 ? error.substring(0, 500) : error;
        insertStep(traceId, requestType, "error", "error", step + ": " + detail, null);
    }

    private synchronized void insertStep(String traceId, String requestType, String step, String status, String detail, Integer durationMs) {
        traceRepository.insertStep(traceId, requestType, step, status, detail, durationMs);
        stepsSincePrune++;
        if (stepsSincePrune >= PRUNE_EVERY_STEPS) {
            stepsSincePrune = 0;
            traceRepository.pruneOldTraces(RETENTION_DAYS, MAX_ROWS);
        }
    }
}
