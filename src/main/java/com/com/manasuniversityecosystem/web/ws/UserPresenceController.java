package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.config.WebSocketEventListener;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.OnlineUserTracker;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Receives geolocation data sent by the browser after WebSocket connects.
 * The browser uses navigator.geolocation + Nominatim reverse-geocode,
 * then sends the result here. This is far more accurate than IP-based lookup.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class UserPresenceController {

    private final OnlineUserTracker       onlineTracker;
    private final WebSocketEventListener  wsEventListener;

    /**
     * Browser sends to: /app/user.geo
     * Payload: { city, country, countryCode, lat, lng }
     */
    @MessageMapping("/user.geo")
    public void updateGeo(@Payload GeoPayload payload,
                          Principal principal,
                          SimpMessageHeaderAccessor headerAccessor) {
        String sessionId = headerAccessor.getSessionId();
        if (sessionId == null) return;

        // Validate we have something useful
        String city    = blank(payload.getCity())    ? "Unknown" : payload.getCity().trim();
        String country = blank(payload.getCountry()) ? "Unknown" : payload.getCountry().trim();
        String cc      = blank(payload.getCountryCode()) ? "" : payload.getCountryCode().trim().toUpperCase();

        log.debug("[Geo] browser update — session={} city={} country={} cc={}",
                sessionId, city, country, cc);

        // Update the stored session
        onlineTracker.updateGeoFromBrowser(sessionId, city, payload.getRegion(), country, cc,
                payload.getLat(), payload.getLng());

        // Rebroadcast so the super-admin dashboard refreshes immediately
        wsEventListener.broadcastFull();
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    @Data
    public static class GeoPayload {
        private String city;
        private String region;
        private String country;
        private String countryCode;
        private Double lat;
        private Double lng;
    }
}