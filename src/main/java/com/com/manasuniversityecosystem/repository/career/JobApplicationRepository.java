package com.com.manasuniversityecosystem.repository.career;


import com.com.manasuniversityecosystem.domain.entity.career.JobApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobApplicationRepository extends JpaRepository<JobApplication, UUID> {

    boolean existsByJobIdAndApplicantId(UUID jobId, UUID applicantId);

    Optional<JobApplication> findByJobIdAndApplicantId(UUID jobId, UUID applicantId);

    @Query("SELECT a FROM JobApplication a JOIN FETCH a.applicant ap JOIN FETCH ap.profile " +
            "WHERE a.job.id = :jobId ORDER BY a.appliedAt DESC")
    List<JobApplication> findByJobIdWithApplicants(@Param("jobId") UUID jobId);

    @Query("SELECT a FROM JobApplication a JOIN FETCH a.job " +
            "WHERE a.applicant.id = :applicantId ORDER BY a.appliedAt DESC")
    List<JobApplication> findByApplicantIdWithJob(@Param("applicantId") UUID applicantId);

    long countByJobId(UUID jobId);

    long countByApplicantId(UUID applicantId);

    /** True if applicantId has applied to any job posted by posterId */
    @Query("SELECT COUNT(a) > 0 FROM JobApplication a " +
            "WHERE a.applicant.id = :applicantId AND a.job.postedBy.id = :posterId")
    boolean existsByApplicantIdAndJobPosterId(
            @Param("applicantId") UUID applicantId,
            @Param("posterId")    UUID posterId);
}