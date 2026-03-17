package com.com.manasuniversityecosystem.web.controller;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.social.PostRepository;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.web.dto.social.CreatePostRequest;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.EmailService;
import com.com.manasuniversityecosystem.service.OnlineUserTracker;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.service.admin.ExcelImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.multipart.MultipartFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserService         userService;
    private final EmailService        emailService;
    private final GamificationService gamificationService;
    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final ExcelImportService excelImportService;
    private final PostService postService;
    private final OnlineUserTracker onlineUserTracker;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totalUsers",    userRepo.count());
        model.addAttribute("pendingUsers",  userRepo.findByStatus(UserStatus.PENDING).size());
        model.addAttribute("activeUsers",   userRepo.findByStatus(UserStatus.ACTIVE).size());
        model.addAttribute("suspendedUsers",userRepo.findByStatus(UserStatus.SUSPENDED).size());
        model.addAttribute("totalPosts",    postRepo.count());
        model.addAttribute("globalTop10",   gamificationService.getGlobalTop(10));
        model.addAttribute("onlineCount",   onlineUserTracker.countOnline());
        return "admin/dashboard";
    }

    // GET /admin/users  — all users list (with optional search by name/email)
    @GetMapping("/users")
    public String usersList(@RequestParam(required = false) String role,
                            @RequestParam(required = false) String status,
                            @RequestParam(required = false) String q,
                            @AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                            Model model) {
        java.util.List<AppUser> users;

        if (q != null && !q.isBlank()) {
            // Full-text search by name or email — ignores role/status filters while searching
            users = userRepo.adminSearchByNameOrEmail("%" + q.trim().toLowerCase() + "%");
        } else if (role != null && !role.isBlank()) {
            users = userRepo.findByRole(UserRole.valueOf(role.toUpperCase()));
        } else if (status != null && !status.isBlank()) {
            users = userRepo.findByStatus(UserStatus.valueOf(status.toUpperCase()));
        } else {
            users = userRepo.findAll();
        }

        model.addAttribute("users",    users);
        model.addAttribute("roles",    UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
        model.addAttribute("q",        q != null ? q : "");
        return "admin/users";
    }

    // GET /admin/users/{id}  — user detail
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable UUID id,
                             @AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                             Model model) {
        AppUser user = userService.getById(id);
        model.addAttribute("user", user);
        // Hide action buttons if target is SUPER_ADMIN (and current user is not SUPER_ADMIN)
        boolean isSuperAdmin = user.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.SUPER_ADMIN;
        boolean viewerIsSuperAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        model.addAttribute("canManage", !isSuperAdmin || viewerIsSuperAdmin);
        model.addAttribute("targetIsSuperAdmin", isSuperAdmin);
        return "admin/user-detail";
    }

    // POST /admin/users/{id}/activate
    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable UUID id,
                               @AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                               RedirectAttributes ra) {
        AppUser target = userService.getById(id);
        if (target.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.SUPER_ADMIN
                && !principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
            ra.addFlashAttribute("errorMsg", "You cannot manage a Super Admin account.");
            return "redirect:/admin/users/" + id;
        }
        userService.activate(id);
        ra.addFlashAttribute("successMsg", "admin.user.activated");
        return "redirect:/admin/users";
    }

    // POST /admin/users/{id}/suspend
    @PostMapping("/users/{id}/suspend")
    public String suspendUser(@PathVariable UUID id,
                              @AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                              RedirectAttributes ra) {
        AppUser target = userService.getById(id);
        if (target.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.SUPER_ADMIN
                && !principal.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"))) {
            ra.addFlashAttribute("errorMsg", "You cannot manage a Super Admin account.");
            return "redirect:/admin/users/" + id;
        }
        userService.suspend(id);
        ra.addFlashAttribute("successMsg", "admin.user.suspended");
        return "redirect:/admin/users";
    }

    // POST /admin/users/{id}/role
    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable UUID id,
                             @RequestParam String role,
                             @AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                             RedirectAttributes ra) {
        AppUser target = userService.getById(id);
        boolean viewerIsSuperAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        // Block: non-SUPER_ADMIN cannot change a SUPER_ADMIN's role
        if (target.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.SUPER_ADMIN
                && !viewerIsSuperAdmin) {
            ra.addFlashAttribute("errorMsg", "You cannot change a Super Admin's role.");
            return "redirect:/admin/users/" + id;
        }
        // Block: nobody can assign SUPER_ADMIN role from this page
        if ("SUPER_ADMIN".equalsIgnoreCase(role) && !viewerIsSuperAdmin) {
            ra.addFlashAttribute("errorMsg", "You cannot assign the Super Admin role.");
            return "redirect:/admin/users/" + id;
        }
        userService.changeRole(id, UserRole.valueOf(role.toUpperCase()));
        ra.addFlashAttribute("successMsg", "admin.user.role_changed");
        return "redirect:/admin/users/" + id;
    }

    // POST /admin/users/{id}/reset-password  — SUPER_ADMIN only
    @PostMapping("/users/{id}/reset-password")
    public String resetPassword(@PathVariable UUID id,
                                @RequestParam String newPassword,
                                @RequestParam String confirmPassword,
                                @AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                                RedirectAttributes ra) {
        // ADMIN and SUPER_ADMIN can reset passwords
        boolean canReset = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ROLE_ADMIN"));
        if (!canReset) {
            ra.addFlashAttribute("errorMsg", "You do not have permission to reset passwords.");
            return "redirect:/admin/users/" + id;
        }
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("errorMsg", "Passwords do not match.");
            return "redirect:/admin/users/" + id;
        }
        try {
            AppUser target = userService.getById(id);
            userService.resetPassword(id, newPassword);
            // Email the user to notify them
            String adminName = principal.getUsername();
            emailService.sendPasswordResetNotification(
                    target.getEmail(), target.getFullName(), adminName);
            ra.addFlashAttribute("successMsg",
                    "Password reset successfully. User has been notified by email.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/users/" + id;
    }

    // POST /admin/posts/{id}/pin
    // POST /admin/users/{id}/delete
    @PostMapping("/users/{id}/delete")
    public String deleteUser(@PathVariable UUID id,
                             @AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                             RedirectAttributes ra) {
        if (id.equals(principal.getId())) {
            ra.addFlashAttribute("errorMsg", "You cannot delete your own account.");
            return "redirect:/admin/users";
        }
        userService.deleteUser(id);
        ra.addFlashAttribute("successMsg", "User deleted successfully.");
        return "redirect:/admin/users";
    }

    @PostMapping("/posts/{id}/pin")
    public String pinPost(@PathVariable UUID id, RedirectAttributes ra) {
        // PostService.pinPost toggles pin state
        // Injecting PostService directly here to keep controller thin
        ra.addFlashAttribute("successMsg", "admin.post.pinned");
        return "redirect:/feed";
    }

    // POST /admin/rankings/recalculate  — manual trigger
    @PostMapping("/rankings/recalculate")
    public String recalculateRankings(RedirectAttributes ra) {
        gamificationService.recalculateRankings();
        ra.addFlashAttribute("successMsg", "admin.rankings.recalculated");
        return "redirect:/admin";
    }

    // GET /admin/import-mezuns  — show Excel import form
    @GetMapping("/import-mezuns")
    public String importMezunsForm(Model model) {
        return "admin/import-mezuns";
    }

    // POST /admin/import-mezuns  — process uploaded Excel
    @PostMapping("/import-mezuns")
    public String importMezuns(@RequestParam("file") MultipartFile file,
                               RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("errorMsg", "Please select an Excel file.");
            return "redirect:/admin/import-mezuns";
        }
        try {
            ExcelImportService.ImportResult result = excelImportService.importMezuns(file);
            ra.addFlashAttribute("importResult", result);
            ra.addFlashAttribute("successMsg",
                    "Import complete: " + result.imported() + " imported, " + result.skipped() + " skipped.");
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", "Failed to process file: " + e.getMessage());
        }
        return "redirect:/admin/import-mezuns";
    }
    // GET /admin/news  — university news management
    @GetMapping("/news")
    public String newsManagement(@RequestParam(defaultValue = "0") int page, Model model) {
        model.addAttribute("posts", postService.getFeedByType(PostType.UNIVERSITY_NEWS, page, 10));
        model.addAttribute("currentPage", page);
        return "admin/news";
    }

    // GET /admin/news/new  — news creation form
    @GetMapping("/news/new")
    public String newNewsForm(Model model) {
        return "admin/news-form";
    }

    // POST /admin/news/new  — create university news
    @PostMapping("/news/new")
    public String createNews(@RequestParam String title,
                             @RequestParam String content,
                             @RequestParam(defaultValue = "en") String lang,
                             @org.springframework.security.core.annotation.AuthenticationPrincipal com.com.manasuniversityecosystem.security.UserDetailsImpl principal,
                             RedirectAttributes ra) {
        AppUser admin = userService.getById(principal.getId());
        CreatePostRequest req = new CreatePostRequest();
        req.setContent((title.isBlank() ? "" : "**" + title + "**\n\n") + content);
        req.setLang(lang);
        req.setPostType(PostType.UNIVERSITY_NEWS);
        postService.createPost(admin, req);
        ra.addFlashAttribute("successMsg", "admin.news.created");
        return "redirect:/admin/news";
    }

    // GET /admin/create-secretary  — form to create a secretary account
    @GetMapping("/create-secretary")
    public String createSecretaryForm(Model model) {
        return "admin/create-secretary";
    }

    // POST /admin/create-secretary
    @PostMapping("/create-secretary")
    public String createSecretary(@RequestParam String fullName,
                                  @RequestParam String email,
                                  @RequestParam String password,
                                  RedirectAttributes ra) {
        try {
            if (userService.getAllByRole(com.com.manasuniversityecosystem.domain.enums.UserRole.SECRETARY)
                    .stream().anyMatch(u -> u.getEmail().equals(email))) {
                ra.addFlashAttribute("errorMsg", "Email already registered.");
                return "redirect:/admin/create-secretary";
            }
            com.com.manasuniversityecosystem.domain.entity.AppUser sec =
                    com.com.manasuniversityecosystem.domain.entity.AppUser.builder()
                            .fullName(fullName.trim())
                            .email(email.trim())
                            .passwordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password))
                            .role(com.com.manasuniversityecosystem.domain.enums.UserRole.SECRETARY)
                            .status(com.com.manasuniversityecosystem.domain.enums.UserStatus.ACTIVE)
                            .build();
            com.com.manasuniversityecosystem.domain.entity.Profile profile =
                    com.com.manasuniversityecosystem.domain.entity.Profile.builder()
                            .user(sec).totalPoints(0).build();
            sec.setProfile(profile);
            userRepo.save(sec);
            ra.addFlashAttribute("successMsg", "Secretary account created: " + email);
        } catch (Exception e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/admin/create-secretary";
    }

    // POST /admin/news/{id}/delete  — delete a news post
    @PostMapping("/news/{id}/delete")
    public String deleteNews(@PathVariable UUID id, RedirectAttributes ra) {
        postService.deletePost(id);
        ra.addFlashAttribute("successMsg", "admin.news.deleted");
        return "redirect:/admin/news";
    }

}