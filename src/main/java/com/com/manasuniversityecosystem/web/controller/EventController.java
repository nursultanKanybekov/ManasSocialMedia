package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.event.MeetingEvent;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.event.EventService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final UserService userService;

    @GetMapping
    public String list(@RequestParam(required = false) String eventType,
                       @RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Page<MeetingEvent> eventPage = eventService.search(eventType, page);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("eventPage", eventPage);
        model.addAttribute("selectedType", eventType);
        return "events/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        MeetingEvent event = eventService.getById(id);
        boolean registered = eventService.isRegistered(id, principal.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("event", event);
        model.addAttribute("registered", registered);
        return "events/detail";
    }

    @PostMapping("/{id}/register")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> register(@PathVariable UUID id,
                                                         @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        boolean registered = eventService.toggleRegistration(id, user);
        long count = eventService.getRegistrationCount(id);

        Map<String, Object> resp = new HashMap<>();
        resp.put("registered", registered);
        resp.put("count", count);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        model.addAttribute("currentUser", userService.getById(principal.getId()));
        return "events/form";
    }

    @PostMapping("/new")
    public String create(@RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String location,
                         @RequestParam(required = false) String meetingLink,
                         @RequestParam(required = false) String eventType,
                         @RequestParam(required = false) String eventDate,
                         @RequestParam(required = false) Integer maxParticipants,
                         @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        MeetingEvent event = eventService.create(user, title, description, location,
                meetingLink, eventType, eventDate, maxParticipants);
        return "redirect:/events/" + event.getId();
    }
}
