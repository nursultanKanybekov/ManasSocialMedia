package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {

    Optional<AssignmentSubmission> findByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId);

    boolean existsByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId);

    List<AssignmentSubmission> findByAssignmentIdOrderBySubmittedAtAsc(UUID assignmentId);

    List<AssignmentSubmission> findByStudentIdOrderBySubmittedAtDesc(UUID studentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId")
    long countByAssignment(@Param("assignmentId") UUID assignmentId);

    @Query("SELECT COUNT(s) FROM AssignmentSubmission s WHERE s.assignment.id = :assignmentId AND s.status = 'GRADED'")
    long countGradedByAssignment(@Param("assignmentId") UUID assignmentId);
}