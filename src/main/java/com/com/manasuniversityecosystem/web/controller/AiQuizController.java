package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
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

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    // Fallback model chain: try each in order until one works
    private static final String[] GEMINI_MODELS = {
            "gemini-1.5-flash-8b",
            "gemini-1.5-flash",
            "gemini-2.0-flash"
    };

    private static final String GEMINI_BASE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=";

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

    // ── Faculty-aware fallback question bank ──────────────────────
    private static final Map<String, List<Map<String, Object>>> FACULTY_FALLBACK = buildFacultyFallback();

    private static Map<String, List<Map<String, Object>>> buildFacultyFallback() {
        Map<String, List<Map<String, Object>>> map = new HashMap<>();

        // ── LAW ───────────────────────────────────────────────────
        List<Map<String, Object>> law = new ArrayList<>();
        law.add(buildQ("What is the principle of 'presumption of innocence'?",
                List.of("A person is guilty until proven innocent",
                        "A person is innocent until proven guilty",
                        "A person may be detained without trial indefinitely",
                        "The judge decides guilt before the trial begins"),
                1, "The presumption of innocence is a fundamental right — every accused person is considered innocent until proven guilty."));
        law.add(buildQ("What does 'habeas corpus' mean?",
                List.of("The right to a fair trial",
                        "A writ requiring a person in custody to be brought before a court",
                        "The right to remain silent",
                        "A legal plea of not guilty"),
                1, "Habeas corpus is a legal writ that protects individuals from unlawful detention by requiring the detaining authority to justify the imprisonment."));
        law.add(buildQ("Which branch of law deals with disputes between private individuals?",
                List.of("Criminal Law", "Constitutional Law", "Civil Law", "Administrative Law"),
                2, "Civil law governs disputes between private parties, such as contracts, property, and family matters."));
        law.add(buildQ("What is 'mens rea' in criminal law?",
                List.of("The physical act of committing a crime",
                        "The mental intent or guilty mind behind a crime",
                        "Evidence presented at trial",
                        "The victim's testimony"),
                1, "Mens rea refers to the criminal intent — the mental element required to prove guilt in most criminal offences."));
        law.add(buildQ("What is the highest law of the land in most countries?",
                List.of("Criminal Code", "Civil Code", "The Constitution", "International Treaties"),
                2, "The Constitution is the supreme law — all other laws must comply with its provisions."));
        law.add(buildQ("What does 'due process' mean?",
                List.of("Fast-tracking a court case",
                        "Fair treatment through the normal judicial system",
                        "The right to appeal any decision",
                        "A mandatory jury trial"),
                1, "Due process guarantees fair legal proceedings and protects individuals from arbitrary government action."));
        law.add(buildQ("What is 'double jeopardy'?",
                List.of("Being charged twice for the same crime",
                        "A higher penalty for repeat offenders",
                        "Two judges presiding over one case",
                        "Trying a case in two different courts simultaneously"),
                0, "Double jeopardy is the prohibition against being tried twice for the same offence after acquittal or conviction."));
        law.add(buildQ("Which international body is the primary source of international law?",
                List.of("NATO", "The United Nations", "The World Bank", "Interpol"),
                1, "The United Nations creates and codifies much of international law through treaties, resolutions, and conventions."));
        law.add(buildQ("What is 'stare decisis'?",
                List.of("The right to remain silent",
                        "The doctrine that courts follow precedent",
                        "A type of contract clause",
                        "The principle of equality before the law"),
                1, "Stare decisis means courts follow prior decisions (precedents) to ensure consistency and predictability in the law."));
        law.add(buildQ("What is the difference between a crime and a tort?",
                List.of("There is no difference",
                        "A crime is a wrong against the state; a tort is a civil wrong against a private party",
                        "A tort is more serious than a crime",
                        "Crimes are only punishable by fines"),
                1, "Crimes are wrongs against society prosecuted by the state; torts are civil wrongs for which the injured party can sue for damages."));
        law.add(buildQ("What does 'actus reus' mean?",
                List.of("The legal defense used in a case",
                        "The physical act or conduct that constitutes a criminal offence",
                        "The judge's final ruling",
                        "The prosecution's burden of proof"),
                1, "Actus reus is the physical element of a crime — the guilty act — which must be proven alongside mens rea."));
        law.add(buildQ("What is judicial review?",
                List.of("A review of a judge's salary",
                        "The power of courts to examine the constitutionality of laws",
                        "A process for appointing new judges",
                        "An appeal to a higher court"),
                1, "Judicial review allows courts to invalidate laws or government actions that violate the constitution."));
        law.add(buildQ("What is the 'rule of law'?",
                List.of("Only lawyers can make laws",
                        "The government is above the law",
                        "Everyone, including the government, is subject to the law",
                        "Laws apply only to citizens, not foreigners"),
                2, "The rule of law means no person or institution is above the law, and laws are applied equally and fairly."));
        law.add(buildQ("Which document is considered the first modern human rights treaty?",
                List.of("The US Constitution",
                        "The Magna Carta",
                        "The Universal Declaration of Human Rights",
                        "The Geneva Convention"),
                2, "The Universal Declaration of Human Rights (1948) is the foundational international human rights document."));
        law.add(buildQ("What is a 'contract' in legal terms?",
                List.of("A verbal promise between friends",
                        "A legally binding agreement between two or more parties",
                        "A government regulation",
                        "A court order"),
                1, "A contract is a legally enforceable agreement that requires offer, acceptance, and consideration."));
        map.put("LAW", Collections.unmodifiableList(law));

        // ── ECO / Business ────────────────────────────────────────
        List<Map<String, Object>> eco = new ArrayList<>();
        eco.add(buildQ("What does GDP stand for?",
                List.of("Gross Domestic Product","General Data Protocol","Government Debt Percentage","Gross Digital Production"),
                0, "GDP measures the total monetary value of all goods and services produced in a country."));
        eco.add(buildQ("What is inflation?",
                List.of("A decrease in the money supply",
                        "A general rise in the price level of goods and services",
                        "An increase in employment",
                        "A fall in government spending"),
                1, "Inflation is the rate at which the general level of prices rises, reducing purchasing power."));
        eco.add(buildQ("What is the law of supply and demand?",
                List.of("Supply always exceeds demand",
                        "Price rises when supply increases",
                        "Price rises when demand exceeds supply, and falls when supply exceeds demand",
                        "Demand determines production costs"),
                2, "The law of supply and demand states that prices adjust until the quantity supplied equals quantity demanded."));
        eco.add(buildQ("What is a monopoly?",
                List.of("A market with many competing firms",
                        "A market dominated by a single seller",
                        "A government-owned enterprise",
                        "A joint venture between two companies"),
                1, "A monopoly exists when one company dominates an entire market, controlling prices with no competition."));
        eco.add(buildQ("What is fiscal policy?",
                List.of("A central bank's control of interest rates",
                        "The government's use of taxation and spending to influence the economy",
                        "A company's budget planning",
                        "Trade tariff regulations"),
                1, "Fiscal policy involves government decisions on taxation and public spending to manage economic conditions."));
        eco.add(buildQ("What is opportunity cost?",
                List.of("The direct cost of producing a good",
                        "The value of the next best alternative forgone when making a choice",
                        "A hidden fee in contracts",
                        "The cost of missed investment opportunities only"),
                1, "Opportunity cost is what you give up when choosing one option over another — the cost of the road not taken."));
        eco.add(buildQ("What is a recession?",
                List.of("A period of rapid economic growth",
                        "Two consecutive quarters of negative GDP growth",
                        "A government budget surplus",
                        "A sharp rise in inflation"),
                1, "A recession is defined as two consecutive quarters of declining GDP, indicating an economic contraction."));
        eco.add(buildQ("What does a central bank do?",
                List.of("Manages personal bank accounts",
                        "Controls monetary policy and regulates the banking system",
                        "Collects taxes",
                        "Issues business licenses"),
                1, "Central banks control money supply, set interest rates, and maintain financial stability."));
        eco.add(buildQ("What is the difference between microeconomics and macroeconomics?",
                List.of("There is no difference",
                        "Microeconomics studies individual markets; macroeconomics studies the economy as a whole",
                        "Microeconomics is about government; macroeconomics is about business",
                        "Macroeconomics applies only to developing countries"),
                1, "Microeconomics analyzes individual consumers and firms; macroeconomics examines aggregates like GDP, inflation, and unemployment."));
        eco.add(buildQ("What is comparative advantage?",
                List.of("The ability to produce all goods more efficiently",
                        "The ability to produce a good at a lower opportunity cost than another producer",
                        "Having the largest economy in a region",
                        "Dominating global trade routes"),
                1, "Comparative advantage is the basis for international trade — countries specialize in goods they produce at a relatively lower opportunity cost."));
        map.put("ECO", Collections.unmodifiableList(eco));

        // ── MED / Medicine ────────────────────────────────────────
        List<Map<String, Object>> med = new ArrayList<>();
        med.add(buildQ("What is the function of red blood cells?",
                List.of("Fight infections","Carry oxygen throughout the body","Produce hormones","Regulate blood pressure"),
                1, "Red blood cells (erythrocytes) contain haemoglobin which binds to oxygen and transports it from the lungs to tissues."));
        med.add(buildQ("What is the largest organ in the human body?",
                List.of("Liver","Lungs","Brain","Skin"),
                3, "The skin is the largest organ, covering the entire body and serving as a protective barrier."));
        med.add(buildQ("What does DNA stand for?",
                List.of("Deoxyribonucleic Acid","Double Nitrogen Acid","Dinucleotide Amino Acid","Deoxyribose Nitramine Acid"),
                0, "DNA (Deoxyribonucleic Acid) carries the genetic information of all living organisms."));
        med.add(buildQ("Which organ produces insulin?",
                List.of("Liver","Kidneys","Pancreas","Thyroid"),
                2, "The pancreas produces insulin, which regulates blood glucose levels."));
        med.add(buildQ("What is homeostasis?",
                List.of("A surgical procedure",
                        "The body's ability to maintain a stable internal environment",
                        "A type of immune response",
                        "The process of cell division"),
                1, "Homeostasis refers to the body's mechanisms for maintaining stable conditions (temperature, pH, glucose) despite external changes."));
        med.add(buildQ("What is the normal resting heart rate for an adult?",
                List.of("20-40 bpm","60-100 bpm","120-160 bpm","40-50 bpm"),
                1, "A normal adult resting heart rate is 60-100 beats per minute."));
        med.add(buildQ("Which part of the brain controls breathing and heart rate?",
                List.of("Cerebrum","Cerebellum","Brainstem (medulla oblongata)","Hippocampus"),
                2, "The brainstem, particularly the medulla oblongata, controls vital automatic functions like breathing and heart rate."));
        med.add(buildQ("What is the role of the liver?",
                List.of("Pumping blood",
                        "Filtering air in the lungs",
                        "Detoxifying chemicals, producing bile, and metabolising nutrients",
                        "Producing red blood cells only"),
                2, "The liver performs over 500 functions including detoxification, protein synthesis, and bile production for digestion."));
        med.add(buildQ("What is a pathogen?",
                List.of("A beneficial bacterium",
                        "A microorganism that causes disease",
                        "A type of white blood cell",
                        "A vitamin"),
                1, "A pathogen is any microorganism (bacteria, virus, fungus, parasite) capable of causing disease."));
        med.add(buildQ("What does the immune system do?",
                List.of("Regulates body temperature",
                        "Defends the body against pathogens and foreign substances",
                        "Controls hormone production",
                        "Manages digestion"),
                1, "The immune system identifies and destroys pathogens, foreign cells, and abnormal cells to protect health."));
        map.put("MED", Collections.unmodifiableList(med));

        // ── IR / International Relations ──────────────────────────
        List<Map<String, Object>> ir = new ArrayList<>();
        ir.add(buildQ("What does NATO stand for?",
                List.of("National Agreement for Trade Operations",
                        "North Atlantic Treaty Organization",
                        "New Alliance of Transatlantic Organizations",
                        "Northern Allies Trade Office"),
                1, "NATO (North Atlantic Treaty Organization) is a military alliance founded in 1949 for collective defence."));
        ir.add(buildQ("What is the United Nations' primary mission?",
                List.of("Promoting global trade",
                        "Maintaining international peace and security",
                        "Controlling world currency",
                        "Governing all member states"),
                1, "The UN was founded in 1945 to maintain peace, promote human rights, and foster international cooperation."));
        ir.add(buildQ("What is sovereignty?",
                List.of("The power of the military",
                        "The supreme authority of a state over its territory and people",
                        "The right to veto UN resolutions",
                        "Membership in international organizations"),
                1, "Sovereignty is the principle that a state has supreme authority within its borders, free from external interference."));
        ir.add(buildQ("What is diplomacy?",
                List.of("Military intervention in foreign countries",
                        "The practice of managing international relations through negotiation",
                        "Economic sanctions against rival states",
                        "Domestic policy-making"),
                1, "Diplomacy is the art and practice of conducting negotiations between representatives of states."));
        ir.add(buildQ("What was the Cold War?",
                List.of("A military conflict in Antarctica",
                        "A geopolitical tension between the US and USSR from 1947-1991",
                        "A trade dispute between Europe and Asia",
                        "A series of wars in the Middle East"),
                1, "The Cold War was a period of geopolitical rivalry between the USA and USSR characterised by ideological conflict and arms race, without direct military confrontation."));
        ir.add(buildQ("What does 'balance of power' mean in IR?",
                List.of("Equal voting rights in the UN",
                        "Economic equality between nations",
                        "A distribution of power preventing any one state from dominating all others",
                        "Military parity between two countries"),
                2, "Balance of power is a concept where states form alliances to prevent a single country from gaining hegemonic control."));
        map.put("IR", Collections.unmodifiableList(ir));

        // ── ART / Humanities ──────────────────────────────────────
        List<Map<String, Object>> art = new ArrayList<>();
        art.add(buildQ("What is the Enlightenment?",
                List.of("A religious movement in the 10th century",
                        "An 18th-century intellectual movement emphasising reason and individual rights",
                        "A Renaissance art style",
                        "A 20th-century literary school"),
                1, "The Enlightenment was an 18th-century philosophical movement that promoted reason, science, and individual liberties."));
        art.add(buildQ("What is Socrates famous for?",
                List.of("Writing epic poetry",
                        "Developing the Socratic method of questioning to explore ideas",
                        "Founding the Roman Empire",
                        "Writing the Iliad"),
                1, "Socrates developed the Socratic method — a form of dialogue using questions to stimulate critical thinking."));
        art.add(buildQ("What does 'linguistics' study?",
                List.of("History of ancient civilisations",
                        "The structure, evolution, and nature of language",
                        "Animal communication only",
                        "Political speeches"),
                1, "Linguistics is the scientific study of language, including its structure, meaning, acquisition, and variation."));
        art.add(buildQ("Who wrote '1984'?",
                List.of("Aldous Huxley","Franz Kafka","George Orwell","Leo Tolstoy"),
                2, "George Orwell wrote '1984' (1949), a dystopian novel about totalitarianism and surveillance."));
        art.add(buildQ("What is cultural relativism?",
                List.of("The belief that Western culture is superior",
                        "The principle that a culture should be understood on its own terms",
                        "The rejection of all cultural traditions",
                        "The spread of a single global culture"),
                1, "Cultural relativism holds that a culture's practices and beliefs should be judged within their own context, not by external standards."));
        map.put("ART", Collections.unmodifiableList(art));

        // ── ENG / Engineering & CS ────────────────────────────────
        List<Map<String, Object>> eng = new ArrayList<>();
        eng.add(buildQ("What is an algorithm?",
                List.of("A computer program","A step-by-step procedure to solve a problem","A type of data","A network protocol"),
                1, "An algorithm is a finite sequence of well-defined instructions for solving a problem or performing a computation."));
        eng.add(buildQ("What does HTTP stand for?",
                List.of("HyperText Transfer Protocol","High-Tech Transfer Process","Hyperlink Text Transfer Path","Host Transfer Technology Protocol"),
                0, "HTTP (HyperText Transfer Protocol) is the foundation of data communication on the World Wide Web."));
        eng.add(buildQ("What is object-oriented programming?",
                List.of("Programming without functions",
                        "A paradigm that organises code around objects combining data and behaviour",
                        "A low-level machine code style",
                        "Programming only in Java"),
                1, "OOP organises software design around data (objects) rather than functions and logic, featuring encapsulation, inheritance, and polymorphism."));
        eng.add(buildQ("What is a database index?",
                List.of("A list of all tables in a database",
                        "A data structure that speeds up data retrieval operations",
                        "A backup of the database",
                        "A foreign key constraint"),
                1, "A database index is a data structure that improves the speed of data retrieval, similar to an index in a book."));
        eng.add(buildQ("What is the difference between TCP and UDP?",
                List.of("There is no difference",
                        "TCP is faster; UDP is slower",
                        "TCP is connection-oriented and reliable; UDP is connectionless and faster but less reliable",
                        "UDP is only used for email"),
                2, "TCP guarantees delivery and order; UDP trades reliability for speed, used in streaming and gaming."));
        map.put("ENG", Collections.unmodifiableList(eng));

        // ── GENERAL fallback ──────────────────────────────────────
        List<Map<String, Object>> general = new ArrayList<>();
        general.add(buildQ("What is the capital of Kyrgyzstan?",
                List.of("Osh","Bishkek","Jalal-Abad","Karakol"),
                1, "Bishkek is the capital and largest city of Kyrgyzstan."));
        general.add(buildQ("Which planet is closest to the Sun?",
                List.of("Venus","Earth","Mars","Mercury"),
                3, "Mercury is the closest planet to the Sun in our solar system."));
        general.add(buildQ("What is the chemical symbol for water?",
                List.of("WA","H2O","HO","W2O"),
                1, "Water's chemical formula is H2O — two hydrogen atoms and one oxygen atom."));
        general.add(buildQ("How many continents are there on Earth?",
                List.of("5","6","7","8"),
                2, "Earth has 7 continents."));
        general.add(buildQ("What does GDP stand for?",
                List.of("Gross Domestic Product","General Data Protocol","Government Debt Percentage","Gross Digital Production"),
                0, "GDP (Gross Domestic Product) measures the total economic output of a country."));
        general.add(buildQ("What year did World War II end?",
                List.of("1943","1944","1945","1946"),
                2, "World War II ended in 1945 with the surrender of Germany in May and Japan in September."));
        general.add(buildQ("What is the speed of light?",
                List.of("300,000 km/s","150,000 km/s","500,000 km/s","100,000 km/s"),
                0, "The speed of light in a vacuum is approximately 299,792 km/s, rounded to 300,000 km/s."));
        general.add(buildQ("What is photosynthesis?",
                List.of("Animals breathing oxygen",
                        "The process by which plants convert sunlight into food",
                        "The breakdown of food in digestion",
                        "The evaporation of water"),
                1, "Photosynthesis is the process by which plants use sunlight, water, and CO2 to produce glucose and oxygen."));
        map.put("GENERAL", Collections.unmodifiableList(general));

        return Collections.unmodifiableMap(map);
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

    /** Pick fallback questions that match the requested faculty/subject. */
    /**
     * Returns fallback questions for the given faculty code (from DB).
     * facultyCode is the raw code stored in the Faculty entity (e.g. "LAW", "ENG").
     * Falls back to GENERAL if the code has no dedicated bank.
     */
    private List<Map<String, Object>> getFallbackQuestions(String facultyCode, int count) {
        String key = "GENERAL";
        if (facultyCode != null && !facultyCode.isBlank()) {
            String code = facultyCode.trim().toUpperCase();
            if (FACULTY_FALLBACK.containsKey(code)) {
                key = code;
            }
        }
        List<Map<String, Object>> pool = new ArrayList<>(
                FACULTY_FALLBACK.getOrDefault(key, FACULTY_FALLBACK.get("GENERAL"))
        );
        Collections.shuffle(pool);
        return new ArrayList<>(pool.subList(0, Math.min(count, pool.size())));
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

        String prompt = buildPrompt(faculty, subject, lang, count);

        List<Map<String, Object>> questions = null;
        String usedModel = "built-in";
        boolean allQuotaExhausted = false;

        for (String model : GEMINI_MODELS) {
            try {
                questions = callGemini(prompt, model);
                usedModel = model;
                log.info("Quiz generated via model: {}", model);
                break;
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429) {
                    log.warn("Model {} quota exhausted (429), trying next...", model);
                    allQuotaExhausted = true;
                } else {
                    log.warn("Model {} HTTP error {}, trying next...", model, e.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("Model {} failed: {}, trying next...", model, e.getMessage());
            }
        }

        // All models failed → serve FACULTY-AWARE fallback bank
        if (questions == null) {
            log.warn("All Gemini models failed — serving faculty-aware fallback for faculty='{}' subject='{}'",
                    faculty, subject);
            questions = getFallbackQuestions(facultyCode, count);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success",   true);
        result.put("questions", questions);
        result.put("faculty",   faculty);
        result.put("subject",   subject);
        result.put("source",    usedModel);
        if ("built-in".equals(usedModel)) {
            result.put("notice", allQuotaExhausted
                    ? "AI quota reached — showing offline questions for your faculty. Try again later."
                    : "AI service unavailable — showing offline questions for your faculty.");
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
        // Language name + explicit instruction — placed FIRST so the model cannot miss it
        String langName = switch (lang) {
            case "ru" -> "Russian (Русский)";
            case "ky" -> "Kyrgyz (Кыргызча)";
            case "tr" -> "Turkish (Türkçe)";
            default   -> "English";
        };
        String langInstruction = switch (lang) {
            case "ru" -> "IMPORTANT: You MUST write ALL text (questions, options, explanations) entirely in Russian. Do NOT use English at all.";
            case "ky" -> "IMPORTANT: You MUST write ALL text (questions, options, explanations) entirely in Kyrgyz. Do NOT use English at all.";
            case "tr" -> "IMPORTANT: You MUST write ALL text (questions, options, explanations) entirely in Turkish. Do NOT use English at all.";
            default   -> "Write all text in English.";
        };
        return String.format("""
            %s
            Language: %s

            You are an expert university professor for %s faculty students, subject: %s.
            Generate exactly %d multiple-choice questions with exactly 4 options each.
            Return ONLY a valid JSON array — no markdown fences, no explanation text outside the JSON.
            Format: [{"question":"...","options":["A","B","C","D"],"correct":0,"explanation":"..."}]
            "correct" = 0-based index of the correct option. Vary difficulty from easy to hard.
            %s
            """, langInstruction, langName, faculty, subject, count, langInstruction);
    }

    // ── Gemini API (single model) ─────────────────────────────────
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> callGemini(String prompt, String model) throws Exception {
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = String.format(GEMINI_BASE, model) + geminiApiKey;

        Map<String, Object> reqBody = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );

        ResponseEntity<Map> resp = rest.postForEntity(
                url, new HttpEntity<>(reqBody, headers), Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null)
            throw new RuntimeException("Gemini API error: " + resp.getStatusCode());

        List<Map<String,Object>> candidates = (List<Map<String,Object>>) resp.getBody().get("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new RuntimeException("No response from Gemini");

        String text = (String)
                ((List<Map<String,Object>>) ((Map<String,Object>) candidates.get(0).get("content")).get("parts"))
                        .get(0).get("text");

        text = text.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
        int s = text.indexOf('['), e = text.lastIndexOf(']');
        if (s >= 0 && e > s) text = text.substring(s, e + 1);

        return objectMapper.readValue(text, List.class);
    }
}