package com.example.guardianplants.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record ServerMessage(
    String role,
    Object content
) {
    public String contentAsString() {
        if (content instanceof String s) return s;
        if (content instanceof JsonNode n) return n.asText();
        return content != null ? content.toString() : "";
    }
}
