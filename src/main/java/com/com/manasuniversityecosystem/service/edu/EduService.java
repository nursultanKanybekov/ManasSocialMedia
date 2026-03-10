package com.com.manasuniversityecosystem.service.edu;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.edu.Quiz;
import java.util.Map;
import com.com.manasuniversityecosystem.domain.entity.edu.QuizAttempt;
import com.com.manasuniversityecosystem.domain.entity.edu.QuizQuestion;
import com.com.manasuniversityecosystem.domain.entity.edu.Tutorial;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.edu.QuizAttemptRepository;
import com.com.manasuniversityecosystem.repository.edu.QuizRepository;
import com.com.manasuniversityecosystem.repository.edu.TutorialRepository;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.web.dto.edu.QuizSubmitRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EduService {

    private final TutorialRepository tutorialRepo;
    private final QuizRepository quizRepo;
    private final QuizAttemptRepository attemptRepo;
    private final GamificationService gamificationService;

    // ── TUTORIALS ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Tutorial> getTutorials(int page, int size) {
        return tutorialRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Tutorial> getTutorialsByCategory(String category, int page, int size) {
        return tutorialRepo.findByCategoryOrderByCreatedAtDesc(category, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Tutorial> searchTutorials(String query, String mediaTypeStr, String category, int page) {
        Tutorial.MediaType mt = null;
        if (mediaTypeStr != null && !mediaTypeStr.isBlank()) {
            try { mt = Tutorial.MediaType.valueOf(mediaTypeStr.toUpperCase()); } catch (Exception ignored) {}
        }
        String q = (query != null && !query.isBlank()) ? query : null;
        String cat = (category != null && !category.isBlank()) ? category : null;
        return tutorialRepo.search(mt != null ? mt.name() : null, cat, q, PageRequest.of(page, 12));
    }

    @Transactional
    public Tutorial createTutorial(AppUser author, String titleEn, String contentEn,
                                    String category, String mediaUrl,
                                    Tutorial.MediaType mediaType, String description) {
        Map<String, String> title = new java.util.HashMap<>();
        title.put("en", titleEn);
        Map<String, String> content = new java.util.HashMap<>();
        content.put("en", contentEn != null ? contentEn : "");

        Tutorial t = Tutorial.builder()
                .author(author)
                .titleI18n(title)
                .contentI18n(content)
                .category(category)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType != null ? mediaType : Tutorial.MediaType.TEXT)
                .description(description)
                .build();
        return tutorialRepo.save(t);
    }

    @Transactional(readOnly = true)
    public Tutorial getTutorialById(UUID id) {
        Tutorial t = tutorialRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Tutorial not found: " + id));
        return t;
    }

    @Transactional
    public Tutorial viewTutorial(UUID id) {
        tutorialRepo.incrementViews(id);
        return getTutorialById(id);
    }

    @Transactional(readOnly = true)
    public List<String> getAllCategories() {
        return tutorialRepo.findAllCategories();
    }

    // ── QUIZZES ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Quiz getQuizWithQuestions(UUID quizId) {
        return quizRepo.findByIdWithQuestions(quizId)
                .orElseThrow(() -> new IllegalArgumentException("Quiz not found: " + quizId));
    }

    /**
     * Submits answers and calculates score.
     * answers: Map<questionId, selectedOptionIndex>
     */
    @Transactional
    public QuizAttempt submitQuiz(AppUser user, UUID quizId, QuizSubmitRequest req) {
        Quiz quiz = getQuizWithQuestions(quizId);
        List<QuizQuestion> questions = quiz.getQuestions();

        if (questions.isEmpty()) {
            throw new IllegalStateException("Quiz has no questions.");
        }

        Map<UUID, Integer> answers = req.getAnswers();
        int correct = 0;

        for (QuizQuestion question : questions) {
            Integer selected = answers.get(question.getId());
            if (selected != null && question.isCorrect(selected)) {
                correct++;
            }
        }

        int score = (int) Math.round((double) correct / questions.size() * 100);
        boolean passed = score >= quiz.getPassScore();

        QuizAttempt attempt = QuizAttempt.builder()
                .quiz(quiz)
                .user(user)
                .score(score)
                .passed(passed)
                .build();
        attemptRepo.save(attempt);

        log.info("Quiz attempt: user={} quiz={} score={} passed={}",
                user.getEmail(), quizId, score, passed);

        // Award points only on first pass
        if (passed) {
            boolean alreadyPassed = attemptRepo.existsByQuizIdAndUserIdAndPassedTrue(
                    quizId, user.getId());
            // existsByQuizIdAndUserIdAndPassedTrue includes the current attempt we just saved
            // so count == 1 means this IS the first pass
            long passCount = attemptRepo.countByUserIdAndPassedTrue(user.getId());
            if (passCount == 1 ||
                    !attemptRepo.existsByQuizIdAndUserIdAndPassedTrue(quizId, user.getId())) {
                gamificationService.awardPoints(user, PointReason.QUIZ, quizId);
            } else {
                // Just award if this is their first pass on this specific quiz
                gamificationService.awardPoints(user, PointReason.QUIZ, quizId);
            }
        }

        return attempt;
    }

    @Transactional(readOnly = true)
    public List<QuizAttempt> getUserAttempts(UUID userId) {
        return attemptRepo.findByUserIdOrderByAttemptedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public QuizAttempt getBestAttempt(UUID quizId, UUID userId) {
        return attemptRepo.findTopByQuizIdAndUserIdOrderByAttemptedAtDesc(quizId, userId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean hasPassed(UUID quizId, UUID userId) {
        return attemptRepo.existsByQuizIdAndUserIdAndPassedTrue(quizId, userId);
    }
}