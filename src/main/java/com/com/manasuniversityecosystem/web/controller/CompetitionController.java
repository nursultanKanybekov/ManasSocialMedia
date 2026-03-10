package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.competition.Competition;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.competition.CompetitionService;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
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
@RequestMapping("/competitions")
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;
    private final UserService userService;
    private final FacultyRepository facultyRepo;

    @GetMapping
    public String list(@RequestParam(required = false) String status,
                       @RequestParam(required = false) UUID facultyId,
                       @RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Page<Competition> competitionPage = competitionService.search(status, facultyId, page);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("competitionPage", competitionPage);
        model.addAttribute("faculties", facultyRepo.findAll());
        model.addAttribute("selectedStatus", status);
        model.addAttribute("selectedFaculty", facultyId);
        return "competitions/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Competition competition = competitionService.getById(id);
        boolean registered = competitionService.isRegistered(id, principal.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("competition", competition);
        model.addAttribute("registered", registered);
        return "competitions/detail";
    }

    @PostMapping("/{id}/register")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> register(@PathVariable UUID id,
                                                         @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        boolean registered = competitionService.toggleRegistration(id, user);
        long count = competitionService.getRegistrationCount(id);

        Map<String, Object> resp = new HashMap<>();
        resp.put("registered", registered);
        resp.put("count", count);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/new")
    public String newForm(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        model.addAttribute("currentUser", userService.getById(principal.getId()));
        model.addAttribute("faculties", facultyRepo.findAll());
        return "competitions/form";
    }

    @PostMapping("/new")
    public String create(@RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(required = false) String prize,
                         @RequestParam(required = false) String organizer,
                         @RequestParam(required = false) UUID facultyId,
                         @RequestParam(required = false) String startDate,
                         @RequestParam(required = false) String endDate,
                         @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        Competition c = competitionService.create(user, title, description, prize, organizer,
                facultyId, startDate, endDate);
        return "redirect:/competitions/" + c.getId();
    }
}
