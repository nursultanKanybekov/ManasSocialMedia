package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.game.GameRoomManager;
import com.com.manasuniversityecosystem.service.game.GameRoomManager.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/games")
@RequiredArgsConstructor
@Slf4j
public class GameController {

    private final GameRoomManager roomManager;
    private final UserService     userService;

    /** Games hub — list of all available games */
    @GetMapping
    public String hub(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser user = userService.getById(principal.getId());
        model.addAttribute("currentUser",  user);
        model.addAttribute("openRooms",    roomManager.getAllOpenRooms());
        model.addAttribute("liveRooms",    roomManager.getAllLiveRooms());
        return "gamification/hub";
    }

    /** Create a new room — JSON API */
    @PostMapping("/create")
    @ResponseBody
    public ResponseEntity<?> createRoom(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        String  gameTypeStr = body.getOrDefault("gameType", "TYPING_RACER");
        GameType type;
        try { type = GameType.valueOf(gameTypeStr); }
        catch (IllegalArgumentException e) { return ResponseEntity.badRequest().body("Unknown game type"); }

        String avatar = user.getProfile() != null ? user.getProfile().getAvatarUrl() : "";
        GameRoom room = roomManager.createRoom(type, principal.getId().toString(),
                user.getFullName(), avatar != null ? avatar : "", user.getRole().name());
        return ResponseEntity.ok(Map.of("code", room.getCode(), "type", type.name()));
    }

    /** Join a room — redirect to room page */
    @GetMapping("/room/{code}")
    public String room(@PathVariable String code,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       Model model) {
        AppUser user = userService.getById(principal.getId());
        String avatar = user.getProfile() != null ? user.getProfile().getAvatarUrl() : "";

        var optRoom = roomManager.getRoom(code);
        if (optRoom.isEmpty()) return "redirect:/games?error=notfound";

        GameRoom room = optRoom.get();
        // Add this user if not already in room
        if (!room.getPlayers().containsKey(principal.getId().toString())
                && room.getStatus() == RoomStatus.WAITING) {
            try {
                roomManager.joinRoom(code, principal.getId().toString(),
                        user.getFullName(), avatar != null ? avatar : "", user.getRole().name());
            } catch (Exception e) {
                return "redirect:/games?error=" + e.getMessage().replace(" ", "+");
            }
        }

        model.addAttribute("currentUser",  user);
        model.addAttribute("currentAvatar", avatar != null ? avatar : "");
        model.addAttribute("room",          room);
        model.addAttribute("roomCode",      code.toUpperCase());
        model.addAttribute("gameType",      room.getGameType().name());
        model.addAttribute("isHost",        principal.getId().toString().equals(room.getHostUserId()));
        return "gamification/room";
    }

    /** Spectate a live room */
    @GetMapping("/spectate/{code}")
    public String spectate(@PathVariable String code,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           Model model) {
        AppUser user   = userService.getById(principal.getId());
        String  avatar = user.getProfile() != null ? user.getProfile().getAvatarUrl() : "";

        var optRoom = roomManager.getRoom(code);
        if (optRoom.isEmpty()) return "redirect:/games?error=notfound";

        GameRoom room = optRoom.get();
        if (room.getStatus() == RoomStatus.WAITING) {
            // Room not started yet — just join as player instead
            return "redirect:/games/room/" + code;
        }

        // Register as spectator
        String userId = principal.getId().toString();
        if (!room.getPlayers().containsKey(userId) && !room.isSpectator(userId)) {
            roomManager.joinAsSpectator(code, userId, user.getFullName());
        }

        model.addAttribute("currentUser",   user);
        model.addAttribute("currentAvatar", avatar != null ? avatar : "");
        model.addAttribute("room",          room);
        model.addAttribute("roomCode",      code.toUpperCase());
        model.addAttribute("gameType",      room.getGameType().name());
        model.addAttribute("isHost",        false);
        model.addAttribute("isSpectator",   true);
        model.addAttribute("spectatorCount", room.spectatorCount());
        return "gamification/room";
    }

    /** Lobby status JSON (for polling) */
    @GetMapping("/room/{code}/status")
    @ResponseBody
    public ResponseEntity<?> roomStatus(@PathVariable String code) {
        return roomManager.getRoom(code)
                .map(r -> ResponseEntity.ok(Map.of(
                        "status",         r.getStatus().name(),
                        "playerCount",    r.playerCount(),
                        "spectatorCount", r.spectatorCount(),
                        "players",        r.playerList().stream().map(p -> Map.of(
                                "userId", p.getUserId(), "name", p.getName(),
                                "avatar", p.getAvatar(), "ready", p.isReady(),
                                "host",  p.isHost(), "score", p.getScore()
                        )).toList()
                )))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Open rooms by game type */
    @GetMapping("/open/{type}")
    @ResponseBody
    public ResponseEntity<?> openRooms(@PathVariable String type) {
        try {
            GameType gt = GameType.valueOf(type.toUpperCase());
            return ResponseEntity.ok(roomManager.getOpenRooms(gt).stream().map(r -> Map.of(
                    "code",    r.getCode(),
                    "host",    r.getPlayers().values().stream().filter(GameRoomManager.Player::isHost)
                            .findFirst().map(GameRoomManager.Player::getName).orElse("Unknown"),
                    "players", r.playerCount(),
                    "max",     r.getMaxPlayers()
            )).toList());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown game type");
        }
    }
}