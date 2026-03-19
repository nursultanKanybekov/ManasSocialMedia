package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.competition.CompetitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/competitions")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Competitions", description = "University competitions and registrations")
public class ApiCompetitionController {

    private final CompetitionService competitionService;
    private final UserService        userService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record CompetitionResponse(
            UUID   id, String title, String description,
            String prize, String organizer, String facultyName,
            LocalDate startDate, LocalDate endDate,
            String status, int registrantCount, boolean registeredByMe,
            LocalDateTime createdAt
    ) {}

    public record CreateCompetitionBody(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 3000) String description,
            String prize, String organizer, UUID facultyId,
            String startDate, String endDate
    ) {}

    public record RegisterResponse(boolean registered, int registrantCount) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping
    @Operation(summary = "List competitions (paginated, optional status/faculty filter)")
    public ResponseEntity<ApiResponse<PageResponse<CompetitionResponse>>> list(
            @RequestParam(defaultValue = "0")  int  page,
            @RequestParam(defaultValue = "10") int  size,
            @RequestParam(required = false) String  status,
            @RequestParam(required = false) UUID    facultyId,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var comps = competitionService.search(status, facultyId, page);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                comps.map(c -> new CompetitionResponse(
                        c.getId(), c.getTitle(), c.getDescription(),
                        c.getPrize(), c.getOrganizer(),
                        c.getFaculty() != null ? c.getFaculty().getName() : null,
                        c.getStartDate(), c.getEndDate(),
                        c.getStatus(),
                        c.getRegistrations().size(),
                        competitionService.isRegistered(c.getId(), principal.getId()),
                        c.getCreatedAt()
                ))
        )));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get competition detail")
    public ResponseEntity<ApiResponse<CompetitionResponse>> detail(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var c = competitionService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(new CompetitionResponse(
                c.getId(), c.getTitle(), c.getDescription(),
                c.getPrize(), c.getOrganizer(),
                c.getFaculty() != null ? c.getFaculty().getName() : null,
                c.getStartDate(), c.getEndDate(),
                c.getStatus(),
                c.getRegistrations().size(),
                competitionService.isRegistered(id, principal.getId()),
                c.getCreatedAt()
        )));
    }

    @PostMapping
    @Operation(summary = "Create a competition (SECRETARY / MEZUN / ADMIN)")
    public ResponseEntity<ApiResponse<CompetitionResponse>> create(
            @Valid @RequestBody CreateCompetitionBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        var c = competitionService.create(user, body.title(), body.description(),
                body.prize(), body.organizer(), body.facultyId(),
                body.startDate(), body.endDate());

        return ResponseEntity.status(201).body(ApiResponse.created(new CompetitionResponse(
                c.getId(), c.getTitle(), c.getDescription(),
                c.getPrize(), c.getOrganizer(), null,
                c.getStartDate(), c.getEndDate(),
                c.getStatus(),
                0, false, c.getCreatedAt()
        )));
    }

    @PostMapping("/{id}/register")
    @Operation(summary = "Register or withdraw from a competition")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user       = userService.getById(principal.getId());
        boolean registered = competitionService.toggleRegistration(id, user);
        var comp = competitionService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(
                new RegisterResponse(registered, comp.getRegistrations().size())));
    }
}