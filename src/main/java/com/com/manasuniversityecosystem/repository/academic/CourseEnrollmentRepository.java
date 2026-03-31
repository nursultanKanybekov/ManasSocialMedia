package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.CourseEnrollment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {

    List<CourseEnrollment> findByStudentIdOrderByCourse_SemesterDescCourse_CodeAsc(UUID studentId);

    List<CourseEnrollment> findByCourseIdAndStatus(UUID courseId, CourseEnrollment.EnrollmentStatus status);

    Optional<CourseEnrollment> findByStudentIdAndCourseId(UUID studentId, UUID courseId);

    boolean existsByStudentIdAndCourseId(UUID studentId, UUID courseId);

    @Query("SELECT e FROM CourseEnrollment e LEFT JOIN FETCH e.student LEFT JOIN FETCH e.course " +
            "WHERE e.student.id = :studentId AND e.status = 'ACTIVE'")
    List<CourseEnrollment> findActiveByStudent(@Param("studentId") UUID studentId);

    long countByCourseIdAndStatus(UUID courseId, CourseEnrollment.EnrollmentStatus status);
}