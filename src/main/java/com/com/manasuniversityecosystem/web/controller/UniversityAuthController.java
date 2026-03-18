package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UniversityAuthService;
import com.com.manasuniversityecosystem.service.UniversityAuthService.UniversityAuthException;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.web.dto.auth.UniversityLoginRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * UniversityAuthController
 *
 * Handles proxy-authentication against the Manas OBIS university portal.
 * Students can verify their university credentials to unlock verified status.
 *
 * GET  /university-verify        → show form
 * POST /university-verify        → submit and check credentials
 */
@Controller
@RequestMapping("/university-verify")
@RequiredArgsConstructor
@Slf4j
public class UniversityAuthController {

    private final UniversityAuthService universityAuthService;
    private final UserService           userService;

    // ── GET: show verification form ────────────────────────────────────────────
    @GetMapping
    public String showForm(@AuthenticationPrincipal UserDetailsImpl principal,
                           Model model) {
        AppUser user = userService.getById(principal.getId());

        model.addAttribute("loginRequest", new UniversityLoginRequest());
        model.addAttribute("currentUser",  user);
        model.addAttribute("alreadyVerified", user.isUniversityVerified());
        return "auth/university-verify";
    }

    // ── POST: verify credentials ───────────────────────────────────────────────
    @PostMapping
    public String verify(@Valid @ModelAttribute("loginRequest") UniversityLoginRequest req,
                         BindingResult bindingResult,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model,
                         RedirectAttributes redirectAttributes) {

        AppUser user = userService.getById(principal.getId());

        if (bindingResult.hasErrors()) {
            model.addAttribute("currentUser", user);
            return "auth/university-verify";
        }

        try {
            boolean verified = universityAuthService.authenticate(
                    req.getUsername(), req.getPassword());

            if (verified) {
                // Mark the user as university-verified in our DB
                userService.markUniversityVerified(user);
                redirectAttributes.addFlashAttribute("successMsg",
                        "✅ University credentials verified successfully!");
                return "redirect:/profile/me/edit";
            } else {
                model.addAttribute("errorMsg",
                        "❌ Invalid university credentials. Please check your OBIS username and password.");
                model.addAttribute("currentUser", user);
                return "auth/university-verify";
            }

        } catch (UniversityAuthException e) {
            log.warn("University auth portal error for user {}: {}", principal.getId(), e.getMessage());
            model.addAttribute("errorMsg",
                    "⚠️ University portal is currently unavailable. Please try again later.");
            model.addAttribute("currentUser", user);
            return "auth/university-verify";
        }
    }
}