package com.example.guardianplants.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "guardian.rate-limit")
public class RateLimitConfig {
    private boolean enabled = true;
    private int appStartPerMinute = 30;
    private int chatPerMinute = 20;
    private int ttsPerMinute = 30;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAppStartPerMinute() {
        return appStartPerMinute;
    }

    public void setAppStartPerMinute(int appStartPerMinute) {
        this.appStartPerMinute = appStartPerMinute;
    }

    public int getChatPerMinute() {
        return chatPerMinute;
    }

    public void setChatPerMinute(int chatPerMinute) {
        this.chatPerMinute = chatPerMinute;
    }

    public int getTtsPerMinute() {
        return ttsPerMinute;
    }

    public void setTtsPerMinute(int ttsPerMinute) {
        this.ttsPerMinute = ttsPerMinute;
    }
}
