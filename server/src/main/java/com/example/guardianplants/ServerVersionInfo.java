package com.example.guardianplants;

import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ServerVersionInfo {
    private final Instant startedAt = Instant.now();
    private final String appName;
    private final String version;
    private final String commit;
    private final String buildTime;
    private final String environment;

    public ServerVersionInfo(
        @Value("${spring.application.name:guardian-plants-server}") String appName,
        @Value("${APP_VERSION:0.0.1-SNAPSHOT}") String version,
        @Value("${GIT_COMMIT:unknown}") String commit,
        @Value("${BUILD_TIME:unknown}") String buildTime,
        @Value("${APP_ENV:local}") String environment
    ) {
        this.appName = appName;
        this.version = version;
        this.commit = commit;
        this.buildTime = buildTime;
        this.environment = environment;
    }

    public Map<String, Object> asMap() {
        return Map.of(
            "app", appName,
            "version", version,
            "commit", commit,
            "buildTime", buildTime,
            "startedAt", startedAt.toString(),
            "environment", environment
        );
    }
}
