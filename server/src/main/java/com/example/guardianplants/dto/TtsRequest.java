package com.example.guardianplants.dto;

public record TtsRequest(
    String text,
    int speaker,
    String format
) {
}
