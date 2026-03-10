package com.com.manasuniversityecosystem.repository.competition;

import com.com.manasuniversityecosystem.domain.entity.competition.Competition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CompetitionRepository extends JpaRepository<Competition, UUID> {

    @Query("SELECT c FROM Competition c WHERE " +
           "(:status IS NULL OR c.status = :status) AND " +
           "(:facultyId IS NULL OR c.faculty.id = :facultyId) " +
           "ORDER BY c.createdAt DESC")
    Page<Competition> search(@Param("status") String status,
                              @Param("facultyId") UUID facultyId,
                              Pageable pageable);

    List<Competition> findTop6ByOrderByCreatedAtDesc();
}
