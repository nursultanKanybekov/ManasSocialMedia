package com.com.manasuniversityecosystem.service;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

/**
 * Tracks failed login attempts per HTTP session.
 * After MAX_ATTEMPTS failures, CAPTCHA is required.
 * Session-based tracking works reliably on localhost (IPv4/IPv6).
 */
@Service
public class LoginAttemptService {

    public static final int MAX_ATTEMPTS = 5;
    public static final String SESSION_KEY_ATTEMPTS = "loginAttempts";
    public static final String SESSION_KEY_CAPTCHA  = "captchaRequired";

    /** Record a failed attempt in the session */
    public void loginFailed(HttpSession session) {
        int current = getAttempts(session);
        int next = current + 1;
        session.setAttribute(SESSION_KEY_ATTEMPTS, next);
        session.setAttribute(SESSION_KEY_CAPTCHA,  next >= MAX_ATTEMPTS);
    }

    /** Clear counters after successful login */
    public void loginSucceeded(HttpSession session) {
        if (session != null) {
            session.removeAttribute(SESSION_KEY_ATTEMPTS);
            session.removeAttribute(SESSION_KEY_CAPTCHA);
        }
    }

    /** How many failed attempts in this session */
    public int getAttempts(HttpSession session) {
        if (session == null) return 0;
        Object val = session.getAttribute(SESSION_KEY_ATTEMPTS);
        return val instanceof Integer ? (Integer) val : 0;
    }

    /** True if this session requires a CAPTCHA */
    public boolean isCaptchaRequired(HttpSession session) {
        if (session == null) return false;
        Object val = session.getAttribute(SESSION_KEY_CAPTCHA);
        return Boolean.TRUE.equals(val);
    }
}