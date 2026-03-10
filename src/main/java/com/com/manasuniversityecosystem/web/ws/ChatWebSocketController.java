package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
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
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final UserService userService;
    private final SimpMessagingTemplate messaging;

    private UserDetailsImpl extractPrincipal(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token) {
            return (UserDetailsImpl) token.getPrincipal();
        }
        throw new IllegalStateException("Unexpected principal type: " + principal.getClass());
    }

    /**
     * Client publishes to: /app/chat.send/{roomId}
     * Payload: SendMessageRequest JSON
     * Broadcast to: /topic/chat.{roomId}
     */
    @MessageMapping("/chat.send/{roomId}")
    public void sendMessage(@DestinationVariable UUID roomId,
                            @Payload SendMessageRequest request,
                            Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser sender = userService.getById(details.getId());
        chatService.sendMessage(sender, roomId,
                request.getContent(), request.getMessageType());
        log.debug("WS message: room={} sender={}", roomId, sender.getEmail());
    }

    /**
     * Client publishes typing indicator to: /app/chat.typing/{roomId}
     * Broadcasts to /topic/typing.{roomId}
     */
    @MessageMapping("/chat.typing/{roomId}")
    public void typingIndicator(@DestinationVariable UUID roomId,
                                Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        TypingIndicatorDTO dto = new TypingIndicatorDTO(details.getId(), details.getFullName());
        messaging.convertAndSend("/topic/typing." + roomId, dto);
    }

    /**
     * Client publishes to: /app/chat.join/{roomId}
     */
    @MessageMapping("/chat.join/{roomId}")
    public void joinRoom(@DestinationVariable UUID roomId,
                         Principal principal) {
        UserDetailsImpl details = extractPrincipal(principal);
        AppUser user = userService.getById(details.getId());
        chatService.joinRoom(user, roomId);
        log.info("WS join: user={} room={}", user.getEmail(), roomId);
    }
}
