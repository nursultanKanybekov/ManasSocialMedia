package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.game.GameRoomManager;
import com.com.manasuniversityecosystem.service.game.GameRoomManager.*;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.*;

/**
 * Single WebSocket controller for ALL games.
 * Topic: /topic/game.{roomCode} — broadcast to room
 * Private: /topic/game.private.{userId} — private messages
 *
 * Clients send to /app/game.{action}/{roomCode}
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GameSignalController {

    private final SimpMessagingTemplate messaging;
    private final GameRoomManager       roomManager;
    private final GamificationService   gamificationService;
    private final UserService           userService;

    // ── ROOM MANAGEMENT ────────────────────────────────────────────

    @MessageMapping("/game.ready/{code}")
    public void ready(@DestinationVariable String code,
                      @Payload Map<String,Object> payload,
                      Principal principal) {
        String userId = id(principal);
        boolean ready = Boolean.TRUE.equals(payload.get("ready"));
        roomManager.setPlayerReady(code, userId, ready);

        var room = roomManager.getRoom(code);
        if (room.isEmpty()) return;
        Player p = room.get().getPlayers().get(userId);

        // Always broadcast full player info so all clients can render the lobby
        broadcast(code, Map.of(
                "type",   "PLAYER_READY",
                "userId", userId,
                "name",   p != null ? p.getName()   : "",
                "avatar", p != null ? p.getAvatar()  : "",
                "host",   p != null && p.isHost(),
                "ready",  ready
        ));

        // Also broadcast full player list so late-joiners sync everyone
        broadcast(code, Map.of(
                "type",    "LOBBY_SYNC",
                "players", buildPlayerList(room.get())
        ));

        // Auto-start if all ready
        if (room.get().playerCount() >= 2 && roomManager.allReady(code)) {
            broadcast(code, Map.of("type","ALL_READY","countdown",3));
        }
    }

    @MessageMapping("/game.start/{code}")
    public void start(@DestinationVariable String code, Principal principal) {
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        GameRoom room = opt.get();
        if (!room.getHostUserId().equals(id(principal))) return; // host only
        if (room.playerCount() < 2) {
            messaging.convertAndSend("/topic/game.private." + id(principal),
                    Map.of("type","ERROR","msg","Need at least 2 players to start"));
            return;
        }
        roomManager.startRoom(code);
        broadcast(code, Map.of("type","GAME_STARTED",
                "gameType", room.getGameType().name(),
                "players",  buildPlayerList(room)));
        log.info("[Game] {} started: {}", room.getGameType(), code);
    }

    @MessageMapping("/game.leave/{code}")
    public void leave(@DestinationVariable String code, Principal principal) {
        String userId = id(principal);
        var opt = roomManager.getRoom(code);
        if (opt.isEmpty()) return;
        GameRoom room = opt.get();
        Player leaving = room.getPlayers().get(userId);
        String name = leaving != null ? leaving.getName() : "Someone";
        roomManager.leaveRoom(code, userId);
        // Broadcast leave with updated player list so all clients re-sync
        broadcast(code, Map.of(
                "type",    "PLAYER_LEFT",
                "userId",  userId,
                "name",    name,
                "players", buildPlayerList(room)
        ));
    }

    @MessageMapping("/game.chat/{code}")
    public void chat(@DestinationVariable String code,
                     @Payload Map<String,Object> payload, Principal principal) {
        String userId = id(principal);
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        Player p = opt.get().getPlayers().get(userId); if (p == null) return;
        broadcast(code, Map.of(
                "type",   "CHAT",
                "userId", userId,
                "name",   p.getName(),
                "avatar", p.getAvatar(),
                "text",   payload.getOrDefault("text","").toString()
        ));
    }

    @MessageMapping("/game.emoji/{code}")
    public void emoji(@DestinationVariable String code,
                      @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "EMOJI",
                "userId", id(principal),
                "emoji",  payload.getOrDefault("emoji","👍").toString()
        ));
    }

    // ── TYPING RACER ────────────────────────────────────────────────

    @MessageMapping("/game.typing.progress/{code}")
    public void typingProgress(@DestinationVariable String code,
                               @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",     "TYPING_PROGRESS",
                "userId",   id(principal),
                "progress", payload.getOrDefault("progress", 0),  // 0-100
                "wpm",      payload.getOrDefault("wpm", 0),
                "nitro",    payload.getOrDefault("nitro", false)
        ));
    }

    @MessageMapping("/game.typing.finish/{code}")
    public void typingFinish(@DestinationVariable String code,
                             @Payload Map<String,Object> payload, Principal principal) {
        String userId = id(principal);
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        GameRoom room = opt.get();
        Player p = room.getPlayers().get(userId);
        if (p != null) {
            int wpm = (int) payload.getOrDefault("wpm", 0);
            p.setScore(wpm);
            p.setGameData("finished"); // mark this player as finished
        }
        broadcast(code, Map.of(
                "type",     "TYPING_FINISH",
                "userId",   userId,
                "name",     p != null ? p.getName() : "?",
                "wpm",      payload.getOrDefault("wpm", 0),
                "time",     payload.getOrDefault("time", 0),
                "accuracy", payload.getOrDefault("accuracy", 100)
        ));
        // Count how many players have actually finished (server-tracked, not client-sent)
        long finished = room.getPlayers().values().stream()
                .filter(pl -> "finished".equals(pl.getGameData())).count();
        if (finished >= room.playerCount()) endGame(code, room);
    }

    // ── CHESS ──────────────────────────────────────────────────────

    @MessageMapping("/game.chess.move/{code}")
    public void chessMove(@DestinationVariable String code,
                          @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "CHESS_MOVE",
                "userId", id(principal),
                "from",   payload.getOrDefault("from",""),
                "to",     payload.getOrDefault("to",""),
                "piece",  payload.getOrDefault("piece",""),
                "promotion", payload.getOrDefault("promotion",""),
                "fen",    payload.getOrDefault("fen","")
        ));
    }

    @MessageMapping("/game.chess.resign/{code}")
    public void chessResign(@DestinationVariable String code, Principal principal) {
        var opt = roomManager.getRoom(code);
        if (opt.isEmpty()) return;
        GameRoom room = opt.get();
        String loserId = id(principal);
        // Set loser score 0, winner score 10
        room.getPlayers().values().forEach(p -> p.setScore(p.getUserId().equals(loserId) ? 0 : 10));
        broadcast(code, Map.of("type","CHESS_RESIGN","userId",loserId));
        endGame(code, room);
    }

    @MessageMapping("/game.chess.checkmate/{code}")
    public void chessCheckmate(@DestinationVariable String code,
                               @Payload Map<String,Object> payload, Principal principal) {
        var opt = roomManager.getRoom(code);
        if (opt.isEmpty()) return;
        GameRoom room = opt.get();
        String winnerId = (String) payload.getOrDefault("winner", "");
        String loserId  = (String) payload.getOrDefault("loser",  "");
        room.getPlayers().values().forEach(p -> p.setScore(p.getUserId().equals(winnerId) ? 10 : 0));
        endGame(code, room);
    }

    @MessageMapping("/game.chess.draw/{code}")
    public void chessDraw(@DestinationVariable String code,
                          @Payload Map<String,Object> payload, Principal principal) {
        boolean accept = Boolean.TRUE.equals(payload.get("accept"));
        if (accept) {
            // Both players agreed — end game as draw
            var opt = roomManager.getRoom(code);
            opt.ifPresent(room -> {
                roomManager.finishRoom(code);
                broadcast(code, Map.of("type", "CHESS_DRAW_ACCEPTED",
                        "userId", id(principal)));
            });
        } else {
            // Offer sent — broadcast to opponent
            broadcast(code, Map.of("type", "CHESS_DRAW_OFFER",
                    "userId", id(principal)));
        }
    }

    // ── TETRIS ─────────────────────────────────────────────────────

    @MessageMapping("/game.tetris.state/{code}")
    public void tetrisState(@DestinationVariable String code,
                            @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "TETRIS_STATE",
                "userId", id(principal),
                "board",  payload.getOrDefault("board",""),
                "score",  payload.getOrDefault("score",0),
                "lines",  payload.getOrDefault("lines",0),
                "level",  payload.getOrDefault("level",1)
        ));
    }

    @MessageMapping("/game.tetris.garbage/{code}")
    public void tetrisGarbage(@DestinationVariable String code,
                              @Payload Map<String,Object> payload, Principal principal) {
        String target = (String) payload.get("target"); // "all" or userId
        int lines = (int) payload.getOrDefault("lines", 1);
        if ("all".equals(target)) {
            broadcast(code, Map.of("type","TETRIS_GARBAGE",
                    "from", id(principal), "lines", lines));
        } else if (target != null) {
            messaging.convertAndSend("/topic/game.private." + target,
                    Map.of("type","TETRIS_GARBAGE","from",id(principal),"lines",lines));
        }
    }

    @MessageMapping("/game.tetris.dead/{code}")
    public void tetrisDead(@DestinationVariable String code, Principal principal) {
        String userId = id(principal);
        broadcast(code, Map.of("type","TETRIS_DEAD","userId",userId));
        // Check if one player left
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        GameRoom room = opt.get();
        long alive = room.getPlayers().values().stream()
                .filter(p -> !Boolean.TRUE.equals(p.getGameData())).count();
        if (alive <= 1) endGame(code, room);
    }

    // ── WORDLE ─────────────────────────────────────────────────────

    @MessageMapping("/game.wordle.guess/{code}")
    public void wordleGuess(@DestinationVariable String code,
                            @Payload Map<String,Object> payload, Principal principal) {
        String userId = id(principal);
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        Player p = opt.get().getPlayers().get(userId);
        // Broadcast guess attempt (with result colors — computed client-side)
        broadcast(code, Map.of(
                "type",   "WORDLE_GUESS",
                "userId", userId,
                "name",   p != null ? p.getName() : "?",
                "guess",  payload.getOrDefault("guess",""),
                "result", payload.getOrDefault("result", List.of()), // ["correct","present","absent"...]
                "row",    payload.getOrDefault("row", 0),
                "solved", payload.getOrDefault("solved", false)
        ));
        if (Boolean.TRUE.equals(payload.get("solved"))) {
            var room = opt.get();
            if (p != null) p.setScore(7 - (int) payload.getOrDefault("row", 6));
            endGame(code, room);
        }
    }

    // ── SUDOKU ─────────────────────────────────────────────────────

    @MessageMapping("/game.sudoku.fill/{code}")
    public void sudokuFill(@DestinationVariable String code,
                           @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "SUDOKU_FILL",
                "userId", id(principal),
                "cell",   payload.getOrDefault("cell",0),
                "value",  payload.getOrDefault("value",0),
                "correct",payload.getOrDefault("correct",false)
        ));
    }

    @MessageMapping("/game.sudoku.complete/{code}")
    public void sudokuComplete(@DestinationVariable String code,
                               @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of("type","SUDOKU_COMPLETE","userId",id(principal),
                "time", payload.getOrDefault("time",0)));
        var opt = roomManager.getRoom(code);
        opt.ifPresent(r -> endGame(code, r));
    }

    // ── DRAW & GUESS ────────────────────────────────────────────────

    @MessageMapping("/game.draw.stroke/{code}")
    public void drawStroke(@DestinationVariable String code,
                           @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "DRAW_STROKE",
                "userId", id(principal),
                "data",   payload  // pass through stroke data
        ));
    }

    @MessageMapping("/game.draw.clear/{code}")
    public void drawClear(@DestinationVariable String code, Principal principal) {
        broadcast(code, Map.of("type","DRAW_CLEAR","userId",id(principal)));
    }

    @MessageMapping("/game.draw.guess/{code}")
    public void drawGuess(@DestinationVariable String code,
                          @Payload Map<String,Object> payload, Principal principal) {
        String userId = id(principal);
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        GameRoom room = opt.get();
        Player p = room.getPlayers().get(userId); if (p == null) return;

        String guess = payload.getOrDefault("guess","").toString().trim().toLowerCase();
        String word  = payload.getOrDefault("word","").toString().trim().toLowerCase();
        boolean correct = guess.equals(word) && !word.isEmpty();

        if (correct) {
            p.setScore(p.getScore() + 100);
            broadcast(code, Map.of("type","DRAW_CORRECT","userId",userId,"name",p.getName()));
        } else {
            // Broadcast guess attempt to all (word hidden)
            broadcast(code, Map.of("type","DRAW_GUESS","userId",userId,
                    "name",p.getName(),"guess",guess,"correct",false));
        }
    }

    @MessageMapping("/game.draw.nextword/{code}")
    public void drawNextWord(@DestinationVariable String code,
                             @Payload Map<String,Object> payload, Principal principal) {
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        if (!opt.get().getHostUserId().equals(id(principal))) return;
        broadcast(code, Map.of("type","DRAW_NEW_ROUND",
                "drawer",  payload.getOrDefault("drawer",""),
                "wordLen", payload.getOrDefault("wordLen", 5),
                "round",   payload.getOrDefault("round", 1)
        ));
        // Send word only to drawer
        String drawer = (String) payload.get("drawer");
        String word   = (String) payload.get("word");
        if (drawer != null && word != null) {
            messaging.convertAndSend("/topic/game.private." + drawer,
                    Map.of("type","DRAW_YOUR_WORD","word", word));
        }
    }

    // ── MAFIA ──────────────────────────────────────────────────────

    @MessageMapping("/game.mafia.action/{code}")
    public void mafiaAction(@DestinationVariable String code,
                            @Payload Map<String,Object> payload, Principal principal) {
        String userId = id(principal);
        var opt = roomManager.getRoom(code); if (opt.isEmpty()) return;
        String action = (String) payload.getOrDefault("action","");
        String target = (String) payload.get("target");

        switch (action) {
            case "VOTE" ->
                    broadcast(code, Map.of("type","MAFIA_VOTE","voter",userId,"target",target));
            case "KILL","SAVE","INVESTIGATE" ->
                // Night actions — only host/narrator sees results
                    messaging.convertAndSend("/topic/game.private." + opt.get().getHostUserId(),
                            Map.of("type","MAFIA_NIGHT_ACTION","action",action,"actor",userId,"target",target));
            case "PHASE" -> {
                // Only host can advance phase
                if (opt.get().getHostUserId().equals(userId))
                    broadcast(code, Map.of("type","MAFIA_PHASE","phase",
                            payload.getOrDefault("phase","DAY")));
            }
            case "ELIMINATE" -> {
                if (opt.get().getHostUserId().equals(userId))
                    broadcast(code, Map.of("type","MAFIA_ELIMINATE","target",target,
                            "role", payload.getOrDefault("role","VILLAGER")));
            }
        }
    }

    // ── UNO ────────────────────────────────────────────────────────

    @MessageMapping("/game.uno.play/{code}")
    public void unoPlay(@DestinationVariable String code,
                        @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "UNO_PLAY",
                "userId", id(principal),
                "card",   payload.getOrDefault("card",""),
                "color",  payload.getOrDefault("color",""),
                "next",   payload.getOrDefault("next",""),
                "stack",  payload.getOrDefault("stack",List.of()),
                "gameState", payload.getOrDefault("gameState", Map.of())
        ));
    }

    @MessageMapping("/game.uno.draw/{code}")
    public void unoDraw(@DestinationVariable String code,
                        @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "UNO_DRAW",
                "userId", id(principal),
                "count",  payload.getOrDefault("count",1)
        ));
    }

    @MessageMapping("/game.uno.say/{code}")
    public void unoSay(@DestinationVariable String code,
                       @Payload Map<String,Object> payload, Principal principal) {
        broadcast(code, Map.of(
                "type",   "UNO_SAY",
                "userId", id(principal),
                "text",   payload.getOrDefault("text","UNO!")
        ));
    }

    // ── HELPERS ────────────────────────────────────────────────────

    private void broadcast(String code, Map<String,Object> msg) {
        messaging.convertAndSend("/topic/game." + code.toUpperCase(), msg);
    }

    private void endGame(String code, GameRoom room) {
        roomManager.finishRoom(code);
        List<Map<String,Object>> scores = room.getPlayers().values().stream()
                .sorted(Comparator.comparingInt(Player::getScore).reversed())
                .map(p -> Map.<String,Object>of(
                        "userId",p.getUserId(),"name",p.getName(),
                        "avatar",p.getAvatar(),"score",p.getScore()))
                .toList();
        broadcast(code, Map.of("type","GAME_OVER","scores",scores,
                "winner", scores.isEmpty() ? "" : scores.get(0).get("name")));
        // Award 5 points to the winner in the rating system
        if (!scores.isEmpty()) {
            String winnerUserId = (String) scores.get(0).get("userId");
            try {
                UUID uid = UUID.fromString(winnerUserId);
                var winner = userService.getById(uid);
                if (winner != null) {
                    gamificationService.awardPoints(winner, PointReason.GAME_WIN, null);
                    log.info("[Game] +5 pts awarded to winner={} room={}", winner.getEmail(), code);
                }
            } catch (Exception e) {
                log.warn("[Game] Could not award points to winner {}: {}", winnerUserId, e.getMessage());
            }
        }
    }

    private List<Map<String,Object>> buildPlayerList(GameRoom room) {
        return room.getPlayers().values().stream().map(p -> Map.<String,Object>of(
                "userId",p.getUserId(),"name",p.getName(),
                "avatar",p.getAvatar(),"host",p.isHost(),"score",p.getScore()
        )).toList();
    }

    private String id(Principal p) {
        if (p instanceof UsernamePasswordAuthenticationToken t
                && t.getPrincipal() instanceof
                com.com.manasuniversityecosystem.security.UserDetailsImpl ud)
            return ud.getId().toString();
        return p.getName();
    }
}