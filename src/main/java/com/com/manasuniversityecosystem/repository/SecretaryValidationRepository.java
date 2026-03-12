package com.com.manasuniversityecosystem.repository;


import com.com.manasuniversityecosystem.domain.entity.SecretaryValidation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SecretaryValidationRepository extends JpaRepository<SecretaryValidation, UUID> {

    List<SecretaryValidation> findByStatusOrderBySubmittedAtAsc(SecretaryValidation.ValidationStatus status);

    Optional<SecretaryValidation> findByUserId(UUID userId);

    boolean existsByUserIdAndStatus(UUID userId, SecretaryValidation.ValidationStatus status);

    @Query("SELECT v FROM SecretaryValidation v JOIN FETCH v.user u " +
            "LEFT JOIN FETCH u.faculty WHERE v.status = :status ORDER BY v.submittedAt ASC")
    List<SecretaryValidation> findPendingWithUsers(@Param("status") SecretaryValidation.ValidationStatus status);

    long countByStatus(SecretaryValidation.ValidationStatus status);

    @Modifying
    @Query("DELETE FROM SecretaryValidation v WHERE v.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}