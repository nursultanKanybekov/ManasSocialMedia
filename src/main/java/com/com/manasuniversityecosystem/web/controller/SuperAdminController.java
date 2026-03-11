package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/super-admin")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class SuperAdminController {

    private final FacultyRepository facultyRepository;
    private final UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    // ─── Dashboard ────────────────────────────────────────────
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("faculties", facultyRepository.findAllByOrderByNameAsc());
        model.addAttribute("totalUsers", userRepository.count());
        model.addAttribute("totalFaculties", facultyRepository.count());
        return "super-admin/dashboard";
    }

    // ─── Faculty: Add ─────────────────────────────────────────
    @PostMapping("/faculties/add")
    public String addFaculty(@RequestParam String name,
                              @RequestParam String code,
                              RedirectAttributes ra) {
        String cleanCode = code.trim().toUpperCase();
        if (facultyRepository.existsByCode(cleanCode)) {
            ra.addFlashAttribute("errorMsg", "Faculty with code '" + cleanCode + "' already exists.");
            return "redirect:/super-admin";
        }
        Faculty faculty = Faculty.builder()
                .name(name.trim())
                .code(cleanCode)
                .build();
        facultyRepository.save(faculty);
        log.info("SuperAdmin added faculty: {} ({})", name, cleanCode);
        ra.addFlashAttribute("successMsg", "Faculty '" + name.trim() + "' added successfully.");
        return "redirect:/super-admin";
    }

    // ─── Faculty: Delete ──────────────────────────────────────
    @PostMapping("/faculties/{id}/delete")
    public String deleteFaculty(@PathVariable UUID id, RedirectAttributes ra) {
        facultyRepository.findById(id).ifPresentOrElse(f -> {
            long usersInFaculty = userRepository.findAll().stream()
                    .filter(u -> u.getFaculty() != null && u.getFaculty().getId().equals(id))
                    .count();
            if (usersInFaculty > 0) {
                ra.addFlashAttribute("errorMsg",
                        "Cannot delete '" + f.getName() + "': " + usersInFaculty + " user(s) are assigned to it.");
            } else {
                facultyRepository.deleteById(id);
                log.warn("SuperAdmin deleted faculty: {}", f.getName());
                ra.addFlashAttribute("successMsg", "Faculty '" + f.getName() + "' deleted.");
            }
        }, () -> ra.addFlashAttribute("errorMsg", "Faculty not found."));
        return "redirect:/super-admin";
    }

    // ─── Drop All Tables ──────────────────────────────────────
    @PostMapping("/database/drop-all")
    @Transactional
    public String dropAllTables(@RequestParam String confirmText, RedirectAttributes ra) {
        if (!"DROP ALL TABLES".equals(confirmText)) {
            ra.addFlashAttribute("errorMsg", "Confirmation text does not match. No action taken.");
            return "redirect:/super-admin";
        }
        try {
            // Disable FK constraints, truncate all tables, re-enable
            entityManager.createNativeQuery("SET session_replication_role = replica").executeUpdate();

            entityManager.createNativeQuery("TRUNCATE TABLE " +
                "post_like, comment, post, " +
                "chat_message, chat_participant, chat_room, " +
                "job_application, job_listing, mentorship_request, " +
                "competition_registration, competition, " +
                "event_registration, meeting_event, " +
                "quiz_attempt, quiz_question, quiz, tutorial, " +
                "user_badge, point_transaction, badge, " +
                "notification, secretary_validation, " +
                "profile, app_user, faculty CASCADE"
            ).executeUpdate();

            entityManager.createNativeQuery("SET session_replication_role = DEFAULT").executeUpdate();

            log.warn("⚠️ SuperAdmin performed DROP ALL TABLES (truncate)");
            ra.addFlashAttribute("successMsg", "All tables have been cleared. The system will reinitialize on next startup.");
        } catch (Exception e) {
            log.error("Failed to truncate tables", e);
            ra.addFlashAttribute("errorMsg", "Error: " + e.getMessage());
        }
        return "redirect:/super-admin";
    }
}
