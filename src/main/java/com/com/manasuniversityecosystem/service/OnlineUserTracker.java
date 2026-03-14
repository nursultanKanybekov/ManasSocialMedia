package com.com.manasuniversityecosystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe store of all active WebSocket sessions.
 * Stores full metadata: user info, IP, geo, lat/lng, connection time.
 */
@Service
@Slf4j
public class OnlineUserTracker {

    /** sessionId → OnlineSessionInfo */
    private final ConcurrentHashMap<String, OnlineSessionInfo> sessions = new ConcurrentHashMap<>();

    // ── Write ──────────────────────────────────────────────────────────

    public void connect(OnlineSessionInfo info) {
        sessions.put(info.getSessionId(), info);
        log.debug("[Online] + {} ({}) from {} — total: {}",
                info.getFullName(), info.getSessionId(), info.getIpAddress(), countOnline());
    }

    public void disconnect(String sessionId) {
        OnlineSessionInfo removed = sessions.remove(sessionId);
        if (removed != null)
            log.debug("[Online] - {} — total: {}", removed.getFullName(), countOnline());
    }

    /** Update geo from server-side IP lookup (fallback) */
    public void updateGeo(String sessionId, GeoLookupService.GeoInfo geo) {
        OnlineSessionInfo info = sessions.get(sessionId);
        if (info == null) return;
        // Only overwrite if browser hasn't already sent more accurate data
        if (!info.isGeoFromBrowser()) {
            info.setCity(geo.city());
            info.setRegionName(geo.regionName());
            info.setCountry(geo.country());
            info.setCountryCode(geo.countryCode());
        }
    }

    /** Update geo from browser navigator.geolocation (most accurate) */
    public void updateGeoFromBrowser(String sessionId,
                                     String city, String region,
                                     String country, String countryCode,
                                     Double lat, Double lng) {
        OnlineSessionInfo info = sessions.get(sessionId);
        if (info == null) return;
        info.setCity(city);
        info.setRegionName(region != null ? region : "");
        info.setCountry(country);
        info.setCountryCode(countryCode);
        if (lat != null) info.setLat(lat);
        if (lng != null) info.setLng(lng);
        info.setGeoFromBrowser(true);
    }

    // ── Read ───────────────────────────────────────────────────────────

    /** Total distinct online users (multiple tabs = 1 user) */
    public int countOnline() {
        return (int) sessions.values().stream()
                .map(OnlineSessionInfo::getUserId).distinct().count();
    }

    /** One entry per distinct user (oldest session wins) */
    public List<OnlineSessionInfo> getOnlineUsers() {
        return sessions.values().stream()
                .collect(Collectors.toMap(
                        OnlineSessionInfo::getUserId,
                        s -> s,
                        (a, b) -> a.getConnectedAt().isBefore(b.getConnectedAt()) ? a : b
                ))
                .values().stream()
                .sorted(Comparator.comparing(OnlineSessionInfo::getConnectedAt))
                .collect(Collectors.toList());
    }

    /** Count per country name */
    public Map<String, Long> getCountryBreakdown() {
        return sessions.values().stream()
                .filter(s -> s.getCountry() != null && !s.getCountry().isBlank()
                        && !s.getCountry().equals("Resolving…"))
                .collect(Collectors.groupingBy(
                        s -> s.getCountry(),
                        Collectors.counting()
                ));
    }

    public boolean isOnline(UUID userId) {
        return sessions.values().stream().anyMatch(s -> userId.equals(s.getUserId()));
    }
}