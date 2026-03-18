package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.service.ObisLoginService;
import com.com.manasuniversityecosystem.service.UniversityAuthService;
import com.com.manasuniversityecosystem.service.UniversityAuthService.UniversityAuthException;
import com.com.manasuniversityecosystem.web.dto.auth.ObisStudentInfo;
import com.com.manasuniversityecosystem.web.dto.auth.UniversityLoginRequest;
import com.com.manasuniversityecosystem.config.SecurityConfig;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
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

    private final UniversityAuthService    universityAuthService;
    private final ObisLoginService         obisLoginService;
    private final TokenBasedRememberMeServices rememberMeServices;

    @GetMapping
    public String showForm(Model model) {
        model.addAttribute("loginRequest", new UniversityLoginRequest());
        return "auth/obis-login";
    }

    @PostMapping
    public String login(@Valid @ModelAttribute("loginRequest") UniversityLoginRequest req,
                        BindingResult bindingResult,
                        HttpServletRequest  request,
                        HttpServletResponse response,
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

            // Set remember-me cookie if user ticked the checkbox
            String rememberParam = request.getParameter("remember-me");
            if ("on".equals(rememberParam) || "true".equals(rememberParam)) {
                // Build a remember-me cookie manually — same format as Spring Security
                // but we call loginSuccess() on our shared RememberMeServices bean.
                // We need to rebuild an Authentication object for it.
                org.springframework.security.core.Authentication auth =
                        org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication();
                if (auth != null) {
                    rememberMeServices.loginSuccess(request, response, auth);
                    log.debug("OBIS remember-me cookie set for user={}", user.getId());
                }
            }

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