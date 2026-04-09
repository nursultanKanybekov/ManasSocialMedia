package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.AnalyticsService;
import com.com.manasuniversityecosystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@Controller
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final UserService      userService;
    private final FacultyRepository facultyRepo;

    /**
     * ADMIN: full platform analytics dashboard.
     * GET /analytics
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public String adminAnalytics(Model model) {
        AnalyticsService.PlatformStats stats = analyticsService.getPlatformStats();
        model.addAttribute("stats", stats);
        model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
        return "analytics/admin-analytics";
    }

    /**
     * FACULTY_ADMIN: analytics for their own faculty.
     * GET /analytics/faculty
     * ADMIN/SUPER_ADMIN may also access with ?facultyId=... for a specific faculty.
     */
    @GetMapping("/faculty")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FACULTY_ADMIN')")
    public String facultyAnalytics(
            @RequestParam(required = false) UUID facultyId,
            @AuthenticationPrincipal UserDetailsImpl principal,
            Model model) {

        UUID resolvedFacultyId = facultyId;

        // Faculty admins can only see their own faculty
        if (principal.getRole() == UserRole.FACULTY_ADMIN) {
            AppUser me = userService.getById(principal.getId());
            if (me.getFaculty() == null) {
                model.addAttribute("error", "You are not assigned to a faculty yet. Please contact an administrator.");
                return "analytics/faculty-analytics";
            }
            resolvedFacultyId = me.getFaculty().getId();
        }

        if (resolvedFacultyId == null) {
            // Admin didn't pick a faculty yet — show faculty picker
            model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
            model.addAttribute("pickFaculty", true);
            return "analytics/faculty-analytics";
        }

        AnalyticsService.FacultyAnalytics fa = analyticsService.getFacultyAnalytics(resolvedFacultyId);
        model.addAttribute("fa", fa);
        model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
        model.addAttribute("selectedFacultyId", resolvedFacultyId);
        model.addAttribute("isAdmin",
                principal.getRole() == UserRole.ADMIN || principal.getRole() == UserRole.SUPER_ADMIN);
        return "analytics/faculty-analytics";
    }
}