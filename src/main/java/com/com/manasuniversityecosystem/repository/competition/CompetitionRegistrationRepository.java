package com.com.manasuniversityecosystem.repository.competition;

import com.com.manasuniversityecosystem.domain.entity.competition.CompetitionRegistration;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompetitionRegistrationRepository extends JpaRepository<CompetitionRegistration, UUID> {

    boolean existsByCompetitionIdAndUserId(UUID competitionId, UUID userId);

    Optional<CompetitionRegistration> findByCompetitionIdAndUserId(UUID competitionId, UUID userId);

    long countByCompetitionId(UUID competitionId);
}
