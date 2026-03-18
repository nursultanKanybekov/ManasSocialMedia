package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.service.ObisLoginService;
import com.com.manasuniversityecosystem.service.UniversityAuthService;
import com.com.manasuniversityecosystem.service.UniversityAuthService.UniversityAuthException;
import com.com.manasuniversityecosystem.web.dto.auth.ObisStudentInfo;
import com.com.manasuniversityecosystem.web.dto.auth.UniversityLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

/**
 * ObisLoginController
 *
 * Handles login via the Manas OBIS university portal.
 *
 * GET  /auth/obis-login   → show form
 * POST /auth/obis-login   → authenticate via OBIS, create/update user, log in
 */
@Controller
@RequestMapping("/auth/obis-login")
@RequiredArgsConstructor
@Slf4j
public class ObisLoginController {

    private final UniversityAuthService universityAuthService;
    private final ObisLoginService      obisLoginService;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("loginRequest", new UniversityLoginRequest());
        return "auth/obis-login";
    }

    @PostMapping
    public String login(@Valid @ModelAttribute("loginRequest") UniversityLoginRequest req,
                        BindingResult bindingResult,
                        HttpServletRequest  request,
                        Model model) {

        if (bindingResult.hasErrors()) {
            return "auth/obis-login";
        }

        try {
            // Authenticate against OBIS and scrape verified student data
            ObisStudentInfo info = universityAuthService.authenticateAndFetch(
                    req.getUsername(), req.getPassword());

            // Create/update user in DB and log them into Spring Security session
            AppUser user = obisLoginService.loginOrRegister(info, request);

            log.info("OBIS login success: user={} name='{}' year={}",
                    user.getId(), user.getFullName(), info.getStudyYear());

            return "redirect:/main";

        } catch (UniversityAuthException e) {
            String msg = e.getMessage().contains("unreachable")
                    ? "⚠️ OBIS portal is currently unavailable. Please try again later."
                    : "❌ Invalid OBIS username or password. Please check your credentials.";
            model.addAttribute("errorMsg", msg);
            model.addAttribute("loginRequest", req);
            return "auth/obis-login";
        }
    }
}