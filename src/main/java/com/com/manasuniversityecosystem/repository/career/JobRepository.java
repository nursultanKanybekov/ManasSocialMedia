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
            "WHERE j.isActive = true AND j.jobType = :type " +
            "ORDER BY j.createdAt DESC")
    Page<JobListing> findActiveJobsByType(
            @Param("type") JobType type,
            Pageable pageable);

    List<JobListing> findByPostedByIdOrderByCreatedAtDesc(UUID postedById);

    @Query("SELECT j FROM JobListing j WHERE j.isActive = true ORDER BY j.createdAt DESC")
    List<JobListing> findLatestActive(Pageable pageable);

    long countByIsActiveTrue();
}