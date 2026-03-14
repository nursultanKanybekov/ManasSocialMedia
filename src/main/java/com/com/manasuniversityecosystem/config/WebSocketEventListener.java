package com.com.manasuniversityecosystem.config;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.GeoLookupService;
import com.com.manasuniversityecosystem.service.OnlineSessionInfo;
import com.com.manasuniversityecosystem.service.OnlineUserTracker;
import com.com.manasuniversityecosystem.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final OnlineUserTracker      onlineTracker;
    private final GeoLookupService       geoLookup;
    private final SimpMessagingTemplate  messaging;
    private final UserService            userService;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        UserDetailsImpl ud = extractDetails(sha.getUser());
        if (ud == null) return;

        String sessionId = sha.getSessionId();
        String ip        = extractIp(sha);

        AppUser user;
        try { user = userService.getById(ud.getId()); }
        catch (Exception e) { log.warn("[WS] cannot load user {}", ud.getId()); return; }

        String avatar = (user.getProfile() != null && user.getProfile().getAvatarUrl() != null)
                ? user.getProfile().getAvatarUrl() : null;

        OnlineSessionInfo info = OnlineSessionInfo.builder()
                .userId(ud.getId())
                .sessionId(sessionId)
                .fullName(user.getFullName())
                .role(user.getRole())
                .avatarUrl(avatar)
                .ipAddress(ip)
                .connectedAt(Instant.now())
                .city("Resolving…")
                .country("Resolving…")
                .countryCode("")
                .geoFromBrowser(false)
                .build();

        onlineTracker.connect(info);
        broadcastCount();
        broadcastFull();

        // IP-based geo as fallback — browser will send more accurate data shortly
        geoLookup.lookupAsync(ip, geo -> {
            onlineTracker.updateGeo(sessionId, geo);
            broadcastFull();
        });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor sha = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = sha.getSessionId();
        if (sessionId == null) return;
        onlineTracker.disconnect(sessionId);
        broadcastCount();
        broadcastFull();
    }

    // ── Broadcast helpers (public so UserPresenceController can call them) ──

    public void broadcastCount() {
        messaging.convertAndSend("/topic/admin.online",
                Map.of("onlineCount", onlineTracker.countOnline()));
    }

    public void broadcastFull() {
        List<Map<String, Object>> users = onlineTracker.getOnlineUsers().stream()
                .map(s -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("userId",      s.getUserId().toString());
                    m.put("fullName",    s.getFullName());
                    m.put("role",        s.getRole() != null ? s.getRole().name() : "");
                    m.put("avatarUrl",   s.getAvatarUrl()   != null ? s.getAvatarUrl()   : "");
                    m.put("ipAddress",   s.getIpAddress()   != null ? s.getIpAddress()   : "");
                    m.put("city",        s.getCity()        != null ? s.getCity()        : "");
                    m.put("country",     s.getCountry()     != null ? s.getCountry()     : "");
                    m.put("countryCode", s.getCountryCode() != null ? s.getCountryCode() : "");
                    m.put("flagEmoji",   s.getFlagEmoji());
                    m.put("duration",    s.getDurationLabel());
                    m.put("lat",         s.getLat());
                    m.put("lng",         s.getLng());
                    m.put("geoSource",   s.isGeoFromBrowser() ? "browser" : "ip");
                    return m;
                })
                .toList();

        Map<String, Object> payload = new HashMap<>();
        payload.put("onlineCount", onlineTracker.countOnline());
        payload.put("users",       users);
        payload.put("countries",   onlineTracker.getCountryBreakdown());

        messaging.convertAndSend("/topic/superadmin.online", payload);
    }

    // ── Private helpers ────────────────────────────────────────────────

    private UserDetailsImpl extractDetails(Principal principal) {
        if (principal instanceof UsernamePasswordAuthenticationToken token
                && token.getPrincipal() instanceof UserDetailsImpl ud) {
            return ud;
        }
        return null;
    }

    private String extractIp(StompHeaderAccessor sha) {
        Map<String, Object> attrs = sha.getSessionAttributes();
        if (attrs != null) {
            Object ip = attrs.get(IpHandshakeInterceptor.getAttrName());
            if (ip instanceof String s && !s.isBlank()) return s;
        }
        return "unknown";
    }
}