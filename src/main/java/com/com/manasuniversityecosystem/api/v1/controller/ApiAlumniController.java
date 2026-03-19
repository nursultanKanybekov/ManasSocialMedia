package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alumni")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Alumni Catalog", description = "Browse and search alumni / graduates")
public class ApiAlumniController {

    private final UserService userService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record AlumniSummary(
            UUID   id,         String fullName,    String avatarUrl,
            String headline,   String facultyName, Integer graduationYear,
            int    totalPoints, boolean canMentor
    ) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping
    @Operation(summary = "Browse alumni catalog (paginated, searchable, sortable)")
    public ResponseEntity<ApiResponse<PageResponse<AlumniSummary>>> catalog(
            @RequestParam(defaultValue = "0")    int     page,
            @RequestParam(defaultValue = "20")   int     size,
            @RequestParam(required = false)      String  name,
            @RequestParam(required = false)      UUID    facultyId,
            @RequestParam(required = false)      Integer graduationYear,
            @RequestParam(defaultValue = "name") String  sortBy,
            @RequestParam(defaultValue = "asc")  String  sortDir) {

        Sort sort = Sort.by("asc".equalsIgnoreCase(sortDir)
                        ? Sort.Direction.ASC : Sort.Direction.DESC,
                "name".equals(sortBy) ? "fullName" : "profile.totalPoints");

        // Filter alumni in-memory — no generic search query in UserService
        java.util.List<AppUser> all = userService.getAllByRole(
                com.com.manasuniversityecosystem.domain.enums.UserRole.MEZUN);
        if (name != null && !name.isBlank()) {
            String q = name.toLowerCase();
            all = all.stream().filter(u -> u.getFullName().toLowerCase().contains(q)).toList();
        }
        if (facultyId != null)
            all = all.stream().filter(u -> u.getFaculty() != null
                    && u.getFaculty().getId().equals(facultyId)).toList();
        if (graduationYear != null)
            all = all.stream().filter(u -> graduationYear.equals(u.getGraduationYear())).toList();

        int from = page * size, to = Math.min(from + size, all.size());
        java.util.List<AppUser> slice = from >= all.size() ? java.util.List.of() : all.subList(from, to);
        var paged = new org.springframework.data.domain.PageImpl<>(
                slice, org.springframework.data.domain.PageRequest.of(page, size), all.size());

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(paged.map(this::toSummary))));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single alumnus profile")
    public ResponseEntity<ApiResponse<AlumniSummary>> getOne(@PathVariable UUID id) {
        AppUser u = userService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(toSummary(u)));
    }

    // ══ Mapper ═══════════════════════════════════════════════════

    private AlumniSummary toSummary(AppUser u) {
        Profile p = u.getProfile();
        return new AlumniSummary(
                u.getId(), u.getFullName(),
                p != null ? p.getAvatarUrl() : null,
                p != null ? p.getHeadline()  : null,
                u.getFaculty() != null ? u.getFaculty().getName() : null,
                u.getGraduationYear(),
                p != null ? p.getTotalPoints() : 0,
                p != null && Boolean.TRUE.equals(p.getCanMentor())
        );
    }
}