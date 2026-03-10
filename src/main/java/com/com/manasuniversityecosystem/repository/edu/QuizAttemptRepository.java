package com.com.manasuniversityecosystem.repository.edu;

import com.com.manasuniversityecosystem.domain.entity.edu.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findByUserIdOrderByAttemptedAtDesc(UUID userId);

    Optional<QuizAttempt> findTopByQuizIdAndUserIdOrderByAttemptedAtDesc(UUID quizId, UUID userId);

    boolean existsByQuizIdAndUserIdAndPassedTrue(UUID quizId, UUID userId);

    @Query("SELECT AVG(a.score) FROM QuizAttempt a WHERE a.quiz.id = :quizId")
    Double findAverageScoreByQuizId(@Param("quizId") UUID quizId);

    long countByUserIdAndPassedTrue(UUID userId);
}

