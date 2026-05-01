package com.example.guardianplants.service;

import com.example.guardianplants.LogRetentionRepository;
import com.example.guardianplants.config.RetentionConfig;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LogRetentionService {
    private static final Logger log = LoggerFactory.getLogger(LogRetentionService.class);

    private final LogRetentionRepository repository;
    private final RetentionConfig config;
    private final AtomicInteger appStartCount = new AtomicInteger();

    public LogRetentionService(LogRetentionRepository repository, RetentionConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public void pruneAfterAppStartIfNeeded() {
        if (!config.isEnabled() || config.getPruneOnAppStartEvery() <= 0) {
            return;
        }
        int count = appStartCount.incrementAndGet();
        if (count % config.getPruneOnAppStartEvery() != 0) {
            return;
        }
        int appLogsDeleted = repository.pruneAppLogs(config.getAppLogsDays());
        int chatDeleted = repository.pruneChatHistories(config.getChatHistoryDays());
        log.info("Retention prune completed: appLogsDeleted={} chatHistoriesDeleted={}", appLogsDeleted, chatDeleted);
    }
}
