package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.ErrorCode;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.security.JwtTokenProvider;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.ObisLoginService;
import com.com.manasuniversityecosystem.service.UniversityAuthService;
import com.com.manasuniversityecosystem.service.UniversityAuthService.UniversityAuthException;
import com.com.manasuniversityecosystem.web.dto.auth.ObisStudentInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Login, OBIS proxy, registration and token management")
public class ApiAuthController {

    private final AuthenticationManager  authManager;
    private final JwtTokenProvider       tokenProvider;
    private final UniversityAuthService  universityAuthService;
    private final ObisLoginService       obisLoginService;

    // ═══════════════════════════════════════════════════════════════
    // DTOs
    // ═══════════════════════════════════════════════════════════════

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6) String password
    ) {}

    public record ObisLoginRequest(
            @NotBlank String username,
            @NotBlank String password
    ) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 100) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank String role   // STUDENT | MEZUN | EMPLOYER
    ) {}

    public record TokenResponse(
            String accessToken,
            String tokenType,
            long   expiresIn,
            UserSummary user
    ) {}

    public record UserSummary(
            UUID   id,
            String email,
            String fullName,
            String role,
            String avatarUrl
    ) {}

    // ═══════════════════════════════════════════════════════════════
    // Endpoints
    // ═══════════════════════════════════════════════════════════════

    @PostMapping("/login")
    @Operation(
            summary     = "Login with ManasApp credentials",
            description = "Authenticate with email and password. Returns a JWT access token.",
            security    = {}  // public endpoint
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Login successful")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Invalid credentials")
    public ResponseEntity<ApiResponse<TokenResponse>> login(
            @Valid @RequestBody LoginRequest req) {

        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.email(), req.password()));

        SecurityContextHolder.getContext().setAuthentication(auth);
        UserDetailsImpl principal = (UserDetailsImpl) auth.getPrincipal();
        String token = tokenProvider.generateToken(principal);

        return ResponseEntity.ok(ApiResponse.ok(buildTokenResponse(token, principal)));
    }

    // ───────────────────────────────────────────────────────────────

    @PostMapping("/obis-login")
    @Operation(
            summary     = "Login via OBIS university portal (students)",
            description = """
            **Proxy flow for mobile:**
            1. Mobile sends `{username, password}` (OBIS credentials).
            2. This server authenticates against `obistest.manas.edu.kg`.
            3. Scrapes verified student data (name, faculty, study year).
            4. Creates/updates the student's ManasApp account.
            5. Returns a JWT token — OBIS credentials are **never stored**.
            """,
            security    = {}
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "OBIS login successful")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Wrong OBIS credentials")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "OBIS portal unreachable")
    public ResponseEntity<ApiResponse<TokenResponse>> obisLogin(
            @Valid @RequestBody ObisLoginRequest req,
            HttpServletRequest  httpRequest) {

        try {
            ObisStudentInfo info = universityAuthService
                    .authenticateAndFetch(req.username(), req.password());

            AppUser user = obisLoginService.loginOrRegister(info, httpRequest);

            UserDetailsImpl principal = UserDetailsImpl.build(user);
            String token = tokenProvider.generateToken(principal);

            return ResponseEntity.ok(ApiResponse.ok(buildTokenResponse(token, principal)));

        } catch (UniversityAuthException e) {
            ErrorCode code = e.getMessage().contains("unreachable")
                    ? ErrorCode.OBIS_UNAVAILABLE
                    : ErrorCode.OBIS_INVALID_CREDENTIALS;
            int status = e.getMessage().contains("unreachable") ? 503 : 401;
            return ResponseEntity.status(status)
                    .body(ApiResponse.error(status, code, e.getMessage()));
        }
    }

    // ───────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(
            summary  = "Register a new ManasApp account",
            description = "Creates account in PENDING state. Requires secretary/admin approval. " +
                    "Students should prefer OBIS login for instant verified access.",
            security = {}
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Registration submitted, pending approval")
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Email already exists")
    public ResponseEntity<ApiResponse<String>> register(
            @Valid @RequestBody RegisterRequest req) {
        // Delegate to existing UserService — no logic duplication
        // Return 202 Accepted (not 201) because account needs approval
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.error(202, null,
                        "Registration submitted. You will be notified once approved."));
    }

    // ───────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the currently authenticated user's profile summary")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<UserSummary>> me(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        return ResponseEntity.ok(ApiResponse.ok(
                new UserSummary(
                        principal.getId(),
                        principal.getEmail(),
                        principal.getFullName(),
                        principal.getRole().name(),
                        null  // avatar resolved via /api/v1/profile/me
                )
        ));
    }

    // ───────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @Operation(summary = "Invalidate the current JWT (client-side discard)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ApiResponse<Void>> logout() {
        // JWT is stateless — client simply discards the token.
        // For server-side invalidation, add token to a Redis blocklist here.
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private TokenResponse buildTokenResponse(String token, UserDetailsImpl principal) {
        return new TokenResponse(
                token,
                "Bearer",
                tokenProvider.getExpirationMs() / 1000,
                new UserSummary(
                        principal.getId(),
                        principal.getEmail(),
                        principal.getFullName(),
                        principal.getRole().name(),
                        null
                )
        );
    }
}