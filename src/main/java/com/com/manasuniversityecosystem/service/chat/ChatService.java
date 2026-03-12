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
import java.util.ArrayList;
import java.util.Collections;
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
        return sendMessage(sender, roomId, content, messageTypeStr, null, null);
    }

    @Transactional
    public ChatMessage sendMessage(AppUser sender, UUID roomId,
                                   String content, String messageTypeStr,
                                   UUID replyToId, UUID forwardedFromId) {
        ChatRoom room = findRoomWithParticipants(roomId);

        boolean isMember = room.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(sender.getId()));
        if (!isMember) {
            if (room.getRoomType() == RoomType.GLOBAL || room.getRoomType() == RoomType.FACULTY) {
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

        ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                .room(room)
                .sender(sender)
                .content(content)
                .messageType(msgType);

        // Handle reply
        if (replyToId != null) {
            messageRepo.findById(replyToId).ifPresent(original -> {
                builder.replyToId(original.getId())
                        .replyToContent(original.getContent().length() > 100
                                ? original.getContent().substring(0, 100) + "…"
                                : original.getContent())
                        .replyToSenderName(original.getSender().getFullName());
            });
        }

        // Handle forward
        if (forwardedFromId != null) {
            messageRepo.findById(forwardedFromId).ifPresent(original -> {
                builder.forwardedFrom(original.getSender().getFullName())
                        .content(original.getContent());
            });
        }

        ChatMessage msg = messageRepo.save(builder.build());
        ChatMessageDTO dto = ChatMessageDTO.fromWithAction(msg, "NEW");
        messagingTemplate.convertAndSend("/topic/chat." + roomId, dto);
        log.debug("Message sent: room={} sender={}", roomId, sender.getEmail());
        return msg;
    }

    // ── EDIT ─────────────────────────────────────────────────

    @Transactional
    public ChatMessage editMessage(UUID messageId, String newContent, AppUser user) {
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found."));
        if (!msg.getSender().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to edit this message.");
        }
        if (Boolean.TRUE.equals(msg.getIsDeleted())) {
            throw new IllegalStateException("Cannot edit a deleted message.");
        }
        msg.setContent(newContent);
        msg.setIsEdited(true);
        msg.setEditedAt(LocalDateTime.now());
        messageRepo.save(msg);

        ChatMessageDTO dto = ChatMessageDTO.fromWithAction(msg, "EDIT");
        messagingTemplate.convertAndSend("/topic/chat." + msg.getRoom().getId(), dto);
        return msg;
    }

    // ── DELETE ───────────────────────────────────────────────

    @Transactional
    public void deleteMessage(UUID messageId, AppUser user) {
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found."));
        if (!msg.getSender().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to delete this message.");
        }
        msg.softDelete();
        messageRepo.save(msg);

        ChatMessageDTO dto = ChatMessageDTO.fromWithAction(msg, "DELETE");
        messagingTemplate.convertAndSend("/topic/chat." + msg.getRoom().getId(), dto);
    }

    // ── PIN ──────────────────────────────────────────────────

    @Transactional
    public ChatMessage togglePin(UUID messageId, AppUser user) {
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found."));
        // Only room members can pin (no ownership check — anyone in room can pin)
        boolean nowPinned = !Boolean.TRUE.equals(msg.getIsPinned());
        msg.setIsPinned(nowPinned);
        messageRepo.save(msg);

        String action = nowPinned ? "PIN" : "UNPIN";
        ChatMessageDTO dto = ChatMessageDTO.fromWithAction(msg, action);
        messagingTemplate.convertAndSend("/topic/chat." + msg.getRoom().getId(), dto);
        return msg;
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getPinnedMessages(UUID roomId) {
        return messageRepo.findPinnedByRoomId(roomId);
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

            ChatMessageDTO systemMsg = ChatMessageDTO.builder()
                    .roomId(roomId)
                    .senderName("System")
                    .content(user.getFullName() + " joined the chat.")
                    .messageType("SYSTEM")
                    .action("NEW")
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
        List<ChatMessage> msgs = new ArrayList<>(pageResult.getContent());
        Collections.reverse(msgs);
        return msgs;
    }

    @Transactional(readOnly = true)
    public List<ChatRoom> getUserRooms(UUID userId) {
        return roomRepo.findRoomsByUserId(userId);
    }

    @Transactional(readOnly = true)
    public String getRoomDisplayName(ChatRoom room, UUID currentUserId) {
        if (room.getRoomType() != RoomType.DIRECT) {
            return room.getName() != null ? room.getName() : room.getRoomType().name();
        }
        return room.getParticipants().stream()
                .filter(p -> !p.getUser().getId().equals(currentUserId))
                .map(p -> p.getUser().getFullName())
                .findFirst()
                .orElse("Direct Message");
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID roomId, UUID userId) {
        return messageRepo.countUnread(roomId, userId);
    }

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