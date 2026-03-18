package com.com.manasuniversityecosystem.security;

import com.com.manasuniversityecosystem.service.LoginAttemptService;
import com.com.manasuniversityecosystem.service.RecaptchaService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Intercepts POST /auth/login BEFORE Spring Security.
 * Blocks submission if session requires CAPTCHA and token is missing/invalid.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CaptchaLoginFilter extends OncePerRequestFilter {

    private final RecaptchaService    recaptchaService;
    private final LoginAttemptService loginAttemptService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        if (!"POST".equalsIgnoreCase(request.getMethod())
                || !"/auth/login".equals(request.getServletPath())) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);

        if (session != null && loginAttemptService.isCaptchaRequired(session)) {
            String token = request.getParameter("g-recaptcha-response");
            log.info("CAPTCHA required for session={}, token present={}",
                    session.getId(), token != null && !token.isBlank());

            if (!recaptchaService.verify(token)) {
                log.warn("CAPTCHA verification failed for session={}", session.getId());
                response.sendRedirect("/auth/login?error=true&captchaError=true");
                return;
            }
            log.info("CAPTCHA passed for session={}", session.getId());
        }

        chain.doFilter(request, response);
    }
}