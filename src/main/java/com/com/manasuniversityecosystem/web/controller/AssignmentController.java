package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.*;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.CourseRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.academic.AssignmentService;
import com.com.manasuniversityecosystem.service.academic.AcademicCourseService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/academic/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentService     assignmentService;
    private final AcademicCourseService courseService;
    private final UserRepository        userRepo;
    private final CourseRepository      courseRepo;
    private final FacultyRepository     facultyRepo;

    // ── List assignments ───────────────────────────────────────

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String list(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        UserRole role = user.getRole();

        List<Assignment> assignments;
        if (role == UserRole.TEACHER) {
            assignments = assignmentService.getForTeacher(principal.getId());
        } else if (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN || role == UserRole.SECRETARY) {
            // Admin sees all — fetch from all courses
            assignments = new ArrayList<>();
            courseService.getAll().forEach(c ->
                    assignments.addAll(assignmentService.getForCourse(c.getId())));
            assignments.sort(Comparator.comparing(Assignment::getDeadline));
        } else {
            // Student: only assignments for their enrolled courses
            assignments = assignmentService.getForStudent(principal.getId());
        }

        // For each assignment, attach student's submission (if student)
        Map<UUID, AssignmentSubmission> mySubmissions = new HashMap<>();
        if (role == UserRole.STUDENT) {
            assignmentService.getSubmissionsForStudent(principal.getId())
                    .forEach(s -> mySubmissions.put(s.getAssignment().getId(), s));
        }

        List<Course> teacherCourses = (role == UserRole.TEACHER)
                ? courseService.getCoursesForTeacher(principal.getId())
                : Collections.emptyList();

        // For admin/super_admin: pass all faculties + all courses for the faculty→course cascade
        // For teacher: also pass faculties so they can see the faculty→course picker
        List<com.com.manasuniversityecosystem.domain.entity.Faculty> allFaculties =
                (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN || role == UserRole.TEACHER)
                        ? facultyRepo.findAllByOrderByNameAsc()
                        : Collections.emptyList();
        List<Course> allCourses =
                (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN)
                        ? courseService.getAll()
                        : Collections.emptyList();

        model.addAttribute("assignments", assignments);
        model.addAttribute("mySubmissions", mySubmissions);
        model.addAttribute("teacherCourses", teacherCourses);
        model.addAttribute("allFaculties", allFaculties);
        model.addAttribute("allCourses", allCourses);
        model.addAttribute("now", LocalDateTime.now());
        model.addAttribute("currentUser", user);
        return "academic/assignments";
    }

    // ── Assignment detail ──────────────────────────────────────

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public String detail(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        Assignment assignment = assignmentService.getById(id);
        boolean isTeacher = assignment.getCourse().getTeacher().getId().equals(principal.getId());
        boolean isAdmin   = user.getRole() == UserRole.ADMIN || user.getRole() == UserRole.SUPER_ADMIN;

        AssignmentSubmission mySubmission = (user.getRole() == UserRole.STUDENT)
                ? assignmentService.getSubmission(principal.getId(), id) : null;

        List<AssignmentSubmission> allSubmissions = (isTeacher || isAdmin)
                ? assignmentService.getSubmissionsForAssignment(id) : Collections.emptyList();

        model.addAttribute("assignment", assignment);
        model.addAttribute("mySubmission", mySubmission);
        model.addAttribute("allSubmissions", allSubmissions);
        model.addAttribute("isTeacher", isTeacher || isAdmin);
        model.addAttribute("now", LocalDateTime.now());
        model.addAttribute("currentUser", user);
        return "academic/assignment-detail";
    }

    // ── Create assignment (TEACHER) ────────────────────────────

    @PostMapping("/create")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public String create(@RequestParam UUID courseId,
                         @RequestParam String title,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam Assignment.AssignmentType type,
                         @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadline,
                         @RequestParam(defaultValue = "100") double maxScore,
                         @RequestParam(required = false) MultipartFile attachmentFile,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra) {
        try {
            Assignment a = assignmentService.create(courseId, principal.getId(),
                    title, description, type, deadline, maxScore, attachmentFile);
            ra.addFlashAttribute("success", "✅ Assignment created: " + a.getTitle());
            return "redirect:/academic/assignments/" + a.getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
            return "redirect:/academic/assignments";
        }
    }

    // ── Submit assignment (STUDENT) ────────────────────────────

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public String submit(@PathVariable UUID id,
                         @RequestParam(defaultValue = "") String textContent,
                         @RequestParam(required = false) MultipartFile file,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra) {
        try {
            assignmentService.submit(principal.getId(), id, textContent, file);
            ra.addFlashAttribute("success", "✅ Assignment submitted successfully!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/academic/assignments/" + id;
    }

    // ── Grade submission (TEACHER) ─────────────────────────────

    @PostMapping("/submissions/{submissionId}/grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> grade(
            @PathVariable UUID submissionId,
            @RequestParam double score,
            @RequestParam(defaultValue = "") String feedback,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            AssignmentSubmission sub = assignmentService.grade(submissionId, principal.getId(), score, feedback);
            return ResponseEntity.ok(Map.of("success", true, "score", sub.getScore()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Delete assignment (TEACHER / ADMIN) ────────────────────

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public String delete(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra) {
        try {
            assignmentService.delete(id, principal.getId());
            ra.addFlashAttribute("success", "Assignment deleted.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/academic/assignments";
    }
}