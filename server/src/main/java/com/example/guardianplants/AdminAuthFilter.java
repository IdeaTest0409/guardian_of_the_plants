package com.example.guardianplants;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    public static final String SESSION_AUTH_KEY = "adminAuthenticated";

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
        if (password.isBlank()) return true;
        String path = request.getRequestURI();
        if (!path.startsWith("/admin/")) return true;
        if (path.equals("/admin/login.html")) return true;
        if (path.equals("/admin/login")) return true;
        if (path.equals("/admin/logout")) return true;
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && Boolean.TRUE.equals(session.getAttribute(SESSION_AUTH_KEY))) {
            filterChain.doFilter(request, response);
            return;
        }

        String loginPath = request.getContextPath() + "/admin/login.html";
        response.sendRedirect(loginPath);
    }

    public boolean checkCredentials(String user, String pass) {
        if (user == null || pass == null) return false;
        return user.equals(this.username) && pass.equals(this.password);
    }
}
