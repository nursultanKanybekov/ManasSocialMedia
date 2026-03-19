package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.game.GameRoomManager;
import com.com.manasuniversityecosystem.service.game.GameRoomManager.GameRoom;
import com.com.manasuniversityecosystem.service.game.GameRoomManager.GameType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/games")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Mini Games", description = "Real-time multiplayer game rooms (Typing Racer, AI Quiz, etc.)")
public class ApiGameController {

    private final GameRoomManager roomManager;
    private final UserService     userService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record CreateRoomBody(
            @NotBlank String gameType   // TYPING_RACER | AI_QUIZ | ...
    ) {}

    public record RoomCreatedResponse(
            String code,
            String gameType,
            String joinUrl   // deep-link for mobile: manas://games/{code}
    ) {}

    public record RoomStatusResponse(
            String  code,
            String  gameType,
            String  status,           // WAITING | IN_PROGRESS | FINISHED
            int     playerCount,
            boolean isHost,
            List<PlayerInfo> players
    ) {}

    public record PlayerInfo(
            String userId,
            String fullName,
            String avatarUrl,
            boolean isHost
    ) {}

    public record OpenRoomsResponse(
            String gameType,
            List<RoomStatusResponse> rooms
    ) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping("/types")
    @Operation(
            summary     = "List available game types",
            description = "Returns all supported mini-game types. Use these values in `game_type` " +
                    "when creating a room."
    )
    public ResponseEntity<ApiResponse<List<String>>> gameTypes() {
        List<String> types = List.of(
                GameType.TYPING_RACER.name()
                // add more GameType enum values here as games are added
        );
        return ResponseEntity.ok(ApiResponse.ok(types));
    }

    @PostMapping("/rooms")
    @Operation(
            summary     = "Create a new game room",
            description = "Creates a room and makes the caller the host. " +
                    "Returns a room `code` — share this with other players to join. " +
                    "The game starts via WebSocket signal once all players are ready."
    )
    public ResponseEntity<ApiResponse<RoomCreatedResponse>> createRoom(
            @Valid @RequestBody CreateRoomBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        GameType type;
        try {
            type = GameType.valueOf(body.gameType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown game type: " + body.gameType());
        }

        var user   = userService.getById(principal.getId());
        String avatar = user.getProfile() != null ? user.getProfile().getAvatarUrl() : "";
        String role   = user.getRole() != null ? user.getRole().name() : "PLAYER";

        GameRoom room = roomManager.createRoom(
                type,
                principal.getId().toString(),
                user.getFullName(),
                avatar,
                role);

        return ResponseEntity.status(201).body(ApiResponse.created(new RoomCreatedResponse(
                room.getCode(),
                type.name(),
                "manas://games/" + room.getCode()
        )));
    }

    @GetMapping("/rooms/{code}/status")
    @Operation(
            summary     = "Get current status of a game room",
            description = "Poll before joining to check if room is still open. " +
                    "For live in-game updates, subscribe to WebSocket `/topic/game.{code}`."
    )
    public ResponseEntity<ApiResponse<RoomStatusResponse>> roomStatus(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        return roomManager.getRoom(code)
                .map(r -> {
                    boolean isHost = principal.getId().toString().equals(r.getHostUserId());
                    List<PlayerInfo> players = r.playerList().stream()
                            .map(p -> new PlayerInfo(
                                    p.getUserId(), p.getName(), p.getAvatar(),
                                    p.getUserId().equals(r.getHostUserId())))
                            .toList();
                    return ResponseEntity.ok(ApiResponse.ok(new RoomStatusResponse(
                            r.getCode(), r.getGameType().name(),
                            r.getStatus().name(), players.size(), isHost, players
                    )));
                })
                .orElseThrow(() -> new IllegalArgumentException("Game room not found: " + code));
    }

    @PostMapping("/rooms/{code}/join")
    @Operation(
            summary     = "Join an existing game room",
            description = "Adds the caller as a player. After joining, connect to WebSocket " +
                    "`/topic/game.{code}` to participate in the real-time game."
    )
    public ResponseEntity<ApiResponse<RoomStatusResponse>> joinRoom(
            @PathVariable String code,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var user   = userService.getById(principal.getId());
        String avatar = user.getProfile() != null ? user.getProfile().getAvatarUrl() : "";
        String role   = user.getRole() != null ? user.getRole().name() : "PLAYER";

        GameRoom room = roomManager.getRoom(code)
                .orElseThrow(() -> new IllegalArgumentException("Game room not found: " + code));

        if (!room.getPlayers().containsKey(principal.getId().toString())) {
            roomManager.joinRoom(code,
                    principal.getId().toString(),
                    user.getFullName(),
                    avatar,
                    role);
        }

        boolean isHost = principal.getId().toString().equals(room.getHostUserId());
        List<PlayerInfo> players = room.playerList().stream()
                .map(p -> new PlayerInfo(p.getUserId(), p.getName(), p.getAvatar(),
                        p.getUserId().equals(room.getHostUserId())))
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(new RoomStatusResponse(
                room.getCode(), room.getGameType().name(),
                room.getStatus().name(), players.size(), isHost, players
        )));
    }

    @GetMapping("/open/{gameType}")
    @Operation(
            summary     = "List open rooms for a specific game type",
            description = "Returns rooms in WAITING state that still have capacity. " +
                    "Used for the 'Quick Join' lobby on mobile."
    )
    public ResponseEntity<ApiResponse<List<RoomStatusResponse>>> openRooms(
            @PathVariable String gameType) {

        GameType type;
        try {
            type = GameType.valueOf(gameType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown game type: " + gameType);
        }

        List<RoomStatusResponse> open = roomManager.getOpenRooms(type).stream()
                .map(r -> {
                    List<PlayerInfo> players = r.playerList().stream()
                            .map(p -> new PlayerInfo(
                                    p.getUserId(), p.getName(), p.getAvatar(),
                                    p.getUserId().equals(r.getHostUserId())))
                            .toList();
                    return new RoomStatusResponse(
                            r.getCode(), r.getGameType().name(),
                            r.getStatus().name(), players.size(), false, players);
                })
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(open));
    }
}