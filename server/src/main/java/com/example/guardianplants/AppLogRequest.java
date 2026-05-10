package com.example.guardianplants;

import java.util.Map;

public record AppLogRequest(
    String deviceId,
    String appVersion,
    String severity,
    String category,
    String message,
    Map<String, Object> details
) {
}
