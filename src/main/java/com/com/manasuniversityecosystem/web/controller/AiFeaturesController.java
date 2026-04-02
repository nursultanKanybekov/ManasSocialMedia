package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.CourseEnrollment;
import com.com.manasuniversityecosystem.domain.entity.academic.Grade;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.CourseEnrollmentRepository;
import com.com.manasuniversityecosystem.repository.academic.GradeRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.academic.AcademicCourseService;
import com.com.manasuniversityecosystem.service.ai.GeminiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiFeaturesController {

    private final GeminiService            geminiService;
    private final UserRepository           userRepo;
    private final AcademicCourseService    courseService;
    private final CourseEnrollmentRepository enrollmentRepo;
    private final GradeRepository          gradeRepo;

    // ─────────────────────────────────────────────────────────────
    // 1.  STUDY ASSISTANT — course-aware chatbot
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/study")
    @PreAuthorize("isAuthenticated()")
    public String studyPage(@AuthenticationPrincipal UserDetailsImpl principal,
                            @RequestParam(required = false) UUID courseId,
                            Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        // Students see their enrolled courses; teachers see courses they teach
        List<CourseEnrollment> enrollments =
                (user.getRole() == UserRole.STUDENT)
                        ? enrollmentRepo.findActiveByStudent(principal.getId())
                        : Collections.emptyList();
        var teacherCourses = (user.getRole() == UserRole.TEACHER)
                ? courseService.getCoursesForTeacher(principal.getId())
                : Collections.emptyList();

        model.addAttribute("enrollments",    enrollments);
        model.addAttribute("teacherCourses", teacherCourses);
        model.addAttribute("selectedCourseId", courseId);
        model.addAttribute("currentUser",    user);
        return "ai/features/study-assistant";
    }

    /** AJAX: send a chat message for the study assistant */
    @PostMapping("/study/chat")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> studyChat(
            @RequestBody Map<String, Object> body) {
        try {
            String courseName = (String) body.getOrDefault("courseName", "General Studies");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages =
                    (List<Map<String, String>>) body.getOrDefault("messages", List.of());

            String system = "You are a study assistant for Manas University, Bishkek. " +
                    "Help the student with course: " + courseName + ". " +
                    "Answer clearly with examples. Reply in the same language as the student (EN/RU/KY/TR). " +
                    "Be concise and encouraging.";

            String reply = geminiService.generate(system, messages);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            log.error("Study chat error", e);
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 2.  ESSAY FEEDBACK
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/essay")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','SUPER_ADMIN')")
    public String essayPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        model.addAttribute("currentUser", userRepo.findById(principal.getId()).orElseThrow());
        return "ai/features/essay-feedback";
    }

    /** AJAX: analyse an essay and return structured JSON feedback */
    @PostMapping("/essay/analyse")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','SUPER_ADMIN')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> essayAnalyse(
            @RequestBody Map<String, String> body) {
        try {
            String essayText = body.getOrDefault("text", "").trim();
            String essayType = body.getOrDefault("type", "Academic Essay");
            if (essayText.length() < 50)
                return ResponseEntity.ok(Map.of("error", "Essay is too short. Please submit at least 50 characters."));

            String system = "You are an academic writing evaluator for Manas University. " +
                    "Analyse this " + essayType + " and respond ONLY with valid JSON (no markdown): " +
                    "{\"overallScore\":<0-100>,\"grades\":{\"grammar\":<0-10>,\"structure\":<0-10>," +
                    "\"clarity\":<0-10>,\"content\":<0-10>,\"originality\":<0-10>}," +
                    "\"summary\":\"<2 sentences>\",\"strengths\":[\"s1\",\"s2\",\"s3\"]," +
                    "\"improvements\":[\"i1\",\"i2\",\"i3\"],\"plagiarismRisk\":\"LOW|MEDIUM|HIGH\"," +
                    "\"plagiarismNote\":\"<brief>\",\"rewrittenIntro\":\"<improved first paragraph>\"}";

            // generateLarge uses 4096 tokens — essays with rewritten intros can be long
            String raw = geminiService.generateLarge(system, "Essay to evaluate:\n\n" + essayText);
            // Strip any accidental markdown fences
            String json = raw.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
            json = repairJson(json);
            // Parse to validate and return as object
            @SuppressWarnings("unchecked")
            Map<String, Object> result = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Essay analyse error", e);
            return ResponseEntity.ok(Map.of("error", "Could not analyse essay: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 3.  AUTO FLASHCARDS
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/flashcards")
    @PreAuthorize("isAuthenticated()")
    public String flashcardsPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        model.addAttribute("currentUser", userRepo.findById(principal.getId()).orElseThrow());
        return "ai/features/flashcards";
    }

    /** AJAX: generate flashcards from lecture notes */
    @PostMapping("/flashcards/generate")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateFlashcards(
            @RequestBody Map<String, String> body) {
        try {
            String notes   = body.getOrDefault("notes", "").trim();
            String subject = body.getOrDefault("subject", "General");
            int    count   = Math.min(20, Math.max(5, Integer.parseInt(
                    body.getOrDefault("count", "10"))));

            if (notes.length() < 30)
                return ResponseEntity.ok(Map.of("error", "Notes are too short. Please provide more content."));

            String system = "Create exactly " + count + " flashcards from lecture notes about: " + subject + ". " +
                    "Respond ONLY with valid JSON (no markdown): " +
                    "{\"subject\":\"" + subject + "\",\"cards\":[{\"question\":\"...\",\"answer\":\"...\"},...]}. " +
                    "Questions must be specific and test real understanding. Answers: 1-3 sentences. " +
                    "Reply in the same language as the notes.";

            // generateLarge uses 4096 tokens — prevents JSON truncation on 10-20 card sets
            String raw = geminiService.generateLarge(system, "Lecture notes:\n\n" + notes);
            String json = raw.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
            // Safety: if JSON is truncated, try to close the array/object gracefully
            json = repairJson(json);
            @SuppressWarnings("unchecked")
            Map<String, Object> result = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Flashcard generation error", e);
            return ResponseEntity.ok(Map.of("error", "Could not generate flashcards: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 4.  ADMIN / UNIVERSITY FAQ CHATBOT
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/faq")
    @PreAuthorize("isAuthenticated()")
    public String faqPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        model.addAttribute("currentUser", user);
        model.addAttribute("isAdmin",
                user.getRole() == UserRole.ADMIN ||
                        user.getRole() == UserRole.SUPER_ADMIN ||
                        user.getRole() == UserRole.SECRETARY);
        return "ai/features/faq-bot";
    }

    /** AJAX: answer a university FAQ question */
    @PostMapping("/faq/ask")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> faqAsk(@RequestBody Map<String, Object> body) {
        try {
            String question = (String) body.getOrDefault("question", "");
            @SuppressWarnings("unchecked")
            List<Map<String, String>> history =
                    (List<Map<String, String>>) body.getOrDefault("history", List.of());

            String system = "You are the 24/7 virtual assistant for Kyrgyz-Turkish Manas University, Bishkek. " +
                    "Answer questions about enrollment, exams, grading, dormitories, scholarships, " +
                    "library, student clubs, administrative procedures and campus services. " +
                    "Be professional and friendly. If you don't know a specific date or number, say so and " +
                    "suggest contacting the department. Reply in the same language as the user (EN/RU/KY/TR).";

            // Add current question to history
            List<Map<String, String>> messages = new ArrayList<>(history);
            messages.add(Map.of("role", "user", "content", question));

            String reply = geminiService.generate(system, messages);
            return ResponseEntity.ok(Map.of("reply", reply));
        } catch (Exception e) {
            log.error("FAQ chatbot error", e);
            return ResponseEntity.ok(Map.of("error", e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 5.  AI CAREER PATH ADVISOR
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/career-advisor")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN','SUPER_ADMIN')")
    public String careerPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        model.addAttribute("currentUser", user);
        return "ai/features/career-advisor";
    }

    /** AJAX: generate personalised career recommendations */
    @PostMapping("/career-advisor/recommend")
    @PreAuthorize("isAuthenticated()")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> careerRecommend(
            @AuthenticationPrincipal UserDetailsImpl principal,
            @RequestBody Map<String, String> body) {
        try {
            AppUser user = userRepo.findById(principal.getId()).orElseThrow();

            // Gather academic context from DB
            List<CourseEnrollment> enrollments = enrollmentRepo.findActiveByStudent(principal.getId());
            List<Grade> grades = gradeRepo.findByStudentIdOrderByCourse_CodeAscTypeAsc(principal.getId());
            Double avgScore = gradeRepo.avgScoreForStudent(principal.getId());

            String interests  = body.getOrDefault("interests", "");
            String strengths  = body.getOrDefault("strengths", "");
            String targetRole = body.getOrDefault("targetRole", "");

            // Build academic profile string
            String courseList = enrollments.stream()
                    .map(e -> "- " + e.getCourse().getName() + " (" + e.getCourse().getCode() + ")")
                    .collect(Collectors.joining("\n"));

            String gradeList = grades.stream()
                    .map(g -> g.getCourse().getCode() + " " + g.getType() + ": "
                            + g.getScore() + "/" + g.getMaxScore()
                            + " (" + (g.getLetterGrade() != null ? g.getLetterGrade() : "?") + ")")
                    .collect(Collectors.joining("\n"));

            String faculty = user.getFaculty() != null ? user.getFaculty().getName() : "Unknown";
            String gpa     = avgScore != null ? String.format("%.1f%%", avgScore) : "No grades yet";

            String system = "You are a career advisor for Manas University students. " +
                    "Analyse the student's academic profile and give career recommendations. " +
                    "Respond ONLY with valid JSON (no markdown): " +
                    "{\"summary\":\"<2 sentences>\",\"topCareers\":[{\"title\":\"...\",\"match\":<0-100>," +
                    "\"why\":\"...\",\"skills\":[\"s1\",\"s2\"],\"nextSteps\":[\"n1\",\"n2\"]}]," +
                    "\"immediateActions\":[\"a1\",\"a2\",\"a3\"]," +
                    "\"strengthsFound\":[\"s1\",\"s2\"],\"gapsToBridge\":[\"g1\",\"g2\"]}. " +
                    "Include 3 career paths. Be specific and realistic.";

            String userMsg = """
                    Student Profile:
                    - Faculty: %s
                    - Average score: %s
                    - Enrolled courses:
                    %s
                    - Recent grades:
                    %s
                    - Student's interests: %s
                    - Student's self-assessed strengths: %s
                    - Target role (if any): %s
                    
                    Please analyse this profile and provide career recommendations.
                    """.formatted(faculty, gpa,
                    courseList.isBlank() ? "No active enrollments" : courseList,
                    gradeList.isBlank()  ? "No grades yet"        : gradeList,
                    interests.isBlank()  ? "Not specified"        : interests,
                    strengths.isBlank()  ? "Not specified"        : strengths,
                    targetRole.isBlank() ? "Not specified"        : targetRole);

            String raw = geminiService.generatePrecise(system, userMsg);
            String json = raw.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
            @SuppressWarnings("unchecked")
            Map<String, Object> result = new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Career advisor error", e);
            return ResponseEntity.ok(Map.of("error", "Could not generate recommendations: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Utility: attempt to repair a JSON string that was truncated
    // mid-stream by the AI provider hitting its token limit.
    // Handles the two most common cases:
    //   • Truncated inside a JSON array  → appends ]}
    //   • Truncated inside a JSON object → appends }
    // ─────────────────────────────────────────────────────────────
    private String repairJson(String json) {
        if (json == null || json.isBlank()) return json;
        String trimmed = json.trim();
        // Count opening vs closing braces/brackets to detect truncation
        long opens  = trimmed.chars().filter(c -> c == '{').count();
        long closes = trimmed.chars().filter(c -> c == '}').count();
        long arrO   = trimmed.chars().filter(c -> c == '[').count();
        long arrC   = trimmed.chars().filter(c -> c == ']').count();

        // Remove any trailing incomplete key-value (last comma or unclosed string)
        String repaired = trimmed;
        // Strip trailing comma before we close
        repaired = repaired.replaceAll(",\\s*$", "");
        // Strip a trailing open string that was never closed
        repaired = repaired.replaceAll("\"[^\"]*$", "\"\"");

        // Close arrays first, then objects
        StringBuilder sb = new StringBuilder(repaired);
        for (long i = arrC; i < arrO; i++) sb.append("]");
        for (long i = closes; i < opens; i++) sb.append("}");
        return sb.toString();
    }
}