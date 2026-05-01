package com.example.guardianplants.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "guardian.retention")
public class RetentionConfig {
    private boolean enabled = true;
    private int appLogsDays = 30;
    private int chatHistoryDays = 90;
    private int pruneOnAppStartEvery = 100;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getAppLogsDays() {
        return appLogsDays;
    }

    public void setAppLogsDays(int appLogsDays) {
        this.appLogsDays = appLogsDays;
    }

    public int getChatHistoryDays() {
        return chatHistoryDays;
    }

    public void setChatHistoryDays(int chatHistoryDays) {
        this.chatHistoryDays = chatHistoryDays;
    }

    public int getPruneOnAppStartEvery() {
        return pruneOnAppStartEvery;
    }

    public void setPruneOnAppStartEvery(int pruneOnAppStartEvery) {
        this.pruneOnAppStartEvery = pruneOnAppStartEvery;
    }
}
