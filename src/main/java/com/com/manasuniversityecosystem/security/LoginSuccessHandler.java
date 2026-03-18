package com.com.manasuniversityecosystem.security;

import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.service.LoginAttemptService;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final GamificationService gamificationService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication)
            throws IOException, ServletException {

        // Clear brute-force counters on successful login
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(LoginAttemptService.SESSION_KEY_ATTEMPTS);
            session.removeAttribute(LoginAttemptService.SESSION_KEY_CAPTCHA);
        }

        // Award daily login points
        String username = authentication.getName();
        userRepository.findByEmail(username).ifPresent(user ->
                gamificationService.awardPoints(user, PointReason.LOGIN, null)
        );

        boolean isSuperAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SUPER_ADMIN"));

        setDefaultTargetUrl(isSuperAdmin ? "/super-admin" : "/main");
        super.onAuthenticationSuccess(request, response, authentication);
    }
}