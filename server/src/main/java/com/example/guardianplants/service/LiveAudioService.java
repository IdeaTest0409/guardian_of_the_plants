package com.example.guardianplants.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class LiveAudioService {

    private static final int MAX_ITEMS = 100;

    private final Map<String, LiveAudio> audioById = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, LiveAudio> eldest) {
            return size() > MAX_ITEMS;
        }
    };

    public synchronized String put(byte[] data, String contentType, String extension) {
        String id = UUID.randomUUID().toString();
        audioById.put(id, new LiveAudio(data, contentType, extension, Instant.now()));
        return id;
    }

    public synchronized LiveAudio get(String id) {
        return audioById.get(id);
    }

    public record LiveAudio(byte[] data, String contentType, String extension, Instant createdAt) {
    }
}
