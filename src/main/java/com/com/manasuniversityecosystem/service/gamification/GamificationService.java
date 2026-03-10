package com.com.manasuniversityecosystem.service.gamification;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.gamification.Badge;
import com.com.manasuniversityecosystem.domain.entity.gamification.PointTransaction;
import com.com.manasuniversityecosystem.domain.entity.gamification.UserBadge;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.ProfileRepository;
import com.com.manasuniversityecosystem.repository.gamification.BadgeRepository;
import com.com.manasuniversityecosystem.repository.gamification.PointTransactionRepository;
import com.com.manasuniversityecosystem.repository.gamification.UserBadgeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GamificationService {

    private final PointTransactionRepository pointRepo;
    private final ProfileRepository profileRepo;
    private final BadgeRepository badgeRepo;
    private final UserBadgeRepository userBadgeRepo;

    // ── Point values per action ──────────────────────────────
    private static final int PTS_POST         = 10;
    private static final int PTS_COMMENT      = 3;
    private static final int PTS_LIKE_RECV    = 1;
    private static final int PTS_MENTOR       = 50;
    private static final int PTS_QUIZ         = 20;
    private static final int PTS_JOB_HELP     = 5;
    private static final int PTS_LOGIN        = 2;

    /**
     * Award points asynchronously so HTTP requests are not blocked.
     * Idempotency guard: LOGIN only once per day, others once per refId.
     */
    @Async("taskExecutor")
    @Transactional
    public void awardPoints(AppUser user, PointReason reason, UUID refId) {
        // Duplicate guard for login (once per day)
        if (reason == PointReason.LOGIN) {
            LocalDateTime startOfDay = LocalDateTime.now().toLocalDate().atStartOfDay();
            boolean alreadyLoggedIn = pointRepo.alreadyAwarded(
                    user.getId(), PointReason.LOGIN, null, startOfDay);
            if (alreadyLoggedIn) return;
        }

        // Duplicate guard for content actions (once per entity)
        if (refId != null && reason != PointReason.LOGIN) {
            boolean alreadyDone = pointRepo.alreadyAwarded(
                    user.getId(), reason, refId, LocalDateTime.now().minusYears(10));
            if (alreadyDone) return;
        }

        int amount = resolvePoints(reason);
        if (amount == 0) return;

        // Persist the transaction
        PointTransaction tx = PointTransaction.builder()
                .user(user)
                .amount(amount)
                .reason(reason)
                .refId(refId)
                .build();
        pointRepo.save(tx);

        // Update profile total using a single UPDATE statement (avoids stale reads)
        profileRepo.addPoints(user.getId(), amount);

        log.info("[GAMIFICATION] +{} pts → user={} reason={} refId={}",
                amount, user.getEmail(), reason, refId);

        // Check badge eligibility after point update
        Profile refreshed = profileRepo.findByUserId(user.getId())
                .orElse(null);
        if (refreshed != null) {
            checkAndAwardBadges(user, refreshed.getTotalPoints());
        }
    }

    private int resolvePoints(PointReason reason) {
        return switch (reason) {
            case POST          -> PTS_POST;
            case COMMENT       -> PTS_COMMENT;
            case LIKE_RECEIVED -> PTS_LIKE_RECV;
            case MENTOR        -> PTS_MENTOR;
            case QUIZ          -> PTS_QUIZ;
            case JOB_HELP      -> PTS_JOB_HELP;
            case LOGIN         -> PTS_LOGIN;
            default -> throw new IllegalArgumentException("Unknown point reason: " + reason);
        };
    }

    /**
     * Check all badges and award any the user is newly eligible for.
     * Called after every point award.
     */
    @Transactional
    public void checkAndAwardBadges(AppUser user, int totalPoints) {
        List<Badge> allBadges = badgeRepo.findAll();

        for (Badge badge : allBadges) {
            boolean alreadyHas = userBadgeRepo.existsByUserIdAndBadgeCode(
                    user.getId(), badge.getCode());
            if (alreadyHas) continue;

            if (isEligible(user, badge, totalPoints)) {
                UserBadge ub = UserBadge.builder()
                        .user(user)
                        .badge(badge)
                        .awardedAt(LocalDateTime.now())
                        .build();
                userBadgeRepo.save(ub);
                log.info("[GAMIFICATION] Badge '{}' awarded to user={}",
                        badge.getCode(), user.getEmail());
            }
        }
    }

    private boolean isEligible(AppUser user, Badge badge, int totalPoints) {
        return switch (badge.getCode()) {
            case "FIRST_POST"    ->
                    pointRepo.countByUserAndReason(user, PointReason.POST) >= 1;
            case "ACTIVE_MEMBER" ->
                    totalPoints >= 100;
            case "MENTOR_STAR"   ->
                    pointRepo.countByUserAndReason(user, PointReason.MENTOR) >= 3;
            case "NETWORKER"     ->
                    pointRepo.countByUserAndReason(user, PointReason.COMMENT) >= 50;
            case "TOP_100"       -> {
                Profile p = user.getProfile();
                yield p != null && p.getRankPosition() != null && p.getRankPosition() <= 100;
            }
            default -> false;
        };
    }

    /**
     * Nightly job: recalculate global rank for every profile.
     * Runs at 02:00 AM every day.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void recalculateRankings() {
        log.info("[GAMIFICATION] Starting nightly ranking recalculation...");
        List<Profile> ranked = profileRepo.findAllByOrderByTotalPointsDesc();
        for (int i = 0; i < ranked.size(); i++) {
            ranked.get(i).setRankPosition(i + 1);
        }
        profileRepo.saveAll(ranked);
        log.info("[GAMIFICATION] Rankings recalculated for {} profiles.", ranked.size());
    }

    /**
     * Weekly top users — used by feed widget.
     */
    @Transactional(readOnly = true)
    public List<Object[]> getWeeklyTopUsers() {
        LocalDateTime weekStart = LocalDateTime.now().minusWeeks(1);
        return pointRepo.findWeeklyPointsSince(weekStart);
    }

    @Transactional(readOnly = true)
    public List<Profile> getGlobalTop(int limit) {
        return profileRepo.findTopProfiles(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Profile> getFacultyTop(UUID facultyId, int limit) {
        return profileRepo.findTopByFaculty(facultyId, PageRequest.of(0, limit));
    }
}