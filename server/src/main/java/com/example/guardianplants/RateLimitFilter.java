package com.example.guardianplants;

import com.example.guardianplants.config.RateLimitConfig;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_MILLIS = 60_000L;

    private final RateLimitConfig config;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimitFilter(RateLimitConfig config) {
        this.config = config;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        int limit = limitFor(request.getRequestURI(), request.getMethod());
        if (!config.isEnabled() || limit <= 0) {
            chain.doFilter(request, response);
            return;
        }

        String key = request.getRemoteAddr() + ":" + request.getMethod() + ":" + request.getRequestURI();
        if (!allow(key, limit)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"rate limit exceeded\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private int limitFor(String path, String method) {
        if (!"POST".equalsIgnoreCase(method)) {
            return 0;
        }
        if ("/api/app-start".equals(path)) {
            return config.getAppStartPerMinute();
        }
        if ("/api/chat".equals(path)) {
            return config.getChatPerMinute();
        }
        if ("/api/tts/synthesize".equals(path)) {
            return config.getTtsPerMinute();
        }
        return 0;
    }

    private boolean allow(String key, int limit) {
        long now = Instant.now().toEpochMilli();
        Counter counter = counters.compute(key, (ignored, current) -> {
            if (current == null || now - current.windowStartMillis >= WINDOW_MILLIS) {
                return new Counter(now);
            }
            current.count.incrementAndGet();
            return current;
        });
        return counter.count.get() <= limit;
    }

    private static final class Counter {
        final long windowStartMillis;
        final AtomicInteger count = new AtomicInteger(1);

        Counter(long windowStartMillis) {
            this.windowStartMillis = windowStartMillis;
        }
    }
}
