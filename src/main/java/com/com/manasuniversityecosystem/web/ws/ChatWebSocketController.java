package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.domain.enums.RoomType;
import com.com.manasuniversityecosystem.repository.chat.ChatParticipantRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final UserService userService;
    private final ChatParticipantRepository participantRepo;
    private final SimpMessagingTemplate messaging;

    private UserDetailsImpl extractPrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token) {
            return (UserDetailsImpl) token.getPrincipal();
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
    }

    /** Client publishes to: /app/chat.send/{roomId} */
    @MessageMapping("/chat.send/{roomId}")
    public void sendMessage(@DestinationVariable UUID roomId,
                            @Payload SendMessageRequest request,
                            Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser sender = userService.getById(details.getId());

        ChatRoom room = chatService.getRoomWithParticipants(roomId);
        String msgContent = request.getContent();

        chatService.sendMessage(sender, roomId, msgContent, request.getMessageType(),
                request.getReplyToId(), request.getForwardedFromId());

        try {
            pushChatNotifications(sender, roomId, room, msgContent);
        } catch (Exception e) {
            log.error("[ChatNotif] push failed room={} sender={}: {}", roomId, sender.getEmail(), e.getMessage(), e);
        }
    }

    /** Client publishes to: /app/chat.edit/{messageId} */
    @MessageMapping("/chat.edit/{messageId}")
    public void editMessage(@DestinationVariable UUID messageId,
                            @Payload EditMessageRequest request,
                            Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser user = userService.getById(details.getId());
        chatService.editMessage(messageId, request.getContent(), user);
    }

    /** Client publishes to: /app/chat.delete/{messageId} */
    @MessageMapping("/chat.delete/{messageId}")
    public void deleteMessage(@DestinationVariable UUID messageId,
                              Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser user = userService.getById(details.getId());
        chatService.deleteMessage(messageId, user);
    }

    /** Client publishes to: /app/chat.pin/{messageId} */
    @MessageMapping("/chat.pin/{messageId}")
    public void pinMessage(@DestinationVariable UUID messageId,
                           Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser user = userService.getById(details.getId());
        chatService.togglePin(messageId, user);
    }

    @MessageMapping("/chat.typing/{roomId}")
    public void typingIndicator(@DestinationVariable UUID roomId, Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        TypingIndicatorDTO dto = new TypingIndicatorDTO(details.getId(), details.getFullName());
        messaging.convertAndSend("/topic/typing." + roomId, dto);
    }

    @MessageMapping("/chat.join/{roomId}")
    public void joinRoom(@DestinationVariable UUID roomId, Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser user = userService.getById(details.getId());
        chatService.joinRoom(user, roomId);
        log.info("WS join: user={} room={}", user.getEmail(), roomId);
    }

    // ── helpers ───────────────────────────────────────────────

    private void pushChatNotifications(AppUser sender, UUID roomId, ChatRoom room, String content) {
        String roomName = room.getName();
        RoomType type   = room.getRoomType();

        String label   = (roomName != null && !roomName.isBlank()) ? roomName : "Direct Message";
        String avatar  = (sender.getProfile() != null && sender.getProfile().getAvatarUrl() != null)
                ? sender.getProfile().getAvatarUrl() : "";
        String link = switch (type) {
            case DIRECT  -> "/chat/room/" + roomId;
            case GLOBAL  -> "/chat/room/" + roomId;
            case GROUP   -> "/channels/" + roomId;
            case FACULTY -> "/chat/faculty-group/" + roomId;
            default      -> "/chat/room/" + roomId;
        };

        Map<String, String> notif = new HashMap<>();
        notif.put("type",         "CHAT_MESSAGE");
        notif.put("senderName",   sender.getFullName());
        notif.put("senderAvatar", avatar);
        notif.put("icon",         avatar.isBlank() ? "msg" : avatar);
        notif.put("roomName",     label);
        String preview = (content != null && content.length() > 70) ? content.substring(0, 70) + "..." : content;
        notif.put("message",      sender.getFullName() + ": " + preview);
        notif.put("link",         link);

        List<AppUser> recipients = participantRepo.findParticipantsExcluding(roomId, sender.getId());
        recipients.forEach(r -> messaging.convertAndSend("/topic/user." + r.getId(), notif));
    }

    // ── inner DTOs ────────────────────────────────────────────

    @Data
    public static class EditMessageRequest {
        private String content;
    }
}