package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.event.EventService;
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

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Events", description = "University events and registrations")
public class ApiEventController {

    private final EventService eventService;
    private final UserService  userService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record EventResponse(
            UUID   id, String title, String description, String location,
            String meetingLink, String eventType, LocalDateTime eventDate,
            int    participantCount, Integer maxParticipants, boolean registeredByMe,
            LocalDateTime createdAt
    ) {}

    public record CreateEventBody(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 3000)          String description,
            @Size(max = 300)           String location,
            String meetingLink,
            String eventType,
            String eventDate,            // ISO-8601
            Integer maxParticipants
    ) {}

    public record RegisterResponse(boolean registered, int participantCount) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping
    @Operation(summary = "List events (paginated, optional type filter)")
    public ResponseEntity<ApiResponse<PageResponse<EventResponse>>> listEvents(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "10") int    size,
            @RequestParam(required = false)    String eventType,
            @RequestParam(defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var events = eventService.search(eventType, page);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                events.map(e -> new EventResponse(
                        e.getId(), e.getTitle(), e.getDescription(), e.getLocation(),
                        e.getMeetingLink(), e.getEventType(),
                        e.getEventDate(), e.getRegistrations().size(), e.getMaxParticipants(),
                        eventService.isRegistered(e.getId(), principal.getId()), e.getCreatedAt()
                ))
        )));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get single event detail")
    public ResponseEntity<ApiResponse<EventResponse>> getEvent(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var e = eventService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(new EventResponse(
                e.getId(), e.getTitle(), e.getDescription(), e.getLocation(),
                e.getMeetingLink(), e.getEventType(),
                e.getEventDate(), e.getRegistrations().size(), e.getMaxParticipants(),
                eventService.isRegistered(id, principal.getId()), e.getCreatedAt()
        )));
    }

    @PostMapping
    @Operation(summary = "Create an event (SECRETARY / MEZUN / ADMIN)")
    public ResponseEntity<ApiResponse<EventResponse>> createEvent(
            @Valid @RequestBody CreateEventBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user  = userService.getById(principal.getId());
        var     event = eventService.create(user, body.title(), body.description(),
                body.location(), body.meetingLink(), body.eventType(),
                body.eventDate(), body.maxParticipants());

        return ResponseEntity.status(201).body(ApiResponse.created(new EventResponse(
                event.getId(), event.getTitle(), event.getDescription(),
                event.getLocation(), event.getMeetingLink(),
                event.getEventType(),
                event.getEventDate(), 0, event.getMaxParticipants(), false, event.getCreatedAt()
        )));
    }

    @PostMapping("/{id}/register")
    @Operation(summary = "Register or unregister from an event (toggle)")
    public ResponseEntity<ApiResponse<RegisterResponse>> toggleRegister(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        boolean registered = eventService.toggleRegistration(id, user);
        var event = eventService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(
                new RegisterResponse(registered, event.getRegistrations().size())));
    }
}