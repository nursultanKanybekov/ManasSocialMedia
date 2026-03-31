package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {

    List<Assignment> findByCourseIdOrderByDeadlineAsc(UUID courseId);

    @Query("SELECT a FROM Assignment a WHERE a.course.id IN " +
            "(SELECT e.course.id FROM CourseEnrollment e WHERE e.student.id = :studentId AND e.status = 'ACTIVE') " +
            "ORDER BY a.deadline ASC")
    List<Assignment> findForStudent(@Param("studentId") UUID studentId);

    @Query("SELECT a FROM Assignment a WHERE a.course.id IN " +
            "(SELECT e.course.id FROM CourseEnrollment e WHERE e.student.id = :studentId AND e.status = 'ACTIVE') " +
            "AND a.deadline >= :now ORDER BY a.deadline ASC")
    List<Assignment> findUpcomingForStudent(@Param("studentId") UUID studentId,
                                            @Param("now") LocalDateTime now);

    @Query("SELECT a FROM Assignment a WHERE a.course.teacher.id = :teacherId ORDER BY a.deadline ASC")
    List<Assignment> findByTeacher(@Param("teacherId") UUID teacherId);
}