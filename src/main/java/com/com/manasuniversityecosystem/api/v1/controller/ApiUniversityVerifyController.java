package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.ErrorCode;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.ProfileService;
import com.com.manasuniversityecosystem.service.UniversityAuthService;
import com.com.manasuniversityecosystem.service.UniversityAuthService.UniversityAuthException;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.web.dto.auth.ObisStudentInfo;
import com.com.manasuniversityecosystem.web.dto.profile.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/university-verify")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "University Verification",
        description = "Link an existing ManasApp account to OBIS for verified student status")
public class ApiUniversityVerifyController {

    private final UniversityAuthService universityAuthService;
    private final UserService           userService;
    private final ProfileService        profileService;

    public record VerifyRequest(@NotBlank String username, @NotBlank String password) {}

    public record VerifyResponse(
            boolean universityVerified, String fullName, String facultyName,
            Integer studyYear, Integer admissionYear, String message
    ) {}

    @PostMapping
    @Operation(
            summary = "Verify university identity via OBIS for an existing account",
            description = "Authenticates against OBIS, syncs name/faculty/studyYear, marks account verified.")
    public ResponseEntity<ApiResponse<VerifyResponse>> verify(
            @Valid @RequestBody VerifyRequest req,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        try {
            ObisStudentInfo info = universityAuthService
                    .authenticateAndFetch(req.username(), req.password());

            AppUser user = userService.getById(principal.getId());

            // Mark verified
            userService.markUniversityVerified(user);

            // Sync study year to profile
            if (info.getStudyYear() != null) {
                ProfileUpdateRequest pur = new ProfileUpdateRequest();
                pur.setStudyYear(info.getStudyYear());
                profileService.update(user, pur);
            }

            AppUser updated = userService.getById(principal.getId());
            return ResponseEntity.ok(ApiResponse.ok(new VerifyResponse(
                    updated.isUniversityVerified(), updated.getFullName(),
                    updated.getFaculty() != null ? updated.getFaculty().getName() : null,
                    updated.getProfile() != null ? updated.getProfile().getStudyYear() : null,
                    info.getAdmissionYear(),
                    "University identity verified. Your profile now shows \u2705 Verified."
            )));

        } catch (UniversityAuthException e) {
            ErrorCode code = e.getMessage().contains("unreachable")
                    ? ErrorCode.OBIS_UNAVAILABLE : ErrorCode.OBIS_INVALID_CREDENTIALS;
            int status = e.getMessage().contains("unreachable") ? 503 : 401;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(status, code, e.getMessage()));
        }
    }

    @GetMapping("/status")
    @Operation(summary = "Check current university verification status")
    public ResponseEntity<ApiResponse<VerifyResponse>> status(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(new VerifyResponse(
                user.isUniversityVerified(), user.getFullName(),
                user.getFaculty() != null ? user.getFaculty().getName() : null,
                user.getProfile() != null ? user.getProfile().getStudyYear() : null,
                null,
                user.isUniversityVerified()
                        ? "Your account is university-verified."
                        : "Not verified. POST /api/v1/university-verify with OBIS credentials."
        )));
    }
}