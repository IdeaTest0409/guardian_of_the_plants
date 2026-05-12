package com.example.guardianplants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Set;

@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final Set<String> PROTECTED_LIVE_POST_PATHS = Set.of(
        "/api/live/message",
        "/api/live/settings",
        "/api/live/plant-image"
    );

    private final String username;
    private final String password;

    public AdminAuthFilter(
        @Value("${guardian.admin-auth.username:admin}") String username,
        @Value("${guardian.admin-auth.password:}") String password
    ) {
        this.username = username == null || username.isBlank() ? "admin" : username;
        this.password = password == null ? "" : password;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return password.isBlank() || !isProtected(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (isAuthorized(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        response.setHeader("WWW-Authenticate", "Basic realm=\"Guardian Plants Admin\"");
        response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Admin authentication required");
    }

    private boolean isProtected(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path.startsWith("/admin/")) return true;
        if (path.startsWith("/api/ai/")) return true;
        if ("DELETE".equalsIgnoreCase(request.getMethod()) && "/api/logs".equals(path)) return true;
        return "POST".equalsIgnoreCase(request.getMethod()) && PROTECTED_LIVE_POST_PATHS.contains(path);
    }

    private boolean isAuthorized(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Basic ")) return false;
        try {
            String decoded = new String(Base64.getDecoder().decode(header.substring("Basic ".length())), StandardCharsets.UTF_8);
            int separator = decoded.indexOf(':');
            if (separator < 0) return false;
            String actualUser = decoded.substring(0, separator);
            String actualPassword = decoded.substring(separator + 1);
            return constantTimeEquals(username, actualUser) && constantTimeEquals(password, actualPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8)
        );
    }
}
