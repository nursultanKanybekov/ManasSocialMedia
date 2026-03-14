package com.com.manasuniversityecosystem.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

/**
 * Captures the real client IP during the HTTP → WebSocket upgrade
 * and stores it in the WS session attributes so WebSocketEventListener
 * can read it on STOMP CONNECT.
 *
 * Handles reverse proxies (X-Forwarded-For, X-Real-IP).
 */
public class IpHandshakeInterceptor implements HandshakeInterceptor {

    private static final String ATTR_IP = "client-ip";

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler handler, Map<String, Object> attrs) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpReq = servletRequest.getServletRequest();
            attrs.put(ATTR_IP, extractIp(httpReq));
        }
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest req, ServerHttpResponse res,
                               WebSocketHandler handler, Exception ex) {}

    private String extractIp(HttpServletRequest req) {
        // Trust X-Forwarded-For (set by Render, Nginx, etc.)
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();  // take the first (real client) IP
        }
        String xri = req.getHeader("X-Real-IP");
        if (xri != null && !xri.isBlank()) return xri.trim();
        return req.getRemoteAddr();
    }

    public static String getAttrName() { return ATTR_IP; }
}