package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.exam.ExamSchedule;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.exam.ExamScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/exams")
@RequiredArgsConstructor
@Slf4j
public class ExamController {

    private final ExamScheduleService examService;
    private final FacultyRepository   facultyRepo;
    private final UserRepository      userRepo;

    /**
     * Main exam schedule page.
     * - STUDENT / TEACHER: see their own faculty's exams by default; can search all.
     * - SECRETARY / ADMIN / SUPER_ADMIN: see all exams + import button.
     * - Everyone else: can only see (no import).
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String examPage(@AuthenticationPrincipal UserDetailsImpl principal,
                           @RequestParam(value = "q",         required = false) String query,
                           @RequestParam(value = "facultyId", required = false) UUID   selectedFacultyId,
                           Model model) {

        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        UserRole role = user.getRole();
        Faculty userFaculty = user.getFaculty();

        List<ExamSchedule> exams;
        List<Faculty> allFaculties = facultyRepo.findAllByOrderByNameAsc();

        boolean isManager = role == UserRole.ADMIN
                || role == UserRole.SECRETARY
                || role == UserRole.SUPER_ADMIN;

        if (query != null && !query.isBlank()) {
            // Global search — everyone can search across all faculties
            exams = examService.search(query);
        } else if (selectedFacultyId != null) {
            // Explicit faculty filter
            exams = examService.getByFaculty(selectedFacultyId);
        } else if (isManager) {
            // Admins/secretaries see everything by default
            exams = examService.getAll();
        } else if (userFaculty != null) {
            // Students & teachers see their own faculty
            exams = examService.getByFaculty(userFaculty.getId());
        } else {
            exams = examService.getAll();
        }

        // Today's exams for the notification banner
        List<ExamSchedule> todayExams = (userFaculty != null)
                ? examService.getTodayByFaculty(userFaculty.getId())
                : examService.getTodayAll();

        model.addAttribute("exams",            exams);
        model.addAttribute("todayExams",       todayExams);
        model.addAttribute("allFaculties",     allFaculties);
        model.addAttribute("selectedFacultyId", selectedFacultyId != null ? selectedFacultyId
                : (userFaculty != null ? userFaculty.getId() : null));
        model.addAttribute("query",            query);
        model.addAttribute("isManager",        isManager);
        model.addAttribute("userFaculty",      userFaculty);
        model.addAttribute("currentUser",      user);

        return "exams/schedule";
    }

    /**
     * Import Excel — SECRETARY and ADMIN only.
     */
    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN','SUPER_ADMIN')")
    public String importExcel(@RequestParam("file")       MultipartFile file,
                              @RequestParam(value = "facultyId", required = false) UUID facultyId,
                              @RequestParam(value = "semester",  defaultValue = "2025-2026 BAHAR") String semester,
                              RedirectAttributes ra) {
        if (file.isEmpty()) {
            ra.addFlashAttribute("error", "Please select an Excel file to upload.");
            return "redirect:/exams";
        }
        try {
            int count = examService.importFromExcel(file, facultyId, semester);
            ra.addFlashAttribute("success",
                    "✅ Successfully imported " + count + " exam records.");
            // Trigger today's notifications immediately if the imported file has today's exams
            examService.sendTodayExamNotificationsAsync();
        } catch (Exception e) {
            log.error("Exam import failed", e);
            ra.addFlashAttribute("error", "❌ Import failed: " + e.getMessage());
        }
        return "redirect:/exams";
    }

    /**
     * Delete a single exam record — SECRETARY and ADMIN only.
     */
    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN','SUPER_ADMIN')")
    public String deleteExam(@PathVariable UUID id, RedirectAttributes ra) {
        examService.delete(id);
        ra.addFlashAttribute("success", "Exam record deleted.");
        return "redirect:/exams";
    }

    /**
     * Clear all exams for a faculty — SECRETARY and ADMIN only.
     */
    @PostMapping("/clear")
    @PreAuthorize("hasAnyRole('SECRETARY','ADMIN','SUPER_ADMIN')")
    public String clearFaculty(@RequestParam UUID facultyId, RedirectAttributes ra) {
        examService.deleteByFaculty(facultyId);
        ra.addFlashAttribute("success", "Exam schedule cleared for the selected faculty.");
        return "redirect:/exams";
    }
}