package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.Grade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GradeRepository extends JpaRepository<Grade, UUID> {

    List<Grade> findByStudentIdOrderByCourse_CodeAscTypeAsc(UUID studentId);

    List<Grade> findByCourseIdOrderByStudent_FullNameAscTypeAsc(UUID courseId);

    List<Grade> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    Optional<Grade> findByStudentIdAndCourseIdAndType(UUID studentId, UUID courseId, Grade.GradeType type);

    @Query("SELECT AVG(g.score / g.maxScore * 100) FROM Grade g WHERE g.course.id = :courseId")
    Double avgScoreForCourse(@Param("courseId") UUID courseId);

    @Query("SELECT AVG(g.score / g.maxScore * 100) FROM Grade g WHERE g.student.id = :studentId")
    Double avgScoreForStudent(@Param("studentId") UUID studentId);
}