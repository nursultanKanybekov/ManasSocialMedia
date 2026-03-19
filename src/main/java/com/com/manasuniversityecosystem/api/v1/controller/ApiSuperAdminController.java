package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.notification.Notification;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.notification.NotificationRepository;
import com.com.manasuniversityecosystem.service.OnlineUserTracker;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/super-admin")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@Tag(name = "Super Admin", description = "Faculty management, online users, system stats — SUPER_ADMIN only")
public class ApiSuperAdminController {

    private final FacultyRepository      facultyRepository;
    private final UserRepository         userRepository;
    private final OnlineUserTracker      onlineUserTracker;
    private final NotificationRepository notificationRepository;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record FacultyResponse(UUID id, String name, String code, long memberCount) {}

    public record CreateFacultyBody(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Size(max = 20)  String code
    ) {}

    public record SystemStats(
            long   totalUsers,
            long   totalFaculties,
            int    onlineNow,
            Map<String, Long> countryBreakdown
    ) {}

    public record FacultyAlertResponse(
            UUID          id,
            String        message,
            LocalDateTime detectedAt
    ) {}

    // ══ Dashboard stats ═══════════════════════════════════════════

    @GetMapping("/stats")
    @Operation(summary = "Get platform-wide system statistics")
    public ResponseEntity<ApiResponse<SystemStats>> stats() {
        return ResponseEntity.ok(ApiResponse.ok(new SystemStats(
                userRepository.count(),
                facultyRepository.count(),
                onlineUserTracker.countOnline(),
                onlineUserTracker.getCountryBreakdown()
        )));
    }

    // ══ Online users ══════════════════════════════════════════════

    @GetMapping("/online")
    @Operation(summary = "Get currently online users and country breakdown")
    public ResponseEntity<ApiResponse<Map<String, Object>>> onlineUsers() {
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "count",            onlineUserTracker.countOnline(),
                "users",            onlineUserTracker.getOnlineUsers(),
                "country_breakdown",onlineUserTracker.getCountryBreakdown()
        )));
    }

    // ══ Faculty management ════════════════════════════════════════

    @GetMapping("/faculties")
    @Operation(summary = "List all faculties with member counts")
    public ResponseEntity<ApiResponse<List<FacultyResponse>>> listFaculties() {
        List<FacultyResponse> faculties = facultyRepository.findAllByOrderByNameAsc()
                .stream()
                .map(f -> new FacultyResponse(
                        f.getId(), f.getName(), f.getCode(),
                        countUsersInFaculty(f.getId())
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(faculties));
    }

    @PostMapping("/faculties")
    @Operation(summary = "Create a new faculty")
    public ResponseEntity<ApiResponse<FacultyResponse>> createFaculty(
            @Valid @RequestBody CreateFacultyBody body) {

        String cleanCode = body.code().trim().toUpperCase();
        if (facultyRepository.existsByCode(cleanCode))
            throw new IllegalArgumentException(
                    "Faculty with code '" + cleanCode + "' already exists.");

        Faculty saved = facultyRepository.save(
                Faculty.builder().name(body.name().trim()).code(cleanCode).build());

        return ResponseEntity.status(201).body(ApiResponse.created(
                new FacultyResponse(saved.getId(), saved.getName(), saved.getCode(), 0L)));
    }

    @DeleteMapping("/faculties/{id}")
    @Operation(summary = "Delete a faculty (fails if users are assigned to it)")
    public ResponseEntity<ApiResponse<Void>> deleteFaculty(@PathVariable UUID id) {
        Faculty f = facultyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found."));

        long users = countUsersInFaculty(id);
        if (users > 0)
            throw new IllegalArgumentException(
                    "Cannot delete '" + f.getName() + "': " + users + " user(s) assigned.");

        facultyRepository.delete(f);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    // ══ OBIS new-faculty detection alerts ════════════════════════

    @GetMapping("/faculty-alerts")
    @Operation(
            summary     = "List unread OBIS new-faculty detection alerts",
            description = "Fired when a student logs in via OBIS and their faculty name doesn't " +
                    "match any existing faculty. The faculty is auto-created, but admins " +
                    "are notified here to review/rename if needed."
    )
    public ResponseEntity<ApiResponse<List<FacultyAlertResponse>>> facultyAlerts() {
        List<FacultyAlertResponse> alerts = notificationRepository
                .findByTypeAndIsReadFalse(Notification.NotifType.NEW_FACULTY_DETECTED)
                .stream()
                .map(n -> new FacultyAlertResponse(n.getId(), n.getMessage(), n.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(alerts));
    }

    @PostMapping("/faculty-alerts/{id}/dismiss")
    @Operation(summary = "Dismiss (mark read) a faculty detection alert")
    public ResponseEntity<ApiResponse<Void>> dismissAlert(@PathVariable UUID id) {
        notificationRepository.findById(id).ifPresent(n -> {
            n.setIsRead(true);
            notificationRepository.save(n);
        });
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    // ══ Helpers ═══════════════════════════════════════════════════

    /**
     * Counts all users (active + inactive) belonging to a faculty.
     * Uses findByFacultyIdAndStatus for each status and sums them,
     * since UserRepository has no countByFacultyId method.
     */
    private long countUsersInFaculty(UUID facultyId) {
        long active  = userRepository.findByFacultyIdAndStatus(facultyId, UserStatus.ACTIVE).size();
        long pending = userRepository.findByFacultyIdAndStatus(facultyId, UserStatus.PENDING).size();
        return active + pending;
    }
}