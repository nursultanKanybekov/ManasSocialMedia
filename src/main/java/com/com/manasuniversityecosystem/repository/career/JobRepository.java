package com.com.manasuniversityecosystem.repository.career;

import com.com.manasuniversityecosystem.domain.entity.career.JobListing;
import com.com.manasuniversityecosystem.domain.enums.JobType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<JobListing, UUID> {

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true " +
            "ORDER BY j.createdAt DESC")
    Page<JobListing> findActiveJobs(Pageable pageable);

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true ORDER BY j.createdAt ASC")
    Page<JobListing> findActiveJobsOldest(Pageable pageable);

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true AND j.deadline IS NOT NULL ORDER BY j.deadline ASC")
    Page<JobListing> findActiveJobsByDeadline(Pageable pageable);

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true ORDER BY j.salaryRange ASC NULLS LAST")
    Page<JobListing> findActiveJobsBySalary(Pageable pageable);

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true AND j.jobType = :type " +
            "ORDER BY j.createdAt DESC")
    Page<JobListing> findActiveJobsByType(
            @Param("type") JobType type,
            Pageable pageable);

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true AND j.jobType = :type ORDER BY j.createdAt ASC")
    Page<JobListing> findActiveJobsByTypeOldest(@Param("type") JobType type, Pageable pageable);

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true AND j.jobType = :type AND j.deadline IS NOT NULL ORDER BY j.deadline ASC")
    Page<JobListing> findActiveJobsByTypeDeadline(@Param("type") JobType type, Pageable pageable);

    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy " +
            "WHERE j.isActive = true AND j.jobType = :type ORDER BY j.salaryRange ASC NULLS LAST")
    Page<JobListing> findActiveJobsByTypeSalary(@Param("type") JobType type, Pageable pageable);

    List<JobListing> findByPostedByIdOrderByCreatedAtDesc(UUID postedById);

    @Query("SELECT j FROM JobListing j WHERE j.isActive = true ORDER BY j.createdAt DESC")
    List<JobListing> findLatestActive(Pageable pageable);

    long countByIsActiveTrue();

    /** All active jobs, eagerly loading postedBy — used for employer analytics panel */
    @Query("SELECT j FROM JobListing j JOIN FETCH j.postedBy p WHERE j.isActive = true ORDER BY p.id, j.createdAt DESC")
    List<JobListing> findAllActiveJobsWithEmployers();
}