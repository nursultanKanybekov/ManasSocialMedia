package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import com.com.manasuniversityecosystem.domain.enums.RoomType;
import com.com.manasuniversityecosystem.repository.chat.ChatParticipantRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.chat.ChatService;
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

    /**
     * Client publishes to: /app/chat.send/{roomId}
     * chatService.sendMessage() is @Transactional and commits before returning.
     * Notifications are pushed HERE after the transaction is done — no lazy-load,
     * no session state issues, works for every role.
     */
    @MessageMapping("/chat.send/{roomId}")
    public void sendMessage(@DestinationVariable UUID roomId,
                            @Payload SendMessageRequest request,
                            Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser sender = userService.getById(details.getId());

        // Transaction commits when this returns
        ChatMessage msg = chatService.sendMessage(sender, roomId,
                request.getContent(), request.getMessageType());

        // Push notifications after commit — fresh state, any role
        try {
            pushChatNotifications(sender, roomId, msg);
        } catch (Exception e) {
            log.error("[ChatNotif] push failed room={} sender={}: {}", roomId, sender.getEmail(), e.getMessage(), e);
        }
    }

    private void pushChatNotifications(AppUser sender, UUID roomId, ChatMessage msg) {
        String content  = msg.getContent();
        String roomName = msg.getRoom().getName();
        RoomType type   = msg.getRoom().getRoomType();

        String label   = (roomName != null && !roomName.isBlank()) ? roomName : "Direct Message";
        String preview = (content != null && content.length() > 70) ? content.substring(0, 70) + "..." : content;
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
        notif.put("message",      sender.getFullName() + ": " + preview);
        notif.put("link",         link);

        // Fresh DB query after transaction commit — works for any sender role
        List<AppUser> recipients = participantRepo.findParticipantsExcluding(roomId, sender.getId());
        log.info("[ChatNotif] room={} type={} sender={} role={} recipients={}",
                roomId, type, sender.getEmail(), sender.getRole(), recipients.size());

        recipients.forEach(r -> {
            messaging.convertAndSend("/topic/user." + r.getId(), notif);
            log.info("[ChatNotif] -> uid={} ({})", r.getId(), r.getEmail());
        });
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
}