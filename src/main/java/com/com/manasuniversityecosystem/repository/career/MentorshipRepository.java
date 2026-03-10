package com.com.manasuniversityecosystem.repository.career;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.career.MentorshipRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MentorshipRepository extends JpaRepository<MentorshipRequest, UUID> {

    boolean existsByStudentAndMentorAndStatus(
            AppUser student, AppUser mentor, MentorshipRequest.MentorshipStatus status);

    List<MentorshipRequest> findByStudentOrderByCreatedAtDesc(AppUser student);

    List<MentorshipRequest> findByStudentAndStatus(AppUser student, MentorshipRequest.MentorshipStatus status);

    @Query("SELECT m FROM MentorshipRequest m JOIN FETCH m.student s JOIN FETCH s.profile " +
            "WHERE m.mentor.id = :mentorId AND m.status = :status ORDER BY m.createdAt DESC")
    List<MentorshipRequest> findByMentorIdAndStatusWithStudent(
            @Param("mentorId") UUID mentorId,
            @Param("status") MentorshipRequest.MentorshipStatus status);

    @Query("SELECT m FROM MentorshipRequest m JOIN FETCH m.mentor mt JOIN FETCH mt.profile " +
            "WHERE m.student.id = :studentId ORDER BY m.createdAt DESC")
    List<MentorshipRequest> findByStudentIdWithMentor(@Param("studentId") UUID studentId);

    long countByMentorIdAndStatus(UUID mentorId, MentorshipRequest.MentorshipStatus status);

    long countByMentorIdAndStatusIn(UUID mentorId, List<MentorshipRequest.MentorshipStatus> statuses);
}
