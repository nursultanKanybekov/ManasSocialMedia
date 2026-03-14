package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.repository.chat.ChatParticipantRepository;
import com.com.manasuniversityecosystem.repository.chat.ChatRoomRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Zoom-like group video conference signaling over WebRTC / STOMP.
 *
 * Topics:
 *   /topic/meet.{roomId}           — broadcast to ALL participants
 *   /topic/meet.user.{userId}      — private signaling to one peer
 *
 * Client sends to:
 *   /app/meet.join/{roomId}
 *   /app/meet.leave/{roomId}
 *   /app/meet.offer/{roomId}       — SDP offer to a specific peer
 *   /app/meet.answer/{roomId}      — SDP answer
 *   /app/meet.ice/{roomId}         — ICE candidate
 *   /app/meet.hand/{roomId}        — raise/lower hand
 *   /app/meet.reaction/{roomId}    — emoji reaction
 *   /app/meet.state/{roomId}       — mic/cam/screen state update
 *   /app/meet.chat/{roomId}        — in-meeting chat message
 *   /app/meet.host/{roomId}        — host control (mute/kick/spotlight)
 *   /app/meet.poll/{roomId}        — quick poll
 *   /app/meet.rename/{roomId}      — rename yourself in meeting
 *   /app/meet.record/{roomId}      — recording start/stop (host only)
 *   /app/meet.wb/{roomId}          — whiteboard draw events
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class GroupCallSignalController {

    private final SimpMessagingTemplate messaging;
    private final UserService userService;
    private final ChatParticipantRepository participantRepo;
    private final ChatRoomRepository chatRoomRepo;

    /** roomId → ordered map of participants */
    private final ConcurrentHashMap<String, LinkedHashMap<String, Participant>> rooms = new ConcurrentHashMap<>();
    /** roomId → hostUserId */
    private final ConcurrentHashMap<String, String> hosts = new ConcurrentHashMap<>();
    /** roomId → recording state */
    private final ConcurrentHashMap<String, Boolean> recording = new ConcurrentHashMap<>();
    /** roomId → waitingRoom set of Participants */
    private final ConcurrentHashMap<String, Map<String, Participant>> waitingRooms = new ConcurrentHashMap<>();

    // ── JOIN ───────────────────────────────────────────────────────────

    @MessageMapping("/meet.join/{roomId}")
    public void join(@DestinationVariable String roomId,
                     @Payload Map<String, Object> payload,
                     Principal principal) {
        UserDetailsImpl me = extract(principal);
        AppUser user = userService.getById(me.getId());
        String avatar = user.getProfile() != null ? user.getProfile().getAvatarUrl() : "";

        boolean waitingRoomEnabled = Boolean.TRUE.equals(
                Optional.ofNullable(payload.get("waitingRoom")).orElse(false));

        Participant p = new Participant(
                me.getId().toString(), user.getFullName(),
                avatar != null ? avatar : "", user.getRole().name(),
                Instant.now().toEpochMilli()
        );

        // First joiner becomes host
        boolean isFirstJoiner = !rooms.containsKey(roomId) || rooms.get(roomId).isEmpty();
        if (isFirstJoiner) {
            hosts.put(roomId, me.getId().toString());
            rooms.computeIfAbsent(roomId, k -> new LinkedHashMap<>());
        }

        // If waiting room is active and user is not host, hold in waiting room
        String hostId = hosts.get(roomId);
        boolean isHost = me.getId().toString().equals(hostId);

        if (waitingRoomEnabled && !isHost && !isFirstJoiner) {
            waitingRooms.computeIfAbsent(roomId, k -> new ConcurrentHashMap<>()).put(p.getUserId(), p);
            // Notify host
            messaging.convertAndSend("/topic/meet.user." + hostId, Map.of(
                    "type", "WAITING", "participant", p
            ));
            // Tell joiner they're waiting
            messaging.convertAndSend("/topic/meet.user." + me.getId(), Map.of(
                    "type", "IN_WAITING_ROOM"
            ));
            log.info("[Meet] {} is waiting for admission in {}", p.getName(), roomId);
            return;
        }

        admitParticipant(roomId, p, isHost);
    }

    private void admitParticipant(String roomId, Participant p, boolean isHost) {
        LinkedHashMap<String, Participant> room =
                rooms.computeIfAbsent(roomId, k -> new LinkedHashMap<>());

        boolean wasEmpty = room.isEmpty();
        room.put(p.getUserId(), p);

        List<Participant> existing = room.values().stream()
                .filter(x -> !x.getUserId().equals(p.getUserId()))
                .collect(Collectors.toList());

        // Tell the joiner the current room state
        messaging.convertAndSend("/topic/meet.user." + p.getUserId(), Map.of(
                "type",         "ROOM_STATE",
                "participants", existing,
                "hostId",       hosts.getOrDefault(roomId, ""),
                "recording",    recording.getOrDefault(roomId, false),
                "roomId",       roomId
        ));

        // Tell everyone else: someone joined
        Map<String, Object> joinMsg = new HashMap<>();
        joinMsg.put("type",        "JOINED");
        joinMsg.put("participant", p);
        joinMsg.put("hostId",      hosts.getOrDefault(roomId, ""));
        joinMsg.put("totalCount",  room.size());
        messaging.convertAndSend("/topic/meet." + roomId, joinMsg);

        // ── When the first person joins, notify ALL channel members ──────
        // so people in the channel chat see a "Meeting started" notification
        if (wasEmpty) {
            notifyChannelMeetingStarted(roomId, p);
        }

        log.info("[Meet] {} admitted to {} (size={})", p.getName(), roomId, room.size());
    }

    /**
     * Pushes a real-time notification to every channel member (except the starter)
     * so they see a banner even if they are not in the meeting page.
     */
    private void notifyChannelMeetingStarted(String roomId, Participant starter) {
        try {
            UUID channelUUID = UUID.fromString(roomId);
            chatRoomRepo.findByIdWithParticipants(channelUUID).ifPresent(chatRoom -> {
                String meetUrl = "/channels/" + roomId + "/meet";
                Map<String, String> notif = new HashMap<>();
                notif.put("type",        "MEET_STARTED");
                notif.put("senderName",  starter.getName());
                notif.put("senderAvatar",starter.getAvatar());
                notif.put("roomName",    chatRoom.getName());
                notif.put("message",     starter.getName() + " started a meeting in " + chatRoom.getName());
                notif.put("icon",        starter.getAvatar() != null && !starter.getAvatar().isBlank()
                        ? starter.getAvatar() : "📹");
                notif.put("link",        meetUrl);
                notif.put("roomId",      roomId);

                // Send to every channel member except the starter
                chatRoom.getParticipants().forEach(cp -> {
                    if (!cp.getUser().getId().toString().equals(starter.getUserId())) {
                        messaging.convertAndSend("/topic/user." + cp.getUser().getId(), notif);
                    }
                });
            });
        } catch (Exception e) {
            log.warn("[Meet] Could not send meeting-started notification: {}", e.getMessage());
        }
    }

    // ── ADMIT FROM WAITING ROOM (host action) ─────────────────────────

    @MessageMapping("/meet.admit/{roomId}")
    public void admit(@DestinationVariable String roomId,
                      @Payload Map<String, Object> payload,
                      Principal principal) {
        UserDetailsImpl me = extract(principal);
        if (!me.getId().toString().equals(hosts.get(roomId))) return; // host only

        String targetId = (String) payload.get("userId");
        boolean admitAll = Boolean.TRUE.equals(payload.get("admitAll"));

        Map<String, Participant> waiting = waitingRooms.getOrDefault(roomId, new ConcurrentHashMap<>());
        if (admitAll) {
            new ArrayList<>(waiting.values()).forEach(p -> {
                waiting.remove(p.getUserId());
                admitParticipant(roomId, p, false);
                messaging.convertAndSend("/topic/meet.user." + p.getUserId(), Map.of("type", "ADMITTED"));
            });
        } else if (targetId != null && waiting.containsKey(targetId)) {
            Participant p = waiting.remove(targetId);
            admitParticipant(roomId, p, false);
            messaging.convertAndSend("/topic/meet.user." + targetId, Map.of("type", "ADMITTED"));
        }
    }

    // ── LEAVE ──────────────────────────────────────────────────────────

    @MessageMapping("/meet.leave/{roomId}")
    public void leave(@DestinationVariable String roomId, Principal principal) {
        UserDetailsImpl me = extract(principal);
        String userId = me.getId().toString();

        LinkedHashMap<String, Participant> room = rooms.get(roomId);
        String leaverName = room != null && room.containsKey(userId)
                ? room.get(userId).getName() : "Unknown";

        if (room != null) {
            room.remove(userId);
            if (room.isEmpty()) {
                rooms.remove(roomId);
                hosts.remove(roomId);
                recording.remove(roomId);
                waitingRooms.remove(roomId);
            } else if (userId.equals(hosts.get(roomId))) {
                // Host left — promote next participant
                String newHost = room.keySet().iterator().next();
                hosts.put(roomId, newHost);
                messaging.convertAndSend("/topic/meet." + roomId, Map.of(
                        "type",    "HOST_CHANGED",
                        "hostId",  newHost
                ));
            }
        }

        messaging.convertAndSend("/topic/meet." + roomId, Map.of(
                "type",       "LEFT",
                "userId",     userId,
                "name",       leaverName,
                "totalCount", room != null ? room.size() : 0
        ));
    }

    // ── WebRTC SIGNALING ───────────────────────────────────────────────

    @MessageMapping("/meet.offer/{roomId}")
    public void offer(@DestinationVariable String roomId,
                      @Payload PeerSignal signal, Principal principal) {
        signal.setFromUserId(extract(principal).getId().toString());
        signal.setType("OFFER");
        messaging.convertAndSend("/topic/meet.user." + signal.getToUserId(), signal);
    }

    @MessageMapping("/meet.answer/{roomId}")
    public void answer(@DestinationVariable String roomId,
                       @Payload PeerSignal signal, Principal principal) {
        signal.setFromUserId(extract(principal).getId().toString());
        signal.setType("ANSWER");
        messaging.convertAndSend("/topic/meet.user." + signal.getToUserId(), signal);
    }

    @MessageMapping("/meet.ice/{roomId}")
    public void ice(@DestinationVariable String roomId,
                    @Payload PeerSignal signal, Principal principal) {
        signal.setFromUserId(extract(principal).getId().toString());
        signal.setType("ICE");
        messaging.convertAndSend("/topic/meet.user." + signal.getToUserId(), signal);
    }

    // ── HAND RAISE ─────────────────────────────────────────────────────

    @MessageMapping("/meet.hand/{roomId}")
    public void hand(@DestinationVariable String roomId,
                     @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        messaging.convertAndSend("/topic/meet." + roomId, Map.of(
                "type",   "HAND",
                "userId", me.getId().toString(),
                "raised", Boolean.TRUE.equals(payload.get("raised"))
        ));
    }

    // ── REACTIONS ──────────────────────────────────────────────────────

    @MessageMapping("/meet.reaction/{roomId}")
    public void reaction(@DestinationVariable String roomId,
                         @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        messaging.convertAndSend("/topic/meet." + roomId, Map.of(
                "type",   "REACTION",
                "userId", me.getId().toString(),
                "emoji",  payload.getOrDefault("emoji", "👍").toString()
        ));
    }

    // ── STATE (mic/cam/screen/spotlight) ──────────────────────────────

    @MessageMapping("/meet.state/{roomId}")
    public void state(@DestinationVariable String roomId,
                      @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        Map<String, Object> msg = new HashMap<>(payload);
        msg.put("type",   "STATE");
        msg.put("userId", me.getId().toString());
        messaging.convertAndSend("/topic/meet." + roomId, msg);
    }

    // ── IN-MEETING CHAT ────────────────────────────────────────────────

    @MessageMapping("/meet.chat/{roomId}")
    public void chat(@DestinationVariable String roomId,
                     @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        AppUser user = userService.getById(me.getId());
        String avatar = user.getProfile() != null ? user.getProfile().getAvatarUrl() : "";
        messaging.convertAndSend("/topic/meet." + roomId, Map.of(
                "type",    "CHAT",
                "userId",  me.getId().toString(),
                "name",    user.getFullName(),
                "avatar",  avatar != null ? avatar : "",
                "text",    payload.getOrDefault("text", "").toString(),
                "to",      payload.getOrDefault("to", "everyone").toString(),
                "toName",  payload.getOrDefault("toName", "Everyone").toString(),
                "ts",      Instant.now().toEpochMilli()
        ));
    }

    // ── HOST CONTROLS ─────────────────────────────────────────────────

    @MessageMapping("/meet.host/{roomId}")
    public void hostControl(@DestinationVariable String roomId,
                            @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        if (!me.getId().toString().equals(hosts.get(roomId)) &&
                !isAdmin(me)) return; // only host or system admin

        String action = (String) payload.getOrDefault("action", "");
        String targetId = (String) payload.get("targetUserId");

        switch (action) {
            case "MUTE_ALL" ->
                    messaging.convertAndSend("/topic/meet." + roomId, Map.of("type","HOST_MUTE_ALL","hostId",me.getId().toString()));
            case "MUTE_ONE" ->
                    messaging.convertAndSend("/topic/meet.user." + targetId, Map.of("type","HOST_MUTE","hostId",me.getId().toString()));
            case "UNMUTE_REQUEST" ->
                    messaging.convertAndSend("/topic/meet.user." + targetId, Map.of("type","HOST_UNMUTE_REQUEST"));
            case "REMOVE" -> {
                LinkedHashMap<String,Participant> room = rooms.get(roomId);
                String removedName = room != null && room.containsKey(targetId) ? room.get(targetId).getName() : "";
                if (room != null) room.remove(targetId);
                messaging.convertAndSend("/topic/meet.user." + targetId,
                        Map.of("type","REMOVED","reason", payload.getOrDefault("reason","Removed by host")));
                messaging.convertAndSend("/topic/meet." + roomId,
                        Map.of("type","LEFT","userId",targetId,"name",removedName,"totalCount",room!=null?room.size():0));
            }
            case "SPOTLIGHT" ->
                    messaging.convertAndSend("/topic/meet." + roomId,
                            Map.of("type","SPOTLIGHT","userId",targetId,"on", payload.getOrDefault("on", true)));
            case "LOWER_ALL_HANDS" ->
                    messaging.convertAndSend("/topic/meet." + roomId, Map.of("type","LOWER_ALL_HANDS"));
            case "LOCK" ->
                    messaging.convertAndSend("/topic/meet." + roomId,
                            Map.of("type","LOCK_CHANGED","locked", payload.getOrDefault("locked", true)));
            case "ALLOW_UNMUTE" ->
                    messaging.convertAndSend("/topic/meet." + roomId,
                            Map.of("type","POLICY","allowSelfUnmute", payload.getOrDefault("allowed", true)));
            case "STOP_SCREEN" ->
                    messaging.convertAndSend("/topic/meet.user." + targetId, Map.of("type","HOST_STOP_SCREEN"));
        }
    }

    // ── RECORDING ──────────────────────────────────────────────────────

    @MessageMapping("/meet.record/{roomId}")
    public void record(@DestinationVariable String roomId,
                       @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        if (!me.getId().toString().equals(hosts.get(roomId))) return;
        boolean started = Boolean.TRUE.equals(payload.get("started"));
        recording.put(roomId, started);
        messaging.convertAndSend("/topic/meet." + roomId, Map.of(
                "type",    "RECORDING",
                "started", started,
                "by",      me.getId().toString()
        ));
    }

    // ── POLL ───────────────────────────────────────────────────────────

    @MessageMapping("/meet.poll/{roomId}")
    public void poll(@DestinationVariable String roomId,
                     @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        Map<String, Object> msg = new HashMap<>(payload);
        msg.put("type",   "POLL");
        msg.put("fromId", me.getId().toString());
        messaging.convertAndSend("/topic/meet." + roomId, msg);
    }

    // ── RENAME ──────────────────────────────────────────────────────────

    @MessageMapping("/meet.rename/{roomId}")
    public void rename(@DestinationVariable String roomId,
                       @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        String newName = (String) payload.getOrDefault("name", "");
        if (newName.isBlank()) return;
        LinkedHashMap<String, Participant> room = rooms.get(roomId);
        if (room != null && room.containsKey(me.getId().toString())) {
            room.get(me.getId().toString()).setName(newName);
        }
        messaging.convertAndSend("/topic/meet." + roomId, Map.of(
                "type",    "RENAMED",
                "userId",  me.getId().toString(),
                "newName", newName
        ));
    }

    // ── WHITEBOARD ──────────────────────────────────────────────────────

    @MessageMapping("/meet.wb/{roomId}")
    public void whiteboard(@DestinationVariable String roomId,
                           @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        Map<String, Object> msg = new HashMap<>(payload);
        msg.put("type",   "WB");
        msg.put("userId", me.getId().toString());
        messaging.convertAndSend("/topic/meet." + roomId, msg);
    }

    // ── BREAKOUT ROOMS ──────────────────────────────────────────────────

    @MessageMapping("/meet.breakout/{roomId}")
    public void breakout(@DestinationVariable String roomId,
                         @Payload Map<String, Object> payload, Principal principal) {
        UserDetailsImpl me = extract(principal);
        if (!me.getId().toString().equals(hosts.get(roomId))) return;
        Map<String, Object> msg = new HashMap<>(payload);
        msg.put("type", "BREAKOUT");
        messaging.convertAndSend("/topic/meet." + roomId, msg);
    }

    // ── PUBLIC STATUS API ───────────────────────────────────────────────

    public boolean isActive(String roomId) {
        LinkedHashMap<String, Participant> room = rooms.get(roomId);
        return room != null && !room.isEmpty();
    }

    public int getParticipantCount(String roomId) {
        LinkedHashMap<String, Participant> room = rooms.get(roomId);
        return room != null ? room.size() : 0;
    }

    // ── HELPERS ────────────────────────────────────────────────────────

    private UserDetailsImpl extract(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token) {
            return (UserDetailsImpl) token.getPrincipal();
        }
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }

    private boolean isAdmin(UserDetailsImpl ud) {
        return ud.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().contains("ADMIN") || a.getAuthority().contains("SUPER_ADMIN"));
    }

    // ── DTOs ───────────────────────────────────────────────────────────

    @lombok.Data
    @lombok.AllArgsConstructor
    public static class Participant {
        private String  userId;
        private String  name;
        private String  avatar;
        private String  role;
        private long    joinedAt;
    }

    @Data
    public static class PeerSignal {
        private String type;
        private String fromUserId;
        private String toUserId;
        private String sdp;
        private Object candidate;
    }
}