package com.com.manasuniversityecosystem.web.controller;


import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.service.AuthService;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import com.com.manasuniversityecosystem.service.EmailService;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.service.CloudinaryService;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.web.dto.auth.JwtResponse;
import com.com.manasuniversityecosystem.web.dto.auth.LoginRequest;
import com.com.manasuniversityecosystem.web.dto.auth.RegisterRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final FacultyRepository facultyRepo;
    private final CloudinaryService cloudinaryService;
    private final NotificationService  notificationService;
    private final EmailService         emailService;
    private final UserRepository       userRepo;

    // ── GET /auth/login ──────────────────────────────────────
    @GetMapping("/login")
    public String loginPage(@RequestParam(required = false) String error,
                            @RequestParam(required = false) String logout,
                            @RequestParam(required = false) String registered,
                            Model model) {
        if (error      != null) model.addAttribute("errorMsg", "auth.error.invalid_credentials");
        if (logout     != null) model.addAttribute("logoutMsg", "auth.logout.success");
        if (registered != null) model.addAttribute("infoMsg", "auth.register.pending_validation");
        return "auth/login";
    }

    // ── GET /auth/register ───────────────────────────────────
    @GetMapping("/register")
    public String registerPage(Model model) {
        model.addAttribute("registerRequest", new RegisterRequest());
        model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
        return "auth/register";
    }

    // ── POST /auth/register ──────────────────────────────────
    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("registerRequest") RegisterRequest req,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
            return "auth/register";
        }
        try {
            userService.register(req);
            redirectAttributes.addFlashAttribute("registered", true);
            return "redirect:/auth/login?registered=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMsg", e.getMessage());
            model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
            return "auth/register";
        }
    }

    // ── GET /auth/pending ────────────────────────────────────
    @GetMapping("/pending")
    public String pendingPage() {
        return "auth/pending";
    }

    // GET /auth/forgot-password
    @GetMapping("/forgot-password")
    public String forgotPasswordPage(@RequestParam(required=false) String sent, Model model) {
        if (sent != null) model.addAttribute("sent", true);
        return "auth/forgot-password";
    }

    // POST /auth/forgot-password
    @PostMapping("/forgot-password")
    public String forgotPassword(@RequestParam String email, RedirectAttributes ra) {
        try {
            var optUser = userRepo.findByEmail(email.trim().toLowerCase());
            if (optUser.isPresent()) {
                var user = optUser.get();
                notificationService.notifyPasswordResetRequest(
                        user.getId(), user.getFullName(), user.getEmail());
                // Send confirmation email to the user
                emailService.sendPasswordResetRequestConfirmation(
                        user.getEmail(), user.getFullName());
            }
            // Always show same message (security: don't reveal if email exists)
        } catch (Exception e) {
            log.error("Forgot password error", e);
        }
        return "redirect:/auth/forgot-password?sent=true";
    }

    // ── REST: POST /auth/api/login  (for API / mobile clients) ──
    @PostMapping("/api/login")
    @ResponseBody
    public ResponseEntity<JwtResponse> apiLogin(@Valid @RequestBody LoginRequest req,
                                                HttpServletResponse response) {
        try {
            JwtResponse jwt = authService.login(req, response);
            return ResponseEntity.ok(jwt);
        } catch (DisabledException e) {
            return ResponseEntity.status(403).build();
        } catch (BadCredentialsException e) {
            return ResponseEntity.status(401).build();
        }
    }

    // ── REST: POST /auth/api/logout ──────────────────────────
    @PostMapping("/api/logout")
    @ResponseBody
    public ResponseEntity<Void> apiLogout(HttpServletResponse response) {
        authService.logout(response);
        return ResponseEntity.ok().build();
    }
}