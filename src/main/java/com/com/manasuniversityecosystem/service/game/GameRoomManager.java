package com.com.manasuniversityecosystem.service.game;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages all active multiplayer game rooms in memory.
 * No DB persistence needed — rooms are ephemeral play sessions.
 */
@Service
@Slf4j
public class GameRoomManager {

    public enum GameType {
        TYPING_RACER, CHESS, TETRIS, WORDLE, SUDOKU, DRAW_GUESS, MAFIA, UNO
    }

    public enum RoomStatus { WAITING, IN_PROGRESS, FINISHED }

    @Data
    public static class Player {
        private final String userId;
        private final String name;
        private final String avatar;
        private final String role;
        private boolean ready = false;
        private boolean host  = false;
        private int     score = 0;
        private Object  gameData;   // game-specific state per player
        private Instant joinedAt = Instant.now();
    }

    @Data
    public static class GameRoom {
        private final String     code;
        private final GameType   gameType;
        private final String     hostUserId;
        private RoomStatus       status = RoomStatus.WAITING;
        private final Map<String, Player> players = new LinkedHashMap<>();
        private int              maxPlayers;
        private Object           gameState;   // game-specific shared state
        private Instant          createdAt    = Instant.now();
        private Instant          startedAt;
        private Map<String,Object> settings   = new HashMap<>();

        public boolean isFull()    { return players.size() >= maxPlayers; }
        public int     playerCount() { return players.size(); }
        public List<Player> playerList() { return new ArrayList<>(players.values()); }
    }

    private final ConcurrentHashMap<String, GameRoom> rooms = new ConcurrentHashMap<>();

    private static final Map<GameType, Integer> MAX_PLAYERS = Map.of(
            GameType.TYPING_RACER, 8,
            GameType.CHESS,        2,
            GameType.TETRIS,       4,
            GameType.WORDLE,       8,
            GameType.SUDOKU,       4,
            GameType.DRAW_GUESS,  12,
            GameType.MAFIA,       16,
            GameType.UNO,          8
    );

    /** Create a new room and add the host */
    public GameRoom createRoom(GameType type, String hostUserId,
                               String hostName, String hostAvatar, String hostRole) {
        String code = generateCode();
        GameRoom room = new GameRoom(code, type, hostUserId);
        room.setMaxPlayers(MAX_PLAYERS.getOrDefault(type, 8));

        Player host = new Player(hostUserId, hostName, hostAvatar, hostRole);
        host.setHost(true);
        host.setReady(true);
        room.getPlayers().put(hostUserId, host);

        rooms.put(code, room);
        log.info("[Game] Room {} created: {} by {}", code, type, hostName);
        return room;
    }

    public Optional<GameRoom> getRoom(String code) {
        return Optional.ofNullable(rooms.get(code.toUpperCase()));
    }

    public GameRoom joinRoom(String code, String userId,
                             String name, String avatar, String role) {
        GameRoom room = rooms.get(code.toUpperCase());
        if (room == null) throw new IllegalArgumentException("Room not found: " + code);
        if (room.isFull()) throw new IllegalStateException("Room is full");
        if (room.getStatus() == RoomStatus.IN_PROGRESS)
            throw new IllegalStateException("Game already in progress");

        Player p = new Player(userId, name, avatar, role);
        room.getPlayers().put(userId, p);
        return room;
    }

    public void leaveRoom(String code, String userId) {
        GameRoom room = rooms.get(code);
        if (room == null) return;
        room.getPlayers().remove(userId);
        if (room.getPlayers().isEmpty() ||
                (room.getStatus() == RoomStatus.WAITING && room.getPlayers().isEmpty())) {
            rooms.remove(code);
            log.info("[Game] Room {} removed (empty)", code);
        }
    }

    public void startRoom(String code) {
        GameRoom room = rooms.get(code);
        if (room != null) {
            room.setStatus(RoomStatus.IN_PROGRESS);
            room.setStartedAt(Instant.now());
        }
    }

    public void finishRoom(String code) {
        GameRoom room = rooms.get(code);
        if (room != null) room.setStatus(RoomStatus.FINISHED);
    }

    public void setPlayerReady(String code, String userId, boolean ready) {
        GameRoom room = rooms.get(code);
        if (room != null && room.getPlayers().containsKey(userId)) {
            room.getPlayers().get(userId).setReady(ready);
        }
    }

    public boolean allReady(String code) {
        GameRoom room = rooms.get(code);
        if (room == null || room.getPlayers().isEmpty()) return false;
        return room.getPlayers().values().stream().allMatch(Player::isReady);
    }

    /** Public rooms of a given type for browse/join */
    public List<GameRoom> getOpenRooms(GameType type) {
        return rooms.values().stream()
                .filter(r -> r.getGameType() == type
                        && r.getStatus() == RoomStatus.WAITING
                        && !r.isFull())
                .sorted(Comparator.comparing(GameRoom::getCreatedAt).reversed())
                .toList();
    }

    /** All rooms regardless of type */
    public List<GameRoom> getAllOpenRooms() {
        return rooms.values().stream()
                .filter(r -> r.getStatus() == RoomStatus.WAITING && !r.isFull())
                .sorted(Comparator.comparing(GameRoom::getCreatedAt).reversed())
                .toList();
    }

    /** Remove stale rooms (> 2 hours old) — call from scheduler */
    public void cleanupStaleRooms() {
        Instant cutoff = Instant.now().minusSeconds(7200);
        rooms.entrySet().removeIf(e -> e.getValue().getCreatedAt().isBefore(cutoff));
    }

    private String generateCode() {
        String chars = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
        Random rng = new Random();
        String code;
        do {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) sb.append(chars.charAt(rng.nextInt(chars.length())));
            code = sb.toString();
        } while (rooms.containsKey(code));
        return code;
    }
}