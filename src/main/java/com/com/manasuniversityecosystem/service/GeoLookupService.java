package com.com.manasuniversityecosystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Resolves IP addresses to city / country using the free ip-api.com API.
 * Results are cached in-memory so the same IP is never looked up twice.
 * Lookup is done asynchronously so it never blocks the STOMP connect thread.
 */
@Service
@Slf4j
public class GeoLookupService {

    /** city | regionName | country | countryCode */
    public record GeoInfo(String city, String regionName, String country, String countryCode) {}

    private static final GeoInfo UNKNOWN = new GeoInfo("Unknown", "", "Unknown", "");
    private static final GeoInfo LOCAL   = new GeoInfo("Localhost", "", "Local", "🖥");

    /** Simple IP → GeoInfo cache (never evicted — IPs don't change geo often) */
    private final ConcurrentHashMap<String, GeoInfo> cache = new ConcurrentHashMap<>();

    /**
     * Looks up geo info for the given IP asynchronously.
     * Calls {@code callback} on the calling thread once the result is ready.
     */
    @Async
    public void lookupAsync(String ip, Consumer<GeoInfo> callback) {
        if (ip == null || ip.isBlank()) { callback.accept(UNKNOWN); return; }
        if (ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.startsWith("192.168") || ip.startsWith("10.")) {
            callback.accept(LOCAL); return;
        }
        if (cache.containsKey(ip)) { callback.accept(cache.get(ip)); return; }

        try {
            URL url = new URL("http://ip-api.com/json/" + ip +
                    "?fields=status,country,countryCode,regionName,city");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "ManasUniversity/1.0");

            if (conn.getResponseCode() == 200) {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                GeoInfo info = parseJson(sb.toString());
                cache.put(ip, info);
                callback.accept(info);
            } else {
                callback.accept(UNKNOWN);
            }
        } catch (Exception e) {
            log.debug("[Geo] lookup failed for {}: {}", ip, e.getMessage());
            callback.accept(UNKNOWN);
        }
    }

    /** Tiny hand-rolled JSON parser (avoids adding Jackson dependency) */
    private GeoInfo parseJson(String json) {
        String status      = extract(json, "status");
        if (!"success".equals(status)) return UNKNOWN;
        String city        = extract(json, "city");
        String regionName  = extract(json, "regionName");
        String country     = extract(json, "country");
        String countryCode = extract(json, "countryCode");
        return new GeoInfo(
                city        != null ? city        : "Unknown",
                regionName  != null ? regionName  : "",
                country     != null ? country     : "Unknown",
                countryCode != null ? countryCode : ""
        );
    }

    private String extract(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        return end < 0 ? null : json.substring(start, end);
    }
}