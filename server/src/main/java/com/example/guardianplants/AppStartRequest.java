package com.example.guardianplants;

import java.util.Map;

public record AppStartRequest(
    String deviceId,
    String appVersion,
    Map<String, String> details
) {
}
