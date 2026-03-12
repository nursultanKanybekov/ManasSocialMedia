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
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class AdminController {

    private final UserService userService;
    private final GamificationService gamificationService;
    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final ExcelImportService excelImportService;
    private final PostService postService;

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totalUsers",    userRepo.count());
        model.addAttribute("pendingUsers",  userRepo.findByStatus(UserStatus.PENDING).size());
        model.addAttribute("activeUsers",   userRepo.findByStatus(UserStatus.ACTIVE).size());
        model.addAttribute("suspendedUsers",userRepo.findByStatus(UserStatus.SUSPENDED).size());
        model.addAttribute("totalPosts",    postRepo.count());
        model.addAttribute("globalTop10",   gamificationService.getGlobalTop(10));
        return "admin/dashboard";
    }

    // GET /admin/users  — all users list
    @GetMapping("/users")
    public String usersList(@RequestParam(required = false) String role,
                            @RequestParam(required = false) String status,
                            Model model) {
        if (role != null && !role.isBlank()) {
            model.addAttribute("users", userRepo.findByRole(UserRole.valueOf(role.toUpperCase())));
        } else if (status != null && !status.isBlank()) {
            model.addAttribute("users", userRepo.findByStatus(UserStatus.valueOf(status.toUpperCase())));
        } else {
            model.addAttribute("users", userRepo.findAll());
        }
        model.addAttribute("roles",    UserRole.values());
        model.addAttribute("statuses", UserStatus.values());
        return "admin/users";
    }

    // GET /admin/users/{id}  — user detail
    @GetMapping("/users/{id}")
    public String userDetail(@PathVariable UUID id, Model model) {
        AppUser user = userService.getById(id);
        model.addAttribute("user", user);
        return "admin/user-detail";
    }

    // POST /admin/users/{id}/activate
    @PostMapping("/users/{id}/activate")
    public String activateUser(@PathVariable UUID id, RedirectAttributes ra) {
        userService.activate(id);
        ra.addFlashAttribute("successMsg", "admin.user.activated");
        return "redirect:/admin/users";
    }

    // POST /admin/users/{id}/suspend
    @PostMapping("/users/{id}/suspend")
    public String suspendUser(@PathVariable UUID id, RedirectAttributes ra) {
        userService.suspend(id);
        ra.addFlashAttribute("successMsg", "admin.user.suspended");
        return "redirect:/admin/users";
    }

    // POST /admin/users/{id}/role
    @PostMapping("/users/{id}/role")
    public String changeRole(@PathVariable UUID id,
                             @RequestParam String role,
                             RedirectAttributes ra) {
        userService.changeRole(id, UserRole.valueOf(role.toUpperCase()));
        ra.addFlashAttribute("successMsg", "admin.user.role_changed");
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