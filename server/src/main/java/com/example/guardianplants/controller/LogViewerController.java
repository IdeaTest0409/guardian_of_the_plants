package com.example.guardianplants.controller;

import com.example.guardianplants.LogViewerRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/logs")
public class LogViewerController {

    private final LogViewerRepository logViewerRepository;

    public LogViewerController(LogViewerRepository logViewerRepository) {
        this.logViewerRepository = logViewerRepository;
    }

    @GetMapping("/chat")
    public List<Map<String, Object>> chatLogs(@RequestParam(defaultValue = "100") int limit) {
        return logViewerRepository.getChatLogs(Math.min(limit, 500));
    }

    @GetMapping("/app")
    public List<Map<String, Object>> appLogs(@RequestParam(defaultValue = "100") int limit) {
        return logViewerRepository.getAppLogs(Math.min(limit, 500));
    }
}
