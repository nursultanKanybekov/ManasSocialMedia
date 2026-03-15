package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.gamification.Badge;
import com.com.manasuniversityecosystem.domain.entity.gamification.UserBadge;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.gamification.BadgeRepository;
import com.com.manasuniversityecosystem.repository.gamification.UserBadgeRepository;
import com.com.manasuniversityecosystem.repository.gamification.PointTransactionRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * GamificationController — handles the /gaming page and gamification API endpoints.
 * Integrates with the web layout gaming.html design.
 */
@Controller
@RequiredArgsConstructor
public class GamificationController {

    private final UserService             userService;
    private final GamificationService     gamificationService;
    private final BadgeRepository         badgeRepository;
    private final UserBadgeRepository     userBadgeRepository;
    private final PointTransactionRepository pointTransactionRepository;

    // Level thresholds: index = level, value = XP required to reach it
    private static final int[] LEVEL_THRESHOLDS = {
            0, 100, 250, 500, 1000, 2000, 3500, 5000, 7500, 10000, 15000, 20000, 30000
    };
    private static final String[] LEVEL_TITLES = {
            "Newcomer", "Beginner", "Explorer", "Active",
            "Engaged", "Dedicated", "Experienced", "Expert",
            "Master", "Champion", "Legend", "Elite", "Pioneer"
    };

    /* ─────────────────── MAIN GAMING PAGE ─────────────────── */

    @GetMapping("/gaming")
    public String gamingPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user    = userService.getById(principal.getId());
        Profile profile = user.getProfile();
        int totalPoints = profile != null ? profile.getTotalPoints() : 0;

        // Level & XP calculations
        int level    = calculateLevel(totalPoints);
        int xpCurrent = totalPoints - getXpForLevel(level);
        int xpNext    = getXpForLevel(level + 1) - getXpForLevel(level);
        int xpPercent = xpNext > 0 ? (int) (100.0 * xpCurrent / xpNext) : 100;

        // Streak (consecutive login days — computed from point transactions)
        int streak = calculateStreak(user);

        // Badges: earned vs locked
        List<Badge>     allBadges   = badgeRepository.findAll();
        List<UserBadge> earnedBadges= userBadgeRepository.findByUserId(user.getId());
        Set<String>     earnedCodes = earnedBadges.stream()
                .map(ub -> ub.getBadge().getCode()).collect(Collectors.toSet());

        // Recent point history (last 10)
        List<Object[]> recentHistory = pointTransactionRepository
                .findRecentByUser(user.getId(), 10);

        // Global rank
        List<Profile> globalTop = gamificationService.getGlobalTop(200);
        int rank = 0;
        for (int i = 0; i < globalTop.size(); i++) {
            if (globalTop.get(i).getUser().getId().equals(user.getId())) {
                rank = i + 1;
                break;
            }
        }

        // Total wins: game wins + quiz completions
        long wins = pointTransactionRepository.countByUserAndReason(user, PointReason.GAME_WIN)
                + pointTransactionRepository.countByUserAndReason(user, PointReason.QUIZ)
                + pointTransactionRepository.countByUserAndReason(user, PointReason.QUIZ_PASS);

        model.addAttribute("currentUser",  user);
        model.addAttribute("profile",      profile);
        model.addAttribute("totalPoints",  totalPoints);
        model.addAttribute("level",        level);
        model.addAttribute("levelTitle",   getLevelTitle(level));
        model.addAttribute("xpCurrent",    xpCurrent);
        model.addAttribute("xpNext",       xpNext);
        model.addAttribute("xpPercent",    xpPercent);
        model.addAttribute("streak",       streak);
        model.addAttribute("rank",         rank);
        model.addAttribute("wins",         wins);
        model.addAttribute("badgeCount",   earnedBadges.size());
        model.addAttribute("allBadges",    allBadges);
        model.addAttribute("earnedCodes",  earnedCodes);
        model.addAttribute("earnedBadges", earnedBadges);
        model.addAttribute("recentHistory",recentHistory);
        model.addAttribute("globalTop",    gamificationService.getGlobalTop(10));
        model.addAttribute("pageTitle",    "Gamification");
        return "gamification/gaming";
    }

    /* ─────────────────── QUIZ SHORTCUT ─────────────────── */

    @GetMapping("/gaming/quiz")
    public String quizRedirect() {
        return "redirect:/edu";
    }

    /* ─────────────────── HELPERS ─────────────────── */

    private int calculateLevel(int totalPoints) {
        int level = 0;
        for (int i = LEVEL_THRESHOLDS.length - 1; i >= 0; i--) {
            if (totalPoints >= LEVEL_THRESHOLDS[i]) {
                level = i;
                break;
            }
        }
        return Math.min(level, LEVEL_THRESHOLDS.length - 1);
    }

    private int getXpForLevel(int level) {
        if (level <= 0) return 0;
        if (level >= LEVEL_THRESHOLDS.length) return LEVEL_THRESHOLDS[LEVEL_THRESHOLDS.length - 1];
        return LEVEL_THRESHOLDS[level];
    }

    private String getLevelTitle(int level) {
        if (level < LEVEL_TITLES.length) return LEVEL_TITLES[level];
        return "Legendary";
    }

    /**
     * Calculate login streak: consecutive days with at least one LOGIN point award.
     */
    private int calculateStreak(AppUser user) {
        try {
            List<LocalDateTime> loginDates =
                    pointTransactionRepository.findLoginDatesByUser(user.getId(), PointReason.LOGIN);
            if (loginDates == null || loginDates.isEmpty()) return 0;

            Set<Long> daySet = loginDates.stream()
                    .map(d -> d.toLocalDate().toEpochDay())
                    .collect(Collectors.toSet());

            long today = LocalDateTime.now().toLocalDate().toEpochDay();
            int streak = 0;
            long current = today;
            while (daySet.contains(current)) {
                streak++;
                current--;
            }
            return streak;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Mini-game points award endpoint (for mini-games on gaming page) */
    @PostMapping("/award-game-points")
    @ResponseBody
    public ResponseEntity<Map<String,Object>> awardGamePoints(
            @RequestBody Map<String,Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            AppUser user = userService.getById(principal.getId());
            gamificationService.awardPoints(user, PointReason.QUIZ_PASS, null);
            int currentPts = user.getProfile() != null ? user.getProfile().getTotalPoints() : 0;
            return ResponseEntity.ok(Map.of("success", true, "totalPoints", currentPts));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

    /** Multiplayer game win — awards 5 rating points to winner */
    @PostMapping("/award-game-win")
    @ResponseBody
    public ResponseEntity<Map<String,Object>> awardGameWin(
            @AuthenticationPrincipal UserDetailsImpl principal) {
        try {
            AppUser user = userService.getById(principal.getId());
            gamificationService.awardPoints(user, PointReason.GAME_WIN, null);
            int newTotal = user.getProfile() != null ? user.getProfile().getTotalPoints() + 5 : 5;
            return ResponseEntity.ok(Map.of("success", true, "pointsAwarded", 5, "totalPoints", newTotal));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "error", e.getMessage()));
        }
    }

}