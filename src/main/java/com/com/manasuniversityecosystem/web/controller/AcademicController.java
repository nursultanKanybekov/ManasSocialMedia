package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.*;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.academic.AcademicCourseService;
import com.com.manasuniversityecosystem.service.academic.GradeService;
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
@RequestMapping("/academic")
@RequiredArgsConstructor
public class AcademicController {

    private final AcademicCourseService courseService;
    private final GradeService          gradeService;
    private final AttendanceService     attendanceService;
    private final UserRepository        userRepo;
    private final FacultyRepository     facultyRepo;

    // ── Courses list ───────────────────────────────────────────

    @GetMapping("/courses")
    @PreAuthorize("isAuthenticated()")
    public String courses(@AuthenticationPrincipal UserDetailsImpl principal,
                          @RequestParam(required = false) String q,
                          Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        UserRole role = user.getRole();

        List<Course> courses;
        if (role == UserRole.TEACHER) {
            courses = q != null && !q.isBlank()
                    ? courseService.search(q)
                    : courseService.getCoursesForTeacher(principal.getId());
        } else if (role == UserRole.ADMIN || role == UserRole.SUPER_ADMIN || role == UserRole.SECRETARY) {
            courses = q != null && !q.isBlank()
                    ? courseService.search(q)
                    : courseService.getAll();
        } else {
            // STUDENT — show their faculty's courses + enrollment status
            courses = user.getFaculty() != null
                    ? courseService.getCoursesForFaculty(user.getFaculty().getId())
                    : courseService.getAll();
        }

        List<CourseEnrollment> myEnrollments = (role == UserRole.STUDENT)
                ? courseService.getActiveEnrollmentsForStudent(principal.getId())
                : Collections.emptyList();
        Set<UUID> enrolledCourseIds = new HashSet<>();
        myEnrollments.forEach(e -> enrolledCourseIds.add(e.getCourse().getId()));

        model.addAttribute("courses", courses);
        model.addAttribute("enrolledCourseIds", enrolledCourseIds);
        model.addAttribute("allFaculties", facultyRepo.findAllByOrderByNameAsc());
        model.addAttribute("allTeachers", userRepo.findByRole(UserRole.TEACHER));
        model.addAttribute("currentUser", user);
        model.addAttribute("q", q);
        return "academic/courses";
    }

    // ── Course detail ──────────────────────────────────────────

    @GetMapping("/courses/{id}")
    @PreAuthorize("isAuthenticated()")
    public String courseDetail(@PathVariable UUID id,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        Course course = courseService.getById(id);
        boolean isTeacher = course.getTeacher().getId().equals(principal.getId());
        boolean isAdmin   = user.getRole() == UserRole.ADMIN
                || user.getRole() == UserRole.SUPER_ADMIN
                || user.getRole() == UserRole.SECRETARY;
        boolean isEnrolled = courseService.isEnrolled(principal.getId(), id);

        List<CourseEnrollment> enrolled = (isTeacher || isAdmin)
                ? courseService.getEnrolledStudents(id) : Collections.emptyList();

        Map<UUID, Double> attendanceRates = (isTeacher || isAdmin)
                ? attendanceService.getAttendanceRatesByCourse(id, enrolled)
                : Collections.emptyMap();

        model.addAttribute("course", course);
        model.addAttribute("isTeacher", isTeacher);
        model.addAttribute("isAdmin", isAdmin);
        model.addAttribute("isEnrolled", isEnrolled);
        model.addAttribute("enrolledStudents", enrolled);
        model.addAttribute("attendanceRates", attendanceRates);
        model.addAttribute("currentUser", user);
        return "academic/course-detail";
    }

    // ── Create course (TEACHER / ADMIN) ────────────────────────

    @PostMapping("/courses/create")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    public String createCourse(@RequestParam String code,
                               @RequestParam String name,
                               @RequestParam(defaultValue = "") String description,
                               @RequestParam String semester,
                               @RequestParam(defaultValue = "3") int credits,
                               @RequestParam(defaultValue = "1") String studyYear,
                               @RequestParam UUID teacherId,
                               @RequestParam UUID facultyId,
                               RedirectAttributes ra) {
        try {
            Course c = courseService.createCourse(code, name, description,
                    semester, credits, studyYear, teacherId, facultyId);
            ra.addFlashAttribute("success", "✅ Course created: " + c.getCode() + " " + c.getName());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/academic/courses";
    }

    // ── Toggle registration (TEACHER / ADMIN) ──────────────────

    @PostMapping("/courses/{id}/registration")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN','SECRETARY')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> toggleRegistration(
            @PathVariable UUID id, @RequestParam boolean open) {
        courseService.toggleRegistration(id, open);
        return ResponseEntity.ok(Map.of("success", true, "open", open));
    }

    // ── Enroll / Drop (STUDENT) ────────────────────────────────

    @PostMapping("/courses/{id}/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public String enroll(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra) {
        try {
            courseService.enroll(principal.getId(), id);
            ra.addFlashAttribute("success", "✅ Successfully enrolled!");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/academic/courses/" + id;
    }

    @PostMapping("/courses/{id}/drop")
    @PreAuthorize("hasRole('STUDENT')")
    public String drop(@PathVariable UUID id,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       RedirectAttributes ra) {
        courseService.dropCourse(principal.getId(), id);
        ra.addFlashAttribute("success", "Course dropped.");
        return "redirect:/academic/courses";
    }

    // ── Grades: Teacher enters grade ───────────────────────────

    @PostMapping("/grades/enter")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> enterGrade(
            @RequestParam UUID studentId,
            @RequestParam UUID courseId,
            @RequestParam Grade.GradeType type,
            @RequestParam double score,
            @RequestParam(defaultValue = "100") double maxScore,
            @RequestParam(defaultValue = "") String feedback,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            Grade g = gradeService.enterGrade(studentId, courseId, principal.getId(),
                    type, score, maxScore, feedback);
            return ResponseEntity.ok(Map.of("success", true,
                    "letterGrade", g.getLetterGrade(),
                    "score", g.getScore()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ── Transcript: STUDENT views own ─────────────────────────

    @GetMapping("/transcript")
    public String myTranscript(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        UUID studentId = principal.getId();
        AppUser student = userRepo.findById(studentId).orElseThrow();
        List<Grade> grades = gradeService.getGradesForStudent(studentId);
        List<CourseEnrollment> enrollments = courseService.getEnrollmentsForStudent(studentId);
        double gpa = gradeService.getStudentGpa(studentId);
        long completedCount = enrollments.stream()
                .filter(e -> e.getFinalGrade() != null).count();

        model.addAttribute("student", student);
        model.addAttribute("grades", grades);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("gpa", gpa);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("currentUser", student);
        return "academic/transcript";
    }

    // ── Transcript: TEACHER / ADMIN views any student ──────────

    @GetMapping("/transcript/{studentId}")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN','SECRETARY')")
    public String transcript(@PathVariable UUID studentId,
                             @AuthenticationPrincipal UserDetailsImpl principal,
                             Model model) {
        AppUser student = userRepo.findById(studentId).orElseThrow();
        List<Grade> grades = gradeService.getGradesForStudent(studentId);
        List<CourseEnrollment> enrollments = courseService.getEnrollmentsForStudent(studentId);
        double gpa = gradeService.getStudentGpa(studentId);
        long completedCount = enrollments.stream()
                .filter(e -> e.getFinalGrade() != null).count();

        model.addAttribute("student", student);
        model.addAttribute("grades", grades);
        model.addAttribute("enrollments", enrollments);
        model.addAttribute("gpa", gpa);
        model.addAttribute("completedCount", completedCount);
        model.addAttribute("currentUser", userRepo.findById(principal.getId()).orElseThrow());
        return "academic/transcript";
    }
}