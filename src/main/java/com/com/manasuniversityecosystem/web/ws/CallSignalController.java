package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * Routes WebRTC signaling messages between two peers via STOMP.
 *
 * Flow:
 *   Caller  → /app/call.invite   → recipient gets INVITE on /topic/call.{recipientId}
 *   Callee  → /app/call.answer   → caller gets ANSWER
 *   Either  → /app/call.ice      → other side gets ICE candidate
 *   Either  → /app/call.end      → other side gets HANGUP
 *   Callee  → /app/call.reject   → caller gets REJECT
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class CallSignalController {

    private final SimpMessagingTemplate messaging;
    private final UserService userService;

    /** Step 1 – Caller invites recipient, includes SDP offer */
    @MessageMapping("/call.invite")
    public void invite(@Payload CallSignal signal, Principal principal) {
        UserDetailsImpl me = extract(principal);
        AppUser caller = userService.getById(me.getId());
        signal.setCallerId(me.getId().toString());
        signal.setCallerName(caller.getFullName());
        String avatar = caller.getProfile() != null ? caller.getProfile().getAvatarUrl() : null;
        signal.setCallerAvatar(avatar != null ? avatar : "");
        signal.setType("INVITE");
        log.debug("call.invite {} -> {}", me.getId(), signal.getTargetUserId());
        messaging.convertAndSend("/topic/call." + signal.getTargetUserId(), signal);
    }

    /** Step 2 – Callee answers with SDP answer */
    @MessageMapping("/call.answer")
    public void answer(@Payload CallSignal signal, Principal principal) {
        UserDetailsImpl me = extract(principal);
        signal.setCallerId(me.getId().toString());
        signal.setType("ANSWER");
        log.debug("call.answer {} -> {}", me.getId(), signal.getTargetUserId());
        messaging.convertAndSend("/topic/call." + signal.getTargetUserId(), signal);
    }

    /** ICE candidate trickle */
    @MessageMapping("/call.ice")
    public void ice(@Payload CallSignal signal, Principal principal) {
        UserDetailsImpl me = extract(principal);
        signal.setCallerId(me.getId().toString());
        signal.setType("ICE");
        messaging.convertAndSend("/topic/call." + signal.getTargetUserId(), signal);
    }

    /** Either side ends the call */
    @MessageMapping("/call.end")
    public void end(@Payload CallSignal signal, Principal principal) {
        UserDetailsImpl me = extract(principal);
        signal.setCallerId(me.getId().toString());
        signal.setType("HANGUP");
        log.debug("call.end {} -> {}", me.getId(), signal.getTargetUserId());
        messaging.convertAndSend("/topic/call." + signal.getTargetUserId(), signal);
    }

    /** Callee rejects incoming call */
    @MessageMapping("/call.reject")
    public void reject(@Payload CallSignal signal, Principal principal) {
        UserDetailsImpl me = extract(principal);
        signal.setCallerId(me.getId().toString());
        signal.setType("REJECT");
        log.debug("call.reject {} -> {}", me.getId(), signal.getTargetUserId());
        messaging.convertAndSend("/topic/call." + signal.getTargetUserId(), signal);
    }

    private UserDetailsImpl extract(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token) {
            return (UserDetailsImpl) token.getPrincipal();
        }
        throw new IllegalStateException("Unexpected principal: " + principal.getClass());
    }

    @Data
    public static class CallSignal {
        private String type;           // INVITE | ANSWER | ICE | HANGUP | REJECT
        private String targetUserId;   // UUID of the recipient
        private String callerId;       // filled by server
        private String callerName;     // filled by server
        private String callerAvatar;   // filled by server
        private String roomId;         // the DM room this call belongs to
        private String sdp;            // SDP offer or answer
        private Object candidate;      // ICE candidate object
    }
}