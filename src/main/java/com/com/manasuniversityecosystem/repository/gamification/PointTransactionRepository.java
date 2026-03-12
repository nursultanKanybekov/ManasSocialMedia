package com.com.manasuniversityecosystem.repository.gamification;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.gamification.PointTransaction;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface PointTransactionRepository extends JpaRepository<PointTransaction, UUID> {

    List<PointTransaction> findByUserOrderByCreatedAtDesc(AppUser user);

    Page<PointTransaction> findByUserOrderByCreatedAtDesc(AppUser user, Pageable pageable);

    long countByUserAndReason(AppUser user, PointReason reason);

    /** Check if user already got points for a specific entity today (prevent duplicates) */
    @Query("SELECT COUNT(pt) > 0 FROM PointTransaction pt " +
            "WHERE pt.user.id = :userId AND pt.reason = :reason " +
            "AND pt.refId = :refId AND pt.createdAt >= :since")
    boolean alreadyAwarded(
            @Param("userId") UUID userId,
            @Param("reason") PointReason reason,
            @Param("refId") UUID refId,
            @Param("since") LocalDateTime since);

    /** Points earned in the current week for leaderboard */
    @Query("SELECT pt.user.id, SUM(pt.amount) FROM PointTransaction pt " +
            "WHERE pt.createdAt >= :since GROUP BY pt.user.id ORDER BY SUM(pt.amount) DESC")
    List<Object[]> findWeeklyPointsSince(@Param("since") LocalDateTime since);

    @Query("SELECT SUM(pt.amount) FROM PointTransaction pt WHERE pt.user.id = :userId")
    Integer sumPointsByUserId(@Param("userId") UUID userId);

    /** Recent transactions for a user (for gaming history panel) */
    @Query("SELECT pt.reason, pt.amount, pt.createdAt FROM PointTransaction pt " +
            "WHERE pt.user.id = :userId ORDER BY pt.createdAt DESC")
    List<Object[]> findRecentByUser(@Param("userId") UUID userId, org.springframework.data.domain.Pageable pageable);

    /** Recent N transactions for user — convenience wrapper */
    default List<Object[]> findRecentByUser(UUID userId, int limit) {
        return findRecentByUser(userId, org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /** Login dates for streak calculation */
    @Query("SELECT pt.createdAt FROM PointTransaction pt " +
            "WHERE pt.user.id = :userId AND pt.reason = :reason ORDER BY pt.createdAt DESC")
    List<java.time.LocalDateTime> findLoginDatesByUser(@Param("userId") UUID userId, @Param("reason") PointReason reason);

    @Modifying
    @Query("DELETE FROM PointTransaction p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);
}