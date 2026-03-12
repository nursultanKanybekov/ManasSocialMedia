package com.com.manasuniversityecosystem.service.chat;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.domain.enums.RoomType;
import com.com.manasuniversityecosystem.repository.chat.ChatMessageRepository;
import com.com.manasuniversityecosystem.repository.chat.ChatParticipantRepository;
import com.com.manasuniversityecosystem.repository.chat.ChatRoomRepository;
import com.com.manasuniversityecosystem.web.ws.ChatMessageDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRoomRepository roomRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatParticipantRepository participantRepo;
    private final SimpMessagingTemplate messagingTemplate;

    // ── SEND ─────────────────────────────────────────────────

    @Transactional
    public ChatMessage sendMessage(AppUser sender, UUID roomId,
                                   String content, String messageTypeStr) {
        ChatRoom room = findRoomWithParticipants(roomId);

        // Authorization check
        boolean isMember = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(sender.getId()));
        if (!isMember) {
            // Auto-join GLOBAL and FACULTY rooms
            if (room.getRoomType() == RoomType.GLOBAL
                    || room.getRoomType() == RoomType.FACULTY) {
                joinRoom(sender, roomId);
            } else {
                throw new SecurityException("User is not a member of this chat room.");
            }
        }

        ChatMessage.MessageType msgType;
        try {
            msgType = messageTypeStr != null
                    ? ChatMessage.MessageType.valueOf(messageTypeStr.toUpperCase())
                    : ChatMessage.MessageType.TEXT;
        } catch (IllegalArgumentException e) {
            msgType = ChatMessage.MessageType.TEXT;
        }

        ChatMessage msg = ChatMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .messageType(msgType)
                .build();
        messageRepo.save(msg);

        // STOMP broadcast to all room subscribers
        ChatMessageDTO dto = ChatMessageDTO.from(msg);
        messagingTemplate.convertAndSend("/topic/chat." + roomId, dto);

        log.debug("Message sent: room={} sender={}", roomId, sender.getEmail());
        return msg;
    }

    // ── ROOMS ─────────────────────────────────────────────────

    @Transactional
    public ChatRoom getOrCreateDirectRoom(AppUser userA, AppUser userB) {
        return roomRepo.findDirectRoom(userA.getId(), userB.getId())
                .orElseGet(() -> {
                    ChatRoom room = ChatRoom.builder()
                            .roomType(RoomType.DIRECT)
                            .createdBy(userA)
                            .build();
                    room.addParticipant(userA);
                    room.addParticipant(userB);
                    ChatRoom saved = roomRepo.save(room);
                    log.info("Direct room created: {} <-> {}", userA.getEmail(), userB.getEmail());
                    return saved;
                });
    }

    @Transactional
    public ChatRoom createGroupRoom(AppUser creator, String name, List<AppUser> members) {
        ChatRoom room = ChatRoom.builder()
                .name(name)
                .roomType(RoomType.GROUP)
                .createdBy(creator)
                .build();
        room.addParticipant(creator);
        members.forEach(m -> {
            if (!m.getId().equals(creator.getId())) room.addParticipant(m);
        });
        return roomRepo.save(room);
    }

    @Transactional
    public ChatRoom getOrCreateFacultyRoom(AppUser user) {
        if (user.getFaculty() == null) {
            throw new IllegalStateException("User has no faculty assigned.");
        }
        UUID facultyId = user.getFaculty().getId();
        return roomRepo.findByFacultyIdAndRoomType(facultyId, RoomType.FACULTY)
                .orElseGet(() -> {
                    ChatRoom room = ChatRoom.builder()
                            .name(user.getFaculty().getName() + " Channel")
                            .roomType(RoomType.FACULTY)
                            .faculty(user.getFaculty())
                            .createdBy(user)
                            .build();
                    room.addParticipant(user);
                    return roomRepo.save(room);
                });
    }

    @Transactional
    public ChatRoom getGlobalRoom(AppUser user) {
        ChatRoom room = roomRepo.findFirstByRoomType(RoomType.GLOBAL)
                .orElseGet(() -> {
                    ChatRoom newRoom = ChatRoom.builder()
                            .name("Global Chat")
                            .roomType(RoomType.GLOBAL)
                            .createdBy(user)
                            .build();
                    return roomRepo.save(newRoom);
                });
        // Auto-join global room on first access
        boolean isMember = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(user.getId()));
        if (!isMember) {
            room.addParticipant(user);
            roomRepo.save(room);
        }
        return room;
    }

    @Transactional
    public void joinRoom(AppUser user, UUID roomId) {
        ChatRoom room = findRoomWithParticipants(roomId);
        boolean already = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(user.getId()));
        if (!already) {
            room.addParticipant(user);
            roomRepo.save(room);

            // Notify other participants via STOMP
            ChatMessageDTO systemMsg = ChatMessageDTO.builder()
                    .roomId(roomId)
                    .senderName("System")
                    .content(user.getFullName() + " joined the chat.")
                    .messageType("SYSTEM")
                    .createdAt(LocalDateTime.now())
                    .build();
            messagingTemplate.convertAndSend("/topic/chat." + roomId, systemMsg);
        }
    }

    @Transactional
    public void leaveRoom(AppUser user, UUID roomId) {
        ChatRoom room = findRoomWithParticipants(roomId);
        room.getParticipants().removeIf(p -> p.getUser().getId().equals(user.getId()));
        roomRepo.save(room);
    }

    @Transactional
    public void markRoomAsRead(AppUser user, UUID roomId) {
        ChatRoom room = findRoomWithParticipants(roomId);
        room.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(user.getId()))
                .findFirst()
                .ifPresent(p -> {
                    p.markRead();
                    roomRepo.save(room);
                });
    }

    // ── HISTORY ──────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatMessage> getRoomHistory(UUID roomId, int page) {
        Page<ChatMessage> pageResult = messageRepo.findByRoomIdOrderByCreatedAtDesc(
                roomId, PageRequest.of(page, 30));
        // Reverse to show oldest-first for display
        List<ChatMessage> msgs = new java.util.ArrayList<>(pageResult.getContent());
        java.util.Collections.reverse(msgs);
        return msgs;
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> getUserRooms(UUID userId) {
        return roomRepo.findRoomsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID roomId, UUID userId) {
        return messageRepo.countUnread(roomId, userId);
    }

    @Transactional
    public void deleteMessage(UUID messageId, AppUser user) {
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found."));
        if (!msg.getSender().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to delete this message.");
        }
        msg.softDelete();
        messageRepo.save(msg);

        // Broadcast deletion event
        ChatMessageDTO dto = ChatMessageDTO.from(msg);
        messagingTemplate.convertAndSend("/topic/chat." + msg.getRoom().getId(), dto);
    }

    // ── PUBLIC room fetch ─────────────────────────────────────

    @Transactional(readOnly = true)
    public ChatRoom getRoomWithParticipants(UUID roomId) {
        return roomRepo.findByIdWithParticipants(roomId).orElse(null);
    }

    // ── HELPERS ──────────────────────────────────────────────

    private ChatRoom findRoomWithParticipants(UUID roomId) {
        return roomRepo.findByIdWithParticipants(roomId)
                .orElseThrow(() -> new IllegalArgumentException("Chat room not found: " + roomId));
    }
}