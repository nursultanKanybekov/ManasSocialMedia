package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.*;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.academic.AcademicCourseService;
import com.com.manasuniversityecosystem.service.academic.AttendanceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

@Controller
@RequestMapping("/academic/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService    attendanceService;
    private final AcademicCourseService courseService;
    private final UserRepository       userRepo;

    // ── Teacher: attendance dashboard for a course ─────────────

    @GetMapping("/course/{courseId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN','SECRETARY')")
    public String courseDashboard(@PathVariable UUID courseId,
                                  @AuthenticationPrincipal UserDetailsImpl principal,
                                  Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        Course course = courseService.getById(courseId);

        List<AttendanceSession> sessions = attendanceService.getSessionsForCourse(courseId);
        List<CourseEnrollment> enrolled  = courseService.getEnrolledStudents(courseId);
        Map<UUID, Double> rates = attendanceService.getAttendanceRatesByCourse(courseId, enrolled);

        // Active session (if any)
        AttendanceSession activeSession = sessions.stream()
                .filter(AttendanceSession::canCheckIn).findFirst().orElse(null);

        model.addAttribute("course", course);
        model.addAttribute("sessions", sessions);
        model.addAttribute("enrolled", enrolled);
        model.addAttribute("attendanceRates", rates);
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("currentUser", user);
        return "academic/attendance";
    }

    // ── Teacher: open new session → generate QR ───────────────

    @PostMapping("/open")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> openSession(
            @RequestParam UUID courseId,
            @RequestParam(defaultValue = "") String topic,
            @RequestParam(defaultValue = "15") int validMinutes,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            AttendanceSession session = attendanceService.openSession(
                    courseId, principal.getId(), topic, validMinutes);
            return ResponseEntity.ok(Map.of(
                    "success",   true,
                    "sessionId", session.getId().toString(),
                    "token",     session.getQrToken(),
                    "expiresAt", session.getExpiresAt().toString()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Teacher: close session (auto-marks absents) ────────────

    @PostMapping("/{sessionId}/close")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> closeSession(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            attendanceService.closeSession(sessionId, principal.getId());
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Student: QR scan landing page ─────────────────────────

    @GetMapping("/checkin")
    @PreAuthorize("isAuthenticated()")
    public String checkInPage(@RequestParam String token,
                              @AuthenticationPrincipal UserDetailsImpl principal,
                              Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        AttendanceSession session = attendanceService.getByToken(token);

        if (session == null || !session.canCheckIn()) {
            model.addAttribute("error", "This QR code is invalid or has expired.");
            model.addAttribute("currentUser", user);
            return "academic/checkin-result";
        }

        // Auto-submit check-in for STUDENT; show info for others
        if (user.getRole() == UserRole.STUDENT) {
            try {
                attendanceService.checkIn(token, principal.getId());
                model.addAttribute("success", true);
                model.addAttribute("course", session.getCourse().getName());
            } catch (IllegalStateException e) {
                model.addAttribute("info", e.getMessage());
            }
        } else {
            model.addAttribute("info", "You are logged in as " + user.getRole().name()
                    + ". Only students can check in.");
        }
        model.addAttribute("session", session);
        model.addAttribute("currentUser", user);
        return "academic/checkin-result";
    }

    // ── Teacher: session records ───────────────────────────────

    @GetMapping("/{sessionId}/records")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN','SECRETARY')")
    @ResponseBody
    public ResponseEntity<List<Map<String, String>>> sessionRecords(@PathVariable UUID sessionId) {
        List<Map<String, String>> result = attendanceService.getRecordsForSession(sessionId)
                .stream().map(r -> Map.of(
                        "student",     r.getStudent().getFullName(),
                        "status",      r.getStatus().name(),
                        "method",      r.getMethod().name(),
                        "checkedInAt", r.getCheckedInAt().toString()
                )).toList();
        return ResponseEntity.ok(result);
    }

    // ── Teacher: manual mark ───────────────────────────────────

    @PostMapping("/{sessionId}/mark")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> manualMark(
            @PathVariable UUID sessionId,
            @RequestParam UUID studentId,
            @RequestParam AttendanceRecord.AttendanceStatus status,
            @RequestParam(defaultValue = "") String note) {
        try {
            attendanceService.manualMark(sessionId, studentId, status, note);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Student: my attendance ─────────────────────────────────

    @GetMapping("/my")
    @PreAuthorize("hasRole('STUDENT')")
    public String myAttendance(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        List<AttendanceRecord> records = attendanceService.getRecordsForStudent(principal.getId());
        List<CourseEnrollment> enrollments = courseService.getActiveEnrollmentsForStudent(principal.getId());

        Map<UUID, Double> courseRates = new LinkedHashMap<>();
        for (CourseEnrollment e : enrollments) {
            courseRates.put(e.getCourse().getId(),
                    attendanceService.getAttendanceRate(principal.getId(), e.getCourse().getId()));
        }

        model.addAttribute("records", records);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("courseRates", courseRates);
        model.addAttribute("currentUser", user);
        return "academic/my-attendance";
    }
}