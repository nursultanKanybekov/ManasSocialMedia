package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Controller
@RequestMapping("/quiz")
@Slf4j
public class AiQuizController {

    private final FacultyRepository   facultyRepository;
    private final UserService         userService;
    private final GamificationService gamificationService;
    private final ObjectMapper        objectMapper;

    // Reads from application.yml: app.gemini.api-key
    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=";

    public AiQuizController(FacultyRepository facultyRepository,
                            UserService userService,
                            GamificationService gamificationService,
                            ObjectMapper objectMapper) {
        this.facultyRepository   = facultyRepository;
        this.userService         = userService;
        this.gamificationService = gamificationService;
        this.objectMapper        = objectMapper;
    }

    private static final Map<String, List<String>> FACULTY_SUBJECTS = Map.of(
            "ENG", List.of("Data Structures & Algorithms", "Operating Systems", "Database Systems",
                    "Software Engineering", "Computer Networks", "Web Development",
                    "Artificial Intelligence", "Cybersecurity"),
            "ECO", List.of("Microeconomics", "Macroeconomics", "Financial Accounting",
                    "Business Management", "Marketing", "Statistics", "International Trade"),
            "LAW", List.of("Constitutional Law", "Civil Law", "Criminal Law",
                    "International Law", "Contract Law", "Human Rights"),
            "MED", List.of("Anatomy", "Physiology", "Biochemistry",
                    "Pharmacology", "Pathology", "Medical Ethics"),
            "IR",  List.of("International Relations", "Diplomacy", "Geopolitics",
                    "Global Economics", "Political Science", "International Organizations"),
            "ART", List.of("World History", "Philosophy", "Literature",
                    "Cultural Studies", "Linguistics", "Psychology")
    );

    private static final List<String> DEFAULT_SUBJECTS = List.of(
            "General Knowledge", "Mathematics", "Critical Thinking", "Logic", "Science"
    );

    // ── GET /quiz ──────────────────────────────────────────────────
    @GetMapping
    public String quizPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user      = userService.getById(principal.getId());
        List<Faculty> all = facultyRepository.findAllByOrderByNameAsc();
        Faculty myFaculty = user.getFaculty();
        String code       = myFaculty != null ? myFaculty.getCode() : null;
        List<String> subs = code != null
                ? FACULTY_SUBJECTS.getOrDefault(code, DEFAULT_SUBJECTS)
                : DEFAULT_SUBJECTS;

        model.addAttribute("currentUser",     user);
        model.addAttribute("allFaculties",    all);
        model.addAttribute("myFaculty",       myFaculty);
        model.addAttribute("subjects",        subs);
        model.addAttribute("facultySubjects", objectMapper.valueToTree(FACULTY_SUBJECTS).toString());
        model.addAttribute("defaultSubjects", objectMapper.valueToTree(DEFAULT_SUBJECTS).toString());
        model.addAttribute("pageTitle",       "AI Knowledge Quiz");
        return "gamification/ai-quiz";
    }

    // ── POST /quiz/generate ────────────────────────────────────────
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateQuiz(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            String faculty = body.getOrDefault("faculty", "General");
            String subject = body.getOrDefault("subject", "General Knowledge");
            String lang    = body.getOrDefault("lang",    "en");
            int    count   = Math.min(Integer.parseInt(body.getOrDefault("count", "10")), 15);

            List<Map<String, Object>> questions = callGemini(buildPrompt(faculty, subject, lang, count));
            return ResponseEntity.ok(Map.of("success", true, "questions", questions,
                    "faculty", faculty, "subject", subject));
        } catch (Exception e) {
            log.error("Quiz generation failed: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── POST /quiz/submit ──────────────────────────────────────────
    @PostMapping("/submit")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> submitQuiz(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            AppUser user  = userService.getById(principal.getId());
            int correct   = Integer.parseInt(body.getOrDefault("correct", "0").toString());
            int total     = Integer.parseInt(body.getOrDefault("total",   "1").toString());
            int score     = (int) Math.round((double) correct / total * 100);
            boolean passed = score >= 70;

            Map<String, Object> result = new HashMap<>();
            result.put("score", score); result.put("correct", correct);
            result.put("total", total); result.put("passed",  passed);

            if (passed) {
                gamificationService.awardPoints(user, PointReason.QUIZ_PASS, null);
                result.put("pointsAwarded", 20);
            } else {
                result.put("pointsAwarded", 0);
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    // ── POST /quiz/subjects ────────────────────────────────────────
    @PostMapping("/subjects")
    @ResponseBody
    public ResponseEntity<List<String>> getSubjects(@RequestBody Map<String, String> body) {
        String code = body.getOrDefault("code", "");
        return ResponseEntity.ok(FACULTY_SUBJECTS.getOrDefault(code, DEFAULT_SUBJECTS));
    }

    // ── Prompt ────────────────────────────────────────────────────
    private String buildPrompt(String faculty, String subject, String lang, int count) {
        String langNote = switch (lang) {
            case "ru" -> "Respond entirely in Russian.";
            case "ky" -> "Respond entirely in Kyrgyz.";
            case "tr" -> "Respond entirely in Turkish.";
            default   -> "Respond entirely in English.";
        };
        return String.format("""
            You are an expert university professor for %s faculty students, subject: %s.
            %s
            Generate exactly %d multiple-choice questions with exactly 4 options each.
            Return ONLY a valid JSON array, no markdown, no explanation, nothing else.
            Format: [{"question":"...","options":["A","B","C","D"],"correct":0,"explanation":"..."}]
            "correct" = 0-based index of the correct option. Vary difficulty from easy to hard.
            """, faculty, subject, langNote, count);
    }

    // ── Gemini API ────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callGemini(String prompt) throws Exception {
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> reqBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        ResponseEntity<Map> resp = rest.postForEntity(
                GEMINI_API_URL + geminiApiKey,
                new HttpEntity<>(reqBody, headers),
                Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null)
            throw new RuntimeException("Gemini API error: " + resp.getStatusCode());

        List<Map<String,Object>> candidates = (List<Map<String,Object>>) resp.getBody().get("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new RuntimeException("No response from Gemini");

        String text = (String)
                ((List<Map<String,Object>>) ((Map<String,Object>) candidates.get(0).get("content")).get("parts"))
                        .get(0).get("text");

        // Clean markdown fences if Gemini adds them
        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();

        // Extract JSON array
        int s = text.indexOf('['), e = text.lastIndexOf(']');
        if (s >= 0 && e > s) text = text.substring(s, e + 1);

        return objectMapper.readValue(text, List.class);
    }
}