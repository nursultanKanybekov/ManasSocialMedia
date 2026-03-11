package com.com.manasuniversityecosystem.web.controller;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.repository.gamification.UserBadgeRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.ProfileService;
import com.com.manasuniversityecosystem.service.ResumeExportService;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.web.dto.profile.ProfileUpdateRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/profile")
@RequiredArgsConstructor
@Slf4j
public class ProfileController {

    private final UserService userService;
    private final ProfileService profileService;
    private final PostService postService;
    private final ResumeExportService resumeService;
    private final UserBadgeRepository userBadgeRepo;

    // ── GET /profile/{id}  — view any user's profile ─────────
    @GetMapping("/{id}")
    public String viewProfile(@PathVariable UUID id,
                              @AuthenticationPrincipal UserDetailsImpl principal,
                              @RequestParam(defaultValue = "en") String lang,
                              Model model) {
        AppUser profileUser = userService.getById(id);
        Profile profile     = profileService.getByUserId(id);

        model.addAttribute("profileUser",  profileUser);
        model.addAttribute("profile",      profile);
        model.addAttribute("userBadges",   userBadgeRepo.findByUserIdWithBadge(id));
        model.addAttribute("postCount",    postService.countUserPosts(id));
        model.addAttribute("recentPosts",  postService.getUserPosts(id, 0, 12).getContent());
        model.addAttribute("isOwn",        principal.getId().equals(id));
        model.addAttribute("lang",         lang);
        return "profile/view";
    }

    // ── GET /profile/me/edit  — edit own profile ─────────────
    @GetMapping("/me/edit")
    public String editPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user    = userService.getById(principal.getId());
        Profile profile = profileService.getByUserId(principal.getId());

        model.addAttribute("user",                  user);
        model.addAttribute("profile",               profile);
        model.addAttribute("profileUpdateRequest",  new ProfileUpdateRequest());
        return "profile/edit";
    }

    // ── POST /profile/me/edit  — save profile update ─────────
    @PostMapping("/me/edit")
    public String updateProfile(@Valid @ModelAttribute ProfileUpdateRequest req,
                                BindingResult result,
                                @RequestParam(value = "canMentor", required = false) String canMentorParam,
                                @AuthenticationPrincipal UserDetailsImpl principal,
                                RedirectAttributes redirectAttributes,
                                Model model) {
        if (result.hasErrors()) {
            model.addAttribute("user",    userService.getById(principal.getId()));
            model.addAttribute("profile", profileService.getByUserId(principal.getId()));
            return "profile/edit";
        }
        // Checkbox: if not submitted, it means unchecked
        req.setCanMentor(canMentorParam != null && !canMentorParam.isEmpty());
        AppUser user = userService.getById(principal.getId());
        profileService.update(user, req);
        redirectAttributes.addFlashAttribute("successMsg", "profile.updated.success");
        return "redirect:/profile/" + principal.getId();
    }

    // ── POST /profile/me/avatar  — upload avatar ───────────
    @PostMapping("/me/avatar")
    public String uploadAvatar(@RequestParam("avatar") MultipartFile file,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               Model model) {
        try {
            AppUser user = userService.getById(principal.getId());
            String url   = profileService.uploadAvatar(user, file);
            model.addAttribute("avatarUrl", url);
            model.addAttribute("success",   true);

            // Rebuild UserDetailsImpl in the SecurityContext so the navbar
            // reflects the new avatar immediately (no logout/login required).
            UserDetailsImpl fresh = UserDetailsImpl.build(userService.getById(principal.getId()));
            UsernamePasswordAuthenticationToken newAuth =
                    new UsernamePasswordAuthenticationToken(fresh, null, fresh.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(newAuth);

        } catch (Exception e) {
            log.warn("Avatar upload failed for user {}: {}", principal.getId(), e.getMessage());
            model.addAttribute("error", e.getMessage() != null ? e.getMessage() : "Upload failed. Please try again.");
        }
        return "profile/fragments/avatar-section :: avatarSection";
    }

    // ── GET /profile/resume/{id}  — download PDF resume ──────
    @GetMapping("/resume/{id}")
    public ResponseEntity<byte[]> downloadResume(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "en") String lang) {
        AppUser user = userService.getById(id);
        byte[] pdf   = resumeService.generateResumePdf(user, lang);

        String filename = "resume_" + user.getFullName().replace(" ", "_") + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── POST /profile/me/password  — change password ─────────
    @PostMapping("/me/password")
    public String changePassword(@RequestParam String oldPassword,
                                 @RequestParam String newPassword,
                                 @RequestParam String confirmPassword,
                                 @AuthenticationPrincipal UserDetailsImpl principal,
                                 RedirectAttributes redirectAttributes) {
        if (!newPassword.equals(confirmPassword)) {
            redirectAttributes.addFlashAttribute("errorMsg", "profile.password.mismatch");
            return "redirect:/profile/me/edit";
        }
        try {
            AppUser user = userService.getById(principal.getId());
            userService.changePassword(user, oldPassword, newPassword);
            redirectAttributes.addFlashAttribute("successMsg", "profile.password.changed");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/profile/me/edit";
    }
}