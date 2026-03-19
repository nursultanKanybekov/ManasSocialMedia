package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.notification.Notification;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Notifications", description = "Bell notifications for the authenticated user")
public class ApiNotificationController {

    private final NotificationService notificationService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record NotificationResponse(
            UUID          id,
            String        type,
            String        message,
            String        link,
            String        icon,
            boolean       isRead,
            LocalDateTime createdAt
    ) {}

    public record UnreadCountResponse(long count) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping
    @Operation(summary = "Get paginated notifications for the authenticated user")
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var notifs = notificationService.getForUser(principal.getId(), page);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                notifs.map(n -> new NotificationResponse(
                        n.getId(), n.getType().name(), n.getMessage(),
                        n.getLink(), n.getIcon(), n.getIsRead(), n.getCreatedAt()
                ))
        )));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        long count = notificationService.countUnread(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(new UnreadCountResponse(count)));
    }

    @GetMapping("/unread")
    @Operation(summary = "Get all unread notifications (no pagination, for badge display)")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> unread(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        List<NotificationResponse> list = notificationService
                .getUnread(principal.getId()).stream()
                .map(n -> new NotificationResponse(
                        n.getId(), n.getType().name(), n.getMessage(),
                        n.getLink(), n.getIcon(), n.getIsRead(), n.getCreatedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<ApiResponse<Void>> markOneRead(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        notificationService.markOneRead(id, principal.getId());
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Void>> markAllRead(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        notificationService.markAllRead(principal.getId());
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @DeleteMapping("/read")
    @Operation(summary = "Delete all already-read notifications")
    public ResponseEntity<ApiResponse<Void>> clearRead(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        notificationService.clearRead(principal.getId());
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}