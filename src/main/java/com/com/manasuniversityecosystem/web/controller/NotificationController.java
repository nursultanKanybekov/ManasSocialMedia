package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.notification.Notification;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifService;

    /** HTMX: unread count badge (polled every 30s from navbar) */
    @GetMapping("/count")
    public String unreadCount(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        long count = notifService.countUnread(principal.getId());
        model.addAttribute("unreadCount", count);
        return "notifications/fragments/badge :: badge";
    }

    /** HTMX: dropdown list of unread notifications */
    @GetMapping("/dropdown")
    public String dropdown(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        List<Notification> unread = notifService.getUnread(principal.getId());
        model.addAttribute("notifications", unread);
        return "notifications/fragments/dropdown :: dropdown";
    }

    /** Full notifications page */
    @GetMapping
    public String allNotifications(@AuthenticationPrincipal UserDetailsImpl principal,
                                   @RequestParam(defaultValue = "0") int page,
                                   Model model) {
        Page<Notification> notifications = notifService.getForUser(principal.getId(), page);
        long unreadCount = notifService.countUnread(principal.getId());
        model.addAttribute("notifications", notifications);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("currentPage", page);
        return "notifications/list";
    }

    /** Mark all as read (HTMX from navbar) */
    @PostMapping("/mark-all-read")
    @ResponseBody
    public ResponseEntity<Void> markAllReadHtmx(@AuthenticationPrincipal UserDetailsImpl principal) {
        notifService.markAllRead(principal.getId());
        return ResponseEntity.ok().build();
    }

    /** Mark all as read (form submit redirect) */
    @PostMapping("/read-all")
    public String markAllRead(@AuthenticationPrincipal UserDetailsImpl principal) {
        notifService.markAllRead(principal.getId());
        return "redirect:/notifications";
    }

    /** HTMX: mark one as read, returns updated badge count */
    @PostMapping("/{id}/read")
    public String markOneRead(@PathVariable UUID id,
                              @AuthenticationPrincipal UserDetailsImpl principal,
                              Model model) {
        notifService.markOneRead(id, principal.getId());
        long count = notifService.countUnread(principal.getId());
        model.addAttribute("unreadCount", count);
        return "notifications/fragments/badge :: badge";
    }

    /** Clear all read notifications */
    @PostMapping("/clear-read")
    public String clearRead(@AuthenticationPrincipal UserDetailsImpl principal) {
        notifService.clearRead(principal.getId());
        return "redirect:/notifications";
    }

    /** REST: unread count as JSON (for JS polling) */
    @GetMapping("/api/count")
    @ResponseBody
    public ResponseEntity<Map<String, Long>> countApi(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return ResponseEntity.ok(Map.of("count", notifService.countUnread(principal.getId())));
    }
}
