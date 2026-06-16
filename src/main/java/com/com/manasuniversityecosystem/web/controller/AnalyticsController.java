package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.AnalyticsExcelService;
import com.com.manasuniversityecosystem.service.AnalyticsService;
import com.com.manasuniversityecosystem.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.UUID;

@Controller
@RequestMapping("/analytics")
@RequiredArgsConstructor
public class AnalyticsController {

    private final AnalyticsService      analyticsService;
    private final AnalyticsExcelService excelService;
    private final UserService           userService;
    private final FacultyRepository     facultyRepo;

    /* ── ADMIN: full platform analytics dashboard ─────────────────── */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public String adminAnalytics(Model model) {
        model.addAttribute("stats", analyticsService.getPlatformStats());
        model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
        return "analytics/admin-analytics";
    }

    /* ── ADMIN: download full analytics as Excel ──────────────────── */
    @GetMapping("/download")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadAdminAnalytics() throws IOException {
        byte[] data = excelService.exportAdminAnalytics();
        return xlsxResponse(data, "university-analytics.xlsx");
    }

    /* ── ADMIN: download blank import template ────────────────────── */
    @GetMapping("/template")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadAdminTemplate() throws IOException {
        byte[] data = excelService.generateImportTemplate();
        return xlsxResponse(data, "user-import-template.xlsx");
    }

    /* ── ADMIN: upload users from Excel ──────────────────────────── */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
    public String uploadAdminUsers(@RequestParam("file") MultipartFile file,
                                   RedirectAttributes ra) throws IOException {
        ra.addFlashAttribute("importResult", excelService.importUsers(file, null));
        return "redirect:/analytics";
    }

    /* ── FACULTY: analytics for one faculty ──────────────────────── */
    @GetMapping("/faculty")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FACULTY_ADMIN')")
    public String facultyAnalytics(@RequestParam(required = false) UUID facultyId,
                                   @AuthenticationPrincipal UserDetailsImpl principal,
                                   Model model) {
        UUID resolvedId = resolveFacultyId(facultyId, principal);

        if (resolvedId == null && principal.getRole() == UserRole.FACULTY_ADMIN) {
            model.addAttribute("error", "You are not assigned to a faculty yet.");
            return "analytics/faculty-analytics";
        }
        if (resolvedId == null) {
            model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
            model.addAttribute("pickFaculty", true);
            return "analytics/faculty-analytics";
        }

        model.addAttribute("fa", analyticsService.getFacultyAnalytics(resolvedId));
        model.addAttribute("faculties", facultyRepo.findAllByOrderByNameAsc());
        model.addAttribute("selectedFacultyId", resolvedId);
        model.addAttribute("isAdmin",
                principal.getRole() == UserRole.ADMIN || principal.getRole() == UserRole.SUPER_ADMIN);
        return "analytics/faculty-analytics";
    }

    /* ── FACULTY: download faculty analytics as Excel ────────────── */
    @GetMapping("/faculty/download")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FACULTY_ADMIN')")
    public ResponseEntity<byte[]> downloadFacultyAnalytics(
            @RequestParam(required = false) UUID facultyId,
            @AuthenticationPrincipal UserDetailsImpl principal) throws IOException {

        UUID resolvedId = resolveFacultyId(facultyId, principal);
        if (resolvedId == null) return ResponseEntity.badRequest().build();

        byte[] data = excelService.exportFacultyAnalytics(resolvedId);
        return xlsxResponse(data, "faculty-analytics.xlsx");
    }

    /* ── FACULTY: download blank import template ─────────────────── */
    @GetMapping("/faculty/template")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FACULTY_ADMIN')")
    public ResponseEntity<byte[]> downloadFacultyTemplate() throws IOException {
        byte[] data = excelService.generateImportTemplate();
        return xlsxResponse(data, "faculty-user-import-template.xlsx");
    }

    /* ── FACULTY: upload users for a faculty from Excel ──────────── */
    @PostMapping("/faculty/upload")
    @PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN','FACULTY_ADMIN')")
    public String uploadFacultyUsers(@RequestParam("file") MultipartFile file,
                                     @RequestParam(required = false) UUID facultyId,
                                     @AuthenticationPrincipal UserDetailsImpl principal,
                                     RedirectAttributes ra) throws IOException {
        UUID resolvedId = resolveFacultyId(facultyId, principal);
        ra.addFlashAttribute("importResult", excelService.importUsers(file, resolvedId));
        String redirect = "/analytics/faculty" + (resolvedId != null ? "?facultyId=" + resolvedId : "");
        return "redirect:" + redirect;
    }

    /* ── Helpers ──────────────────────────────────────────────────── */

    private UUID resolveFacultyId(UUID facultyId, UserDetailsImpl principal) {
        if (principal.getRole() == UserRole.FACULTY_ADMIN) {
            AppUser me = userService.getById(principal.getId());
            return me.getFaculty() != null ? me.getFaculty().getId() : null;
        }
        return facultyId;
    }

    private ResponseEntity<byte[]> xlsxResponse(byte[] data, String filename) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(data.length)
                .body(data);
    }
}