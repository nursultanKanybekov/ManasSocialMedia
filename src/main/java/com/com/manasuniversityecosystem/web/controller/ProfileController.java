package com.com.manasuniversityecosystem.web.controller;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.service.career.JobService;
import com.com.manasuniversityecosystem.repository.career.JobApplicationRepository;
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
    private final FacultyRepository facultyRepo;
    private final JobService jobService;
    private final JobApplicationRepository jobApplicationRepo;

    // ── GET /profile/{id}  — view any user's profile ─────────
    @GetMapping("/{id}")
    public String viewProfile(@PathVariable String id,
                              @AuthenticationPrincipal UserDetailsImpl principal,
                              @RequestParam(defaultValue = "en") String lang,
                              Model model) {
        // Guard: reject non-UUID paths (e.g. Telegram @handle clicked as relative href)
        UUID userId;
        try {
            userId = UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            log.warn("Non-UUID profile path: /profile/{} — redirecting", id);
            return "redirect:/";
        }
        AppUser profileUser = userService.getById(userId);
        Profile profile     = profileService.getByUserId(userId);

        // CV visible to: self, or employer/admin who has a job this user applied to
        boolean isOwn = principal.getId().equals(userId);
        boolean viewerIsEmployer = false;
        try {
            com.com.manasuniversityecosystem.domain.entity.AppUser viewer = userService.getById(principal.getId());
            viewerIsEmployer = viewer.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.EMPLOYER
                    || viewer.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.ADMIN
                    || viewer.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.SUPER_ADMIN;
        } catch (Exception ignored) {}
        boolean canViewCv = isOwn || (viewerIsEmployer &&
                jobApplicationRepo.existsByApplicantIdAndJobPosterId(userId, principal.getId()));

        model.addAttribute("profileUser",  profileUser);
        model.addAttribute("profile",      profile);
        model.addAttribute("userBadges",   userBadgeRepo.findByUserIdWithBadge(userId));
        model.addAttribute("postCount",    postService.countUserPosts(userId));
        model.addAttribute("recentPosts",  postService.getUserPosts(userId, 0, 12).getContent());
        model.addAttribute("isOwn",        isOwn);
        model.addAttribute("canViewCv",    canViewCv);
        model.addAttribute("lang",         lang);
        return "profile/view";
    }

    // ── GET /profile/me/edit  — edit own profile ─────────────
    @GetMapping("/me/edit")
    public String editPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user    = userService.getById(principal.getId());
        Profile profile = profileService.getByUserId(principal.getId());

        // Pre-populate DTO so th:field bindings show existing values
        com.com.manasuniversityecosystem.web.dto.profile.ProfileUpdateRequest prefilled =
                new com.com.manasuniversityecosystem.web.dto.profile.ProfileUpdateRequest();
        prefilled.setFullName(user.getFullName());
        prefilled.setEmail(user.getEmail());
        prefilled.setGraduationYear(user.getGraduationYear());
        if (user.getFaculty() != null) prefilled.setFacultyId(user.getFaculty().getId());
        prefilled.setBio(profile.getBio());
        prefilled.setHeadline(profile.getHeadline());
        prefilled.setCurrentJobTitle(profile.getCurrentJobTitle());
        prefilled.setCurrentCompany(profile.getCurrentCompany());
        prefilled.setPhone(profile.getPhone());
        prefilled.setLocation(profile.getLocation());
        prefilled.setWebsite(profile.getWebsite());
        prefilled.setDateOfBirth(profile.getDateOfBirth());
        prefilled.setNationality(profile.getNationality());

        model.addAttribute("user",                  user);
        model.addAttribute("profile",               profile);
        model.addAttribute("faculties",             facultyRepo.findAllByOrderByNameAsc());
        model.addAttribute("profileUpdateRequest",  prefilled);
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
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam(defaultValue = "US") String standard) {
        AppUser user = userService.getById(id);
        byte[] pdf   = resumeService.generateResumePdf(user, lang, standard);

        String filename = "resume_" + user.getFullName().replace(" ", "_") + "_" + standard + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    // ── GET /profile/{id}/posts  — user's post feed (HTMX paginated) ─
    @GetMapping("/{id}/posts")
    public String userPosts(@PathVariable UUID id,
                            @RequestParam(defaultValue = "0") int page,
                            @AuthenticationPrincipal UserDetailsImpl principal,
                            Model model) {
        AppUser profileUser = userService.getById(id);
        Profile profile     = profileService.getByUserId(id);
        org.springframework.data.domain.Page<com.com.manasuniversityecosystem.domain.entity.social.Post>
                posts = postService.getUserPosts(id, page, 10);

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        java.util.List<String> likedPostIds = posts.getContent().stream()
                .filter(p -> postService.isLikedByUser(p.getId(), principal.getId()))
                .map(p -> p.getId().toString())
                .toList();

        model.addAttribute("profileUser",   profileUser);
        model.addAttribute("profile",       profile);
        model.addAttribute("posts",         posts.getContent());
        model.addAttribute("nextPage",      posts.hasNext() ? page + 1 : -1);
        model.addAttribute("currentUserId", principal.getId());
        model.addAttribute("isAdmin",       isAdmin);
        model.addAttribute("likedPostIds",  likedPostIds);
        model.addAttribute("isOwn",         principal.getId().equals(id));
        return page == 0 ? "profile/user-posts" : "feed/fragments/user-post-list :: userPostList";
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