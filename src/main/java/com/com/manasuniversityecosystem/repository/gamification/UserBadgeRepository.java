package com.com.manasuniversityecosystem.repository.gamification;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.gamification.UserBadge;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserBadgeRepository extends JpaRepository<UserBadge, UUID> {

    List<UserBadge> findByUser(AppUser user);

    List<UserBadge> findByUserId(UUID userId);

    boolean existsByUserIdAndBadgeCode(UUID userId, String badgeCode);

    @Query("SELECT ub FROM UserBadge ub JOIN FETCH ub.badge WHERE ub.user.id = :userId " +
            "ORDER BY ub.awardedAt DESC")
    List<UserBadge> findByUserIdWithBadge(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}