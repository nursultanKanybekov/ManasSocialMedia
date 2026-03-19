package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.admin.ExcelImportService;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.web.dto.social.CreatePostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@Tag(name = "Admin", description = "User management, news publishing, rankings — ADMIN / SUPER_ADMIN only")
public class ApiAdminController {

    private final UserService         userService;
    private final UserRepository      userRepository;
    private final PostService         postService;
    private final GamificationService gamificationService;
    private final ExcelImportService  excelImportService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record AdminUserResponse(
            UUID id, String fullName, String email, String role, String status,
            String facultyName, Integer graduationYear, boolean universityVerified,
            LocalDateTime createdAt
    ) {}

    public record ChangeRoleBody(@NotBlank String role) {}

    public record ResetPasswordBody(@NotBlank @Size(min = 8) String newPassword) {}

    public record CreateNewsBody(
            @NotBlank @Size(max = 300)    String title,
            @NotBlank @Size(max = 100000) String content,
            String lang
    ) {}

    public record CreateSecretaryBody(
            @NotBlank @Size(min = 2, max = 100) String fullName,
            @NotBlank @Email                    String email,
            @NotBlank @Size(min = 8)            String password
    ) {}

    public record DashboardStats(
            long totalUsers, long totalStudents, long totalAlumni,
            long pendingApprovals, long totalFaculties
    ) {}

    // ══ Dashboard ════════════════════════════════════════════════

    @GetMapping("/dashboard")
    @Operation(summary = "Get admin dashboard statistics")
    public ResponseEntity<ApiResponse<DashboardStats>> dashboard() {
        long total    = userRepository.count();
        long students = userRepository.findByRole(UserRole.STUDENT).size();
        long alumni   = userRepository.findByRole(UserRole.MEZUN).size();
        long pending  = userRepository.findByRoleAndStatus(UserRole.STUDENT, UserStatus.PENDING).size()
                + userRepository.findByRoleAndStatus(UserRole.MEZUN,  UserStatus.PENDING).size();

        return ResponseEntity.ok(ApiResponse.ok(
                new DashboardStats(total, students, alumni, pending, 0L)));
    }

    // ══ User management ══════════════════════════════════════════

    @GetMapping("/users")
    @Operation(summary = "List users (paginated, filterable by role / status)")
    public ResponseEntity<ApiResponse<PageResponse<AdminUserResponse>>> listUsers(
            @RequestParam(required = false)    String role,
            @RequestParam(required = false)    String status,
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size) {

        List<AppUser> all = userRepository.findAll();

        // Filter in-memory (no generic search query in existing repository)
        if (role   != null && !role.isBlank())
            all = all.stream().filter(u -> u.getRole().name().equalsIgnoreCase(role)).toList();
        if (status != null && !status.isBlank())
            all = all.stream().filter(u -> u.getStatus().name().equalsIgnoreCase(status)).toList();

        int from = page * size, to = Math.min(from + size, all.size());
        var slice = from >= all.size() ? List.<AppUser>of() : all.subList(from, to);
        var paged = new PageImpl<>(slice, PageRequest.of(page, size), all.size());

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(paged.map(this::toAdminUser))));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a single user's admin detail")
    public ResponseEntity<ApiResponse<AdminUserResponse>> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(toAdminUser(userService.getById(id))));
    }

    @PostMapping("/users/{id}/activate")
    @Operation(summary = "Activate a PENDING or SUSPENDED user account")
    public ResponseEntity<ApiResponse<Void>> activateUser(@PathVariable UUID id) {
        userService.activate(id);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/users/{id}/suspend")
    @Operation(summary = "Suspend an active user account")
    public ResponseEntity<ApiResponse<Void>> suspendUser(@PathVariable UUID id) {
        userService.suspend(id);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role")
    public ResponseEntity<ApiResponse<AdminUserResponse>> changeRole(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeRoleBody body) {

        UserRole newRole = UserRole.valueOf(body.role().toUpperCase());
        AppUser  updated = userService.changeRole(id, newRole);
        return ResponseEntity.ok(ApiResponse.ok(toAdminUser(updated)));
    }

    @PostMapping("/users/{id}/reset-password")
    @Operation(summary = "Admin resets a user's password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @PathVariable UUID id,
            @Valid @RequestBody ResetPasswordBody body) {

        userService.resetPassword(id, body.newPassword());
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @DeleteMapping("/users/{id}")
    @Operation(summary = "Permanently delete a user account (irreversible)")
    public ResponseEntity<ApiResponse<Void>> deleteUser(@PathVariable UUID id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    // ══ Posts ════════════════════════════════════════════════════

    @PostMapping("/posts/{id}/pin")
    @Operation(summary = "Toggle pin on any post (admin override)")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> pinPost(@PathVariable UUID id) {
        postService.pinPost(id);
        boolean pinned = postService.getById(id).getIsPinned();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("pinned", pinned)));
    }

    // ══ Rankings ═════════════════════════════════════════════════

    @PostMapping("/rankings/recalculate")
    @Operation(summary = "Trigger a global rank recalculation (run off-peak)")
    public ResponseEntity<ApiResponse<Void>> recalculateRankings() {
        gamificationService.recalculateRankings();
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    // ══ University news (stored as UNIVERSITY_NEWS posts) ════════

    @PostMapping("/news")
    @Operation(summary = "Publish a university news post")
    public ResponseEntity<ApiResponse<Map<String, Object>>> createNews(
            @Valid @RequestBody CreateNewsBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser admin = userService.getById(principal.getId());
        String  lang  = body.lang() != null ? body.lang() : "en";

        CreatePostRequest req = new CreatePostRequest();
        req.setContent(("**" + body.title() + "**\n\n" + body.content()));
        req.setLang(lang);
        req.setPostType(PostType.UNIVERSITY_NEWS);

        var post = postService.createPost(admin, req);
        return ResponseEntity.status(201).body(ApiResponse.created(Map.of(
                "id",      post.getId(),
                "content", body.title()
        )));
    }

    // ══ Alumni import ═════════════════════════════════════════════

    @PostMapping(value = "/import-alumni", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Bulk-import alumni from Excel/CSV (cols: fullName, email, facultyCode, graduationYear)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> importAlumni(
            @RequestPart("file") MultipartFile file) throws IOException {

        ExcelImportService.ImportResult result = excelImportService.importMezuns(file);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "imported", result.imported(),
                "skipped",  result.skipped(),
                "errors",   result.errors()
        )));
    }

    // ══ Secretary creation ════════════════════════════════════════

    @PostMapping("/secretaries")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Create a new secretary account (SUPER_ADMIN only)")
    public ResponseEntity<ApiResponse<AdminUserResponse>> createSecretary(
            @Valid @RequestBody CreateSecretaryBody body) {

        // Reuse existing register flow with SECRETARY role
        if (userRepository.findByEmail(body.email()).isPresent())
            throw new IllegalArgumentException("Email already registered.");

        var req = new com.com.manasuniversityecosystem.web.dto.auth.RegisterRequest();
        req.setFullName(body.fullName());
        req.setEmail(body.email());
        req.setPassword(body.password());
        req.setRole(UserRole.SECRETARY);

        AppUser sec = userService.register(req);
        sec = userService.activate(sec.getId());
        return ResponseEntity.status(201).body(ApiResponse.created(toAdminUser(sec)));
    }

    // ══ Mapper ═══════════════════════════════════════════════════

    private AdminUserResponse toAdminUser(AppUser u) {
        return new AdminUserResponse(
                u.getId(), u.getFullName(), u.getEmail(),
                u.getRole().name(), u.getStatus().name(),
                u.getFaculty() != null ? u.getFaculty().getName() : null,
                u.getGraduationYear(), u.isUniversityVerified(),
                u.getCreatedAt()
        );
    }
}