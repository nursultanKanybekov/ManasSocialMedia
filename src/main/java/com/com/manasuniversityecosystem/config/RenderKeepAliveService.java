package com.com.manasuniversityecosystem.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Prevents Render free-tier cold starts by pinging the app's health
 * endpoint every 14 minutes.  Render sleeps services after ~15 minutes
 * of inactivity, so this keeps the JVM warm and eliminates the
 * "APPLICATION LOADING" splash screen for real users.
 *
 * Set APP_BASE_URL environment variable on Render to your service URL,
 * e.g.  https://your-app-name.onrender.com
 * If the variable is absent the task is silently skipped (safe for local dev).
 */
@Slf4j
@Component
public class RenderKeepAliveService {

    @Value("${app.base-url:}")
    private String appBaseUrl;

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** Runs every 14 minutes (840 000 ms). */
    @Scheduled(fixedDelay = 840_000, initialDelay = 60_000)
    public void keepAlive() {
        if (appBaseUrl == null || appBaseUrl.isBlank()) {
            // No URL configured — skip (local dev)
            return;
        }

        String url = appBaseUrl.replaceAll("/+$", "") + "/actuator/health";
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .GET()
                    .build();

            HttpResponse<Void> res = HTTP.send(req, HttpResponse.BodyHandlers.discarding());
            log.debug("Keep-alive ping → {} | status {}", url, res.statusCode());

        } catch (Exception e) {
            // Non-fatal — just log at debug level so it doesn't clutter production logs
            log.debug("Keep-alive ping failed ({}): {}", url, e.getMessage());
        }
    }
}