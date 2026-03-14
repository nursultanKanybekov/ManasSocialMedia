package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class OnlineSessionInfo {

    private UUID     userId;
    private String   sessionId;
    private String   fullName;
    private UserRole role;
    private String   avatarUrl;

    // Network
    private String  ipAddress;

    // Geo — set by IP lookup first, then overwritten by browser geo
    private String  city;
    private String  regionName;
    private String  country;
    private String  countryCode;
    private Double  lat;
    private Double  lng;

    /** true once the browser has confirmed geo — prevents IP lookup overwriting it */
    @Builder.Default
    private boolean geoFromBrowser = false;

    // Timing
    private Instant connectedAt;

    public Duration getDuration() {
        return Duration.between(connectedAt, Instant.now());
    }

    public String getDurationLabel() {
        Duration d = getDuration();
        long h = d.toHours();
        long m = d.toMinutesPart();
        long s = d.toSecondsPart();
        if (h > 0)      return h + "h " + m + "m";
        else if (m > 0) return m + "m " + s + "s";
        else            return s + "s";
    }

    /** Flag emoji from ISO 3166-1 alpha-2 country code */
    public String getFlagEmoji() {
        if (countryCode == null || countryCode.length() != 2) return "🌍";
        int base = 0x1F1E6 - 'A';
        return new String(new int[]{
                base + countryCode.toUpperCase().charAt(0),
                base + countryCode.toUpperCase().charAt(1)
        }, 0, 2);
    }

    /** Whether we have precise coordinates from the browser */
    public boolean hasCoordinates() {
        return lat != null && lng != null;
    }
}