package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.ai.GeminiService;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Controller
@RequestMapping("/quiz")
@Slf4j
public class AiQuizController {

    private final FacultyRepository   facultyRepository;
    private final UserService         userService;
    private final GamificationService gamificationService;
    private final ObjectMapper        objectMapper;
    private final GeminiService       geminiService;   // Groq-first, Gemini fallback

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

    private static final Map<String, List<Map<String, Object>>> FACULTY_FALLBACK = buildFacultyFallback();

    public AiQuizController(FacultyRepository facultyRepository,
                            UserService userService,
                            GamificationService gamificationService,
                            ObjectMapper objectMapper,
                            GeminiService geminiService) {
        this.facultyRepository   = facultyRepository;
        this.userService         = userService;
        this.gamificationService = gamificationService;
        this.objectMapper        = objectMapper;
        this.geminiService       = geminiService;
    }

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
    /**
     * Generates quiz questions via GeminiService (Groq-first, Gemini fallback).
     * Falls back to the built-in faculty-aware question bank if both providers fail.
     */
    @PostMapping("/generate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> generateQuiz(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        String faculty     = body.getOrDefault("faculty",     "General");
        String facultyCode = body.getOrDefault("facultyCode", "").trim().toUpperCase();
        String subject     = body.getOrDefault("subject",     "General Knowledge");
        String lang        = body.getOrDefault("lang",        "en");
        int    count       = Math.min(Integer.parseInt(body.getOrDefault("count", "10")), 15);

        String systemPrompt = buildSystemPrompt(count);
        String userPrompt   = buildUserPrompt(faculty, subject, lang, count);

        List<Map<String, Object>> questions = null;
        String usedProvider = "built-in";

        try {
            // GeminiService tries Groq first, then Gemini automatically
            String raw = geminiService.generatePrecise(systemPrompt, userPrompt);
            String json = raw.replaceAll("(?s)```json\\s*", "")
                    .replaceAll("(?s)```\\s*", "")
                    .trim();
            int s = json.indexOf('['), e = json.lastIndexOf(']');
            if (s >= 0 && e > s) json = json.substring(s, e + 1);

            //noinspection unchecked
            questions    = objectMapper.readValue(json, List.class);
            usedProvider = "groq/gemini";
            log.info("Quiz generated via GeminiService (Groq-first) for faculty='{}' subject='{}'", faculty, subject);
        } catch (Exception ex) {
            log.warn("GeminiService failed for quiz — falling back to offline bank. Error: {}", ex.getMessage());
        }

        if (questions == null) {
            questions    = getFallbackQuestions(facultyCode, count);
            usedProvider = "built-in";
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success",   true);
        result.put("questions", questions);
        result.put("faculty",   faculty);
        result.put("subject",   subject);
        result.put("source",    usedProvider);
        if ("built-in".equals(usedProvider)) {
            result.put("notice",
                    "AI quota reached — showing offline questions for your faculty. Try again later.");
        }
        return ResponseEntity.ok(result);
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
            result.put("score",  score);
            result.put("correct", correct);
            result.put("total",   total);
            result.put("passed",  passed);

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

    // ── Prompt builders ───────────────────────────────────────────

    private String buildSystemPrompt(int count) {
        return "You are an expert university professor AI. " +
                "Generate exactly " + count + " multiple-choice quiz questions. " +
                "Return ONLY a valid JSON array — no markdown fences, no explanation text outside JSON. " +
                "Format: [{\"question\":\"...\",\"options\":[\"A\",\"B\",\"C\",\"D\"]," +
                "\"correct\":0,\"explanation\":\"...\"}] " +
                "where 'correct' is the 0-based index of the correct option. " +
                "Vary difficulty from easy to hard.";
    }

    private String buildUserPrompt(String faculty, String subject, String lang, int count) {
        String langName = switch (lang) {
            case "ru" -> "Russian (Русский)";
            case "ky" -> "Kyrgyz (Кыргызча)";
            case "tr" -> "Turkish (Türkçe)";
            default   -> "English";
        };
        String langInstruction = switch (lang) {
            case "ru" -> "IMPORTANT: Write ALL text (questions, options, explanations) entirely in Russian.";
            case "ky" -> "IMPORTANT: Write ALL text (questions, options, explanations) entirely in Kyrgyz.";
            case "tr" -> "IMPORTANT: Write ALL text (questions, options, explanations) entirely in Turkish.";
            default   -> "Write all text in English.";
        };
        return langInstruction + "\n" +
                "Language: " + langName + "\n" +
                "Faculty: " + faculty + "\n" +
                "Subject: " + subject + "\n" +
                "Number of questions: " + count + "\n" +
                langInstruction;
    }

    // ── Fallback helpers ──────────────────────────────────────────

    private List<Map<String, Object>> getFallbackQuestions(String facultyCode, int count) {
        String key = "GENERAL";
        if (facultyCode != null && !facultyCode.isBlank()) {
            String code = facultyCode.trim().toUpperCase();
            if (FACULTY_FALLBACK.containsKey(code)) key = code;
        }
        List<Map<String, Object>> pool = new ArrayList<>(
                FACULTY_FALLBACK.getOrDefault(key, FACULTY_FALLBACK.get("GENERAL")));
        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
    }

    private static Map<String, Object> buildQ(String question, List<String> options,
                                              int correct, String explanation) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("question",    question);
        q.put("options",     options);
        q.put("correct",     correct);
        q.put("explanation", explanation);
        return q;
    }

    private static Map<String, List<Map<String, Object>>> buildFacultyFallback() {
        Map<String, List<Map<String, Object>>> map = new HashMap<>();

        List<Map<String, Object>> law = new ArrayList<>();
        law.add(buildQ("What is the principle of 'presumption of innocence'?",
                List.of("A person is guilty until proven innocent","A person is innocent until proven guilty",
                        "A person may be detained without trial indefinitely","The judge decides guilt before the trial begins"),
                1, "Every accused person is considered innocent until proven guilty."));
        law.add(buildQ("What does 'habeas corpus' mean?",
                List.of("The right to a fair trial","A writ requiring a person in custody to be brought before a court",
                        "The right to remain silent","A legal plea of not guilty"),
                1, "Habeas corpus protects individuals from unlawful detention."));
        law.add(buildQ("Which branch of law deals with disputes between private individuals?",
                List.of("Criminal Law","Constitutional Law","Civil Law","Administrative Law"),
                2, "Civil law governs disputes between private parties."));
        law.add(buildQ("What is 'mens rea' in criminal law?",
                List.of("The physical act of committing a crime","The mental intent or guilty mind behind a crime",
                        "Evidence presented at trial","The victim's testimony"),
                1, "Mens rea is the mental intent required to prove guilt."));
        law.add(buildQ("What is the highest law of the land in most countries?",
                List.of("Criminal Code","Civil Code","The Constitution","International Treaties"),
                2, "The Constitution is the supreme law — all other laws must comply with it."));
        law.add(buildQ("What is 'double jeopardy'?",
                List.of("Being charged twice for the same crime","A higher penalty for repeat offenders",
                        "Two judges presiding over one case","Trying a case in two courts simultaneously"),
                0, "Double jeopardy is the prohibition against being tried twice for the same offence."));
        law.add(buildQ("What is 'stare decisis'?",
                List.of("The right to remain silent","The doctrine that courts follow precedent",
                        "A type of contract clause","Equality before the law"),
                1, "Stare decisis means courts follow prior decisions to ensure consistency."));
        law.add(buildQ("What is a 'contract' in legal terms?",
                List.of("A verbal promise between friends","A legally binding agreement between two or more parties",
                        "A government regulation","A court order"),
                1, "A contract is a legally enforceable agreement requiring offer, acceptance, and consideration."));
        map.put("LAW", Collections.unmodifiableList(law));

        List<Map<String, Object>> eco = new ArrayList<>();
        eco.add(buildQ("What does GDP stand for?",
                List.of("Gross Domestic Product","General Data Protocol","Government Debt Percentage","Gross Digital Production"),
                0, "GDP measures the total value of goods and services produced in a country."));
        eco.add(buildQ("What is inflation?",
                List.of("A decrease in the money supply","A general rise in the price level of goods and services",
                        "An increase in employment","A fall in government spending"),
                1, "Inflation is the rate at which prices rise, reducing purchasing power."));
        eco.add(buildQ("What is fiscal policy?",
                List.of("A central bank's control of interest rates","The government's use of taxation and spending to influence the economy",
                        "A company's budget planning","Trade tariff regulations"),
                1, "Fiscal policy involves government decisions on taxation and public spending."));
        eco.add(buildQ("What is opportunity cost?",
                List.of("The direct cost of producing a good","The value of the next best alternative forgone when making a choice",
                        "A hidden fee in contracts","The cost of missed investments only"),
                1, "Opportunity cost is what you give up when choosing one option over another."));
        eco.add(buildQ("What is a recession?",
                List.of("A period of rapid economic growth","Two consecutive quarters of negative GDP growth",
                        "A government budget surplus","A sharp rise in inflation"),
                1, "A recession is two consecutive quarters of declining GDP."));
        map.put("ECO", Collections.unmodifiableList(eco));

        List<Map<String, Object>> med = new ArrayList<>();
        med.add(buildQ("What is the function of red blood cells?",
                List.of("Fight infections","Carry oxygen throughout the body","Produce hormones","Regulate blood pressure"),
                1, "Red blood cells transport oxygen from lungs to tissues via haemoglobin."));
        med.add(buildQ("What is the largest organ in the human body?",
                List.of("Liver","Lungs","Brain","Skin"),
                3, "The skin is the largest organ and serves as a protective barrier."));
        med.add(buildQ("Which organ produces insulin?",
                List.of("Liver","Kidneys","Pancreas","Thyroid"),
                2, "The pancreas produces insulin to regulate blood glucose levels."));
        med.add(buildQ("What is homeostasis?",
                List.of("A surgical procedure","The body's ability to maintain a stable internal environment",
                        "A type of immune response","The process of cell division"),
                1, "Homeostasis is the body's mechanism for maintaining stable internal conditions."));
        med.add(buildQ("What is a pathogen?",
                List.of("A beneficial bacterium","A microorganism that causes disease",
                        "A type of white blood cell","A vitamin"),
                1, "A pathogen is any microorganism capable of causing disease."));
        map.put("MED", Collections.unmodifiableList(med));

        List<Map<String, Object>> ir = new ArrayList<>();
        ir.add(buildQ("What does NATO stand for?",
                List.of("National Agreement for Trade Operations","North Atlantic Treaty Organization",
                        "New Alliance of Transatlantic Organizations","Northern Allies Trade Office"),
                1, "NATO is a military alliance founded in 1949 for collective defence."));
        ir.add(buildQ("What is sovereignty?",
                List.of("The power of the military","The supreme authority of a state over its territory and people",
                        "The right to veto UN resolutions","Membership in international organizations"),
                1, "Sovereignty means a state has supreme authority within its borders."));
        ir.add(buildQ("What is diplomacy?",
                List.of("Military intervention in foreign countries","The practice of managing international relations through negotiation",
                        "Economic sanctions against rival states","Domestic policy-making"),
                1, "Diplomacy is the art of conducting negotiations between representatives of states."));
        ir.add(buildQ("What was the Cold War?",
                List.of("A military conflict in Antarctica","A geopolitical tension between the US and USSR from 1947-1991",
                        "A trade dispute between Europe and Asia","A series of wars in the Middle East"),
                1, "The Cold War was a geopolitical rivalry between the USA and USSR without direct military conflict."));
        map.put("IR", Collections.unmodifiableList(ir));

        List<Map<String, Object>> art = new ArrayList<>();
        art.add(buildQ("What is the Enlightenment?",
                List.of("A religious movement in the 10th century","An 18th-century intellectual movement emphasising reason and individual rights",
                        "A Renaissance art style","A 20th-century literary school"),
                1, "The Enlightenment promoted reason, science, and individual liberties."));
        art.add(buildQ("Who wrote '1984'?",
                List.of("Aldous Huxley","Franz Kafka","George Orwell","Leo Tolstoy"),
                2, "George Orwell wrote '1984' (1949), a dystopian novel about totalitarianism."));
        art.add(buildQ("What does 'linguistics' study?",
                List.of("History of ancient civilisations","The structure, evolution, and nature of language",
                        "Animal communication only","Political speeches"),
                1, "Linguistics is the scientific study of language and its structure."));
        map.put("ART", Collections.unmodifiableList(art));

        List<Map<String, Object>> eng = new ArrayList<>();
        eng.add(buildQ("What is an algorithm?",
                List.of("A computer program","A step-by-step procedure to solve a problem","A type of data","A network protocol"),
                1, "An algorithm is a finite sequence of instructions for solving a problem."));
        eng.add(buildQ("What does HTTP stand for?",
                List.of("HyperText Transfer Protocol","High-Tech Transfer Process","Hyperlink Text Transfer Path","Host Transfer Technology Protocol"),
                0, "HTTP is the foundation of data communication on the World Wide Web."));
        eng.add(buildQ("What is object-oriented programming?",
                List.of("Programming without functions","A paradigm organising code around objects combining data and behaviour",
                        "A low-level machine code style","Programming only in Java"),
                1, "OOP organises software design around objects featuring encapsulation, inheritance, and polymorphism."));
        eng.add(buildQ("What is the difference between TCP and UDP?",
                List.of("There is no difference","TCP is faster; UDP is slower",
                        "TCP is connection-oriented and reliable; UDP is connectionless and faster but less reliable",
                        "UDP is only used for email"),
                2, "TCP guarantees delivery and order; UDP trades reliability for speed."));
        map.put("ENG", Collections.unmodifiableList(eng));

        List<Map<String, Object>> general = new ArrayList<>();
        general.add(buildQ("What is the capital of Kyrgyzstan?",
                List.of("Osh","Bishkek","Jalal-Abad","Karakol"),
                1, "Bishkek is the capital and largest city of Kyrgyzstan."));
        general.add(buildQ("Which planet is closest to the Sun?",
                List.of("Venus","Earth","Mars","Mercury"),
                3, "Mercury is the closest planet to the Sun."));
        general.add(buildQ("What is the chemical symbol for water?",
                List.of("WA","H2O","HO","W2O"),
                1, "Water's chemical formula is H2O — two hydrogen atoms and one oxygen atom."));
        general.add(buildQ("How many continents are there on Earth?",
                List.of("5","6","7","8"),
                2, "Earth has 7 continents."));
        general.add(buildQ("What year did World War II end?",
                List.of("1943","1944","1945","1946"),
                2, "World War II ended in 1945 with the surrender of Germany and Japan."));
        general.add(buildQ("What is the speed of light?",
                List.of("300,000 km/s","150,000 km/s","500,000 km/s","100,000 km/s"),
                0, "The speed of light in a vacuum is approximately 299,792 km/s."));
        general.add(buildQ("What is photosynthesis?",
                List.of("Animals breathing oxygen","The process by which plants convert sunlight into food",
                        "The breakdown of food in digestion","The evaporation of water"),
                1, "Photosynthesis is how plants use sunlight, water, and CO2 to produce glucose and oxygen."));
        map.put("GENERAL", Collections.unmodifiableList(general));

        return Collections.unmodifiableMap(map);
    }
}