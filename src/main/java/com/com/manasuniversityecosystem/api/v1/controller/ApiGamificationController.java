package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.gamification.Badge;
import com.com.manasuniversityecosystem.domain.entity.gamification.UserBadge;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.gamification.UserBadgeRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/gamification")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Gamification", description = "Points, badges, leaderboards and mini-games")
public class ApiGamificationController {

    private final GamificationService  gamificationService;
    private final UserBadgeRepository  userBadgeRepo;
    private final UserService          userService;
    private final UserRepository       userRepository;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record PlayerCard(
            UUID    userId,
            String  fullName,
            String  avatarUrl,
            String  role,
            String  facultyName,
            int     totalPoints,
            Integer rankPosition,
            int     badgeCount,
            Integer studyYear,
            List<BadgeSummary> recentBadges
    ) {}

    public record BadgeSummary(
            UUID          badgeId,
            String        name,
            String        description,
            String        iconUrl,
            String        tier,       // BRONZE | SILVER | GOLD | PLATINUM
            LocalDateTime earnedAt
    ) {}

    public record LeaderboardEntry(
            int     rank,
            UUID    userId,
            String  fullName,
            String  avatarUrl,
            String  facultyName,
            int     totalPoints
    ) {}

    public record LeaderboardResponse(
            List<LeaderboardEntry> top3,
            List<LeaderboardEntry> rest,
            LeaderboardEntry       myPosition    // null if not in list
    ) {}

    public record GameRoomResponse(
            String  code,
            String  gameType,
            boolean isOpen,
            int     playerCount,
            int     maxPlayers
    ) {}

    // ══ Player profile ════════════════════════════════════════════

    @GetMapping("/me")
    @Operation(
            summary     = "Get the authenticated user's gamification profile (player card)",
            description = "Returns points, rank, badge count, recent badges and study year. " +
                    "Use this as the home-screen summary for the gamification section."
    )
    public ResponseEntity<ApiResponse<PlayerCard>> myCard(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(toPlayerCard(user, principal.getId())));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get another user's public gamification card")
    public ResponseEntity<ApiResponse<PlayerCard>> userCard(@PathVariable UUID id,
                                                            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(toPlayerCard(user, principal.getId())));
    }

    // ══ Badges ═══════════════════════════════════════════════════

    @GetMapping("/me/badges")
    @Operation(summary = "Get all badges earned by the authenticated user")
    public ResponseEntity<ApiResponse<List<BadgeSummary>>> myBadges(
            @RequestParam(defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        List<BadgeSummary> badges = userBadgeRepo
                .findByUserId(principal.getId())
                .stream()
                .sorted((a,b) -> b.getAwardedAt().compareTo(a.getAwardedAt()))
                .map(ub -> toBadgeSummary(ub, lang))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(badges));
    }

    @GetMapping("/users/{id}/badges")
    @Operation(summary = "Get badges earned by any user (public)")
    public ResponseEntity<ApiResponse<List<BadgeSummary>>> userBadges(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "en") String lang) {

        List<BadgeSummary> badges = userBadgeRepo
                .findByUserId(id)
                .stream()
                .sorted((a,b) -> b.getAwardedAt().compareTo(a.getAwardedAt()))
                .map(ub -> toBadgeSummary(ub, lang))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(badges));
    }

    // ══ Leaderboard ══════════════════════════════════════════════

    @GetMapping("/leaderboard/global")
    @Operation(
            summary     = "Global leaderboard — top users by total points",
            description = "Returns top-3 as a podium + the rest. Also includes `my_position` " +
                    "so the mobile app can highlight the current user's rank even if " +
                    "they fall outside the top-20."
    )
    public ResponseEntity<ApiResponse<LeaderboardResponse>> globalLeaderboard(
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        List<Profile> top = gamificationService.getGlobalTop(limit);
        return ResponseEntity.ok(ApiResponse.ok(
                buildLeaderboard(top, principal.getId())));
    }

    @GetMapping("/leaderboard/faculty/{facultyId}")
    @Operation(summary = "Faculty leaderboard — top users within a specific faculty")
    public ResponseEntity<ApiResponse<LeaderboardResponse>> facultyLeaderboard(
            @PathVariable UUID facultyId,
            @RequestParam(defaultValue = "20") int limit,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        List<Profile> top = gamificationService.getFacultyTop(facultyId, limit);
        return ResponseEntity.ok(ApiResponse.ok(
                buildLeaderboard(top, principal.getId())));
    }

    @GetMapping("/leaderboard/weekly")
    @Operation(
            summary     = "Weekly leaderboard — most points earned this week",
            description = "Resets every Monday. Used for the 'Top This Week' widget in the feed."
    )
    public ResponseEntity<ApiResponse<List<LeaderboardEntry>>> weeklyLeaderboard() {

        List<Object[]> weekly = gamificationService.getWeeklyTopUsers();
        List<LeaderboardEntry> entries = buildWeeklyEntries(weekly);
        return ResponseEntity.ok(ApiResponse.ok(entries));
    }

    // ══ Games (mini-game room integration) ════════════════════════

    @GetMapping("/games/open")
    @Operation(
            summary     = "List open game rooms waiting for players",
            description = "Mobile clients poll this to show a 'Quick Join' lobby. " +
                    "Returns rooms for all game types unless `type` is specified."
    )
    public ResponseEntity<ApiResponse<List<GameRoomResponse>>> openRooms(
            @RequestParam(required = false) String type) {

        // Delegate to game service via REST — reuses existing GameController logic
        // Returns a lightweight summary; full room details fetched via WebSocket on join
        List<GameRoomResponse> rooms = List.of(); // populated by GameService in real impl
        return ResponseEntity.ok(ApiResponse.ok(rooms));
    }

    @PostMapping("/games/award-points")
    @Operation(
            summary     = "Award points after a game session (internal — called by game engine)",
            description = "Protected by the same JWT auth. Game engine calls this after a round ends."
    )
    public ResponseEntity<ApiResponse<Map<String, Object>>> awardGamePoints(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        int points   = Integer.parseInt(String.valueOf(body.getOrDefault("points", 5)));
        gamificationService.awardPoints(user, PointReason.QUIZ, null);

        Profile p = userService.getById(principal.getId()).getProfile();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "points_awarded", points,
                "total_points",   p != null ? p.getTotalPoints() : 0
        )));
    }

    @PostMapping("/games/award-win")
    @Operation(summary = "Award win bonus points (internal — called by game engine)")
    public ResponseEntity<ApiResponse<Map<String, Object>>> awardGameWin(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        gamificationService.awardPoints(user, PointReason.GAME_WIN, null);

        Profile p = userService.getById(principal.getId()).getProfile();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "win_bonus",    true,
                "total_points", p != null ? p.getTotalPoints() : 0
        )));
    }

    // ══ Helpers ═══════════════════════════════════════════════════

    private PlayerCard toPlayerCard(AppUser user, UUID currentUserId) {
        Profile p = user.getProfile();
        List<BadgeSummary> recent = userBadgeRepo.findByUserId(user.getId()).stream()
                .sorted((a,b) -> b.getAwardedAt().compareTo(a.getAwardedAt()))
                .limit(3)
                .map(ub -> toBadgeSummary(ub, "en"))
                .toList();

        return new PlayerCard(
                user.getId(),
                user.getFullName(),
                p != null ? p.getAvatarUrl() : null,
                user.getRole().name(),
                user.getFaculty() != null ? user.getFaculty().getName() : null,
                p != null ? p.getTotalPoints() : 0,
                p != null ? p.getRankPosition() : null,
                (int) userBadgeRepo.countByUserId(user.getId()),
                p != null ? p.getStudyYear() : null,
                recent
        );
    }

    private BadgeSummary toBadgeSummary(UserBadge ub, String lang) {
        Badge b = ub.getBadge();
        return new BadgeSummary(
                b.getId(),
                b.getLocalizedName(lang),
                b.getLocalizedDescription(lang),
                b.getIconUrl(),
                b.getTier() != null ? b.getTier().name() : null,
                ub.getAwardedAt()
        );
    }

    private LeaderboardResponse buildLeaderboard(List<Profile> profiles, UUID currentUserId) {
        List<LeaderboardEntry> all = profiles.stream()
                .filter(p -> p.getUser() != null)
                .map(p -> new LeaderboardEntry(
                        p.getRankPosition() != null ? p.getRankPosition() : 0,
                        p.getUser().getId(),
                        p.getUser().getFullName(),
                        p.getAvatarUrl(),
                        p.getUser().getFaculty() != null
                                ? p.getUser().getFaculty().getName() : null,
                        p.getTotalPoints()
                ))
                .toList();

        List<LeaderboardEntry> top3 = all.stream().limit(3).toList();
        List<LeaderboardEntry> rest = all.stream().skip(3).toList();
        LeaderboardEntry myPos = all.stream()
                .filter(e -> e.userId().equals(currentUserId))
                .findFirst().orElse(null);

        return new LeaderboardResponse(top3, rest, myPos);
    }

    private List<LeaderboardEntry> buildWeeklyEntries(List<Object[]> rows) {
        int[] rank = {1};
        return rows.stream().map(row -> {
            UUID   userId     = (UUID)    row[0];
            Long   pts        = (Long)    row[1];
            AppUser u         = userRepository.findById(userId).orElse(null);
            if (u == null) return null;
            return new LeaderboardEntry(
                    rank[0]++,
                    userId,
                    u.getFullName(),
                    u.getProfile() != null ? u.getProfile().getAvatarUrl() : null,
                    u.getFaculty() != null ? u.getFaculty().getName() : null,
                    pts != null ? pts.intValue() : 0
            );
        }).filter(e -> e != null).toList();
    }
}