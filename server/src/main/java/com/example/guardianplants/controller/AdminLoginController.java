package com.example.guardianplants.controller;

import com.example.guardianplants.AdminAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AdminLoginController {

    private final AdminAuthFilter adminAuthFilter;

    public AdminLoginController(AdminAuthFilter adminAuthFilter) {
        this.adminAuthFilter = adminAuthFilter;
    }

    @PostMapping("/admin/login")
    public String login(
        @RequestParam String username,
        @RequestParam String password,
        HttpServletRequest request
    ) {
        if (adminAuthFilter.checkCredentials(username, password)) {
            HttpSession session = request.getSession(true);
            session.setAttribute(AdminAuthFilter.SESSION_AUTH_KEY, Boolean.TRUE);
            return "redirect:/admin/";
        }
        return "redirect:/admin/login.html?error=1";
    }

    @GetMapping("/admin/logout")
    public String logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return "redirect:/admin/login.html";
    }
}
