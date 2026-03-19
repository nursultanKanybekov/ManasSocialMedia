package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.SecretaryService;
import com.com.manasuniversityecosystem.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/secretary")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SECRETARY','ADMIN','SUPER_ADMIN')")
@Tag(name = "Secretary", description = "Pending account approval queue")
public class ApiSecretaryController {

    private final SecretaryService secretaryService;
    private final UserService      userService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record PendingUserResponse(
            UUID   validationId, UUID userId, String fullName,
            String email, String role, String facultyName, LocalDateTime registeredAt
    ) {}

    public record RejectBody(@Size(max = 500) String reason) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping("/queue")
    @Operation(summary = "Get all pending registration requests")
    public ResponseEntity<ApiResponse<List<PendingUserResponse>>> queue() {
        List<PendingUserResponse> pending = secretaryService.getPendingValidations()
                .stream()
                .map(v -> new PendingUserResponse(
                        v.getId(),
                        v.getUser().getId(),
                        v.getUser().getFullName(),
                        v.getUser().getEmail(),
                        v.getUser().getRole().name(),
                        v.getUser().getFaculty() != null ? v.getUser().getFaculty().getName() : null,
                        v.getUser().getCreatedAt()
                ))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(pending));
    }

    @PostMapping("/queue/{validationId}/approve")
    @Operation(summary = "Approve a pending user registration")
    public ResponseEntity<ApiResponse<Void>> approve(
            @PathVariable UUID validationId,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser secretary = userService.getById(principal.getId());
        secretaryService.approve(validationId, secretary);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/queue/{validationId}/reject")
    @Operation(summary = "Reject a pending user registration with optional reason")
    public ResponseEntity<ApiResponse<Void>> reject(
            @PathVariable UUID validationId,
            @RequestBody(required = false) RejectBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser secretary = userService.getById(principal.getId());
        String  reason    = body != null ? body.reason() : null;
        secretaryService.reject(validationId, secretary, reason);
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}