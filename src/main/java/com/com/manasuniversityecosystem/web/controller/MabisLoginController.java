package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.service.MabisAuthService;
import com.com.manasuniversityecosystem.service.MabisAuthService.MabisAuthException;
import com.com.manasuniversityecosystem.service.MabisLoginService;
import com.com.manasuniversityecosystem.web.dto.auth.MabisTeacherInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * MabisLoginController
 *
 * Handles teacher login via the Manas MABIS portal (info.manas.edu.kg).
 *
 * POST /auth/mabis-login
 *   → authenticates against MABIS
 *   → creates/updates user with TEACHER role
 *   → redirects to /auth/mabis-faculty (shows department confirmation page)
 *   → then user clicks through to /main
 *
 * The form for this endpoint is embedded inside obis-login.html (teacher tab)
 * so there is no separate GET handler needed — the form lives on the OBIS/MABIS
 * combined login page.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class MabisLoginController {

    private final MabisAuthService  mabisAuthService;
    private final MabisLoginService mabisLoginService;

    /**
     * POST /auth/mabis-login
     * Submitted from the teacher tab in obis-login.html.
     */
    @PostMapping("/auth/mabis-login")
    public String login(@RequestParam String username,
                        @RequestParam String password,
                        HttpServletRequest request,
                        RedirectAttributes redirectAttributes,
                        Model model) {

        try {
            // Step 1: Authenticate against MABIS and scrape teacher info
            MabisTeacherInfo info = mabisAuthService.authenticateAndFetch(username, password);

            // Step 2: Create/update TEACHER user and establish Spring Security session
            AppUser user = mabisLoginService.loginOrRegister(info, request);

            log.info("MABIS login success: user={} name='{}' dept='{}'",
                    user.getId(), user.getFullName(), info.getDepartmentName());

            // Step 3: Go directly to dashboard (no intermediate confirmation page)
            return "redirect:/main";

        } catch (MabisAuthException e) {
            String msg = e.getMessage().contains("unreachable")
                    ? "⚠️ MABIS portal is currently unavailable. Please try again later."
                    : "❌ Invalid MABIS username or password. Please check your credentials.";
            // Add as flash attribute so obis-login.html can show it in the teacher tab
            redirectAttributes.addFlashAttribute("mabisErrorMsg", msg);
            return "redirect:/auth/obis-login";
        }
    }

    /**
     * GET /auth/mabis-faculty
     * Shown after a successful MABIS login.
     * Displays the teacher's detected name, employee number, and faculty/department.
     * The teacher then clicks "Continue to Dashboard".
     */
    @GetMapping("/auth/mabis-faculty")
    public String facultyConfirmation(@AuthenticationPrincipal UserDetails userDetails,
                                      Model model) {
        // If somehow accessed without login, redirect to login
        if (userDetails == null) {
            return "redirect:/auth/login";
        }
        // The model attributes come from the currently authenticated session user
        // We pass them through the auth principal
        return "auth/mabis-faculty";
    }
}