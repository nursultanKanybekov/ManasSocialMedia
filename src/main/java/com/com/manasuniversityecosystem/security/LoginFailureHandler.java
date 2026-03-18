package com.com.manasuniversityecosystem.security;

import com.com.manasuniversityecosystem.service.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final LoginAttemptService loginAttemptService;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception)
            throws IOException {

        HttpSession session = request.getSession(true);
        loginAttemptService.loginFailed(session);

        int     attempts = loginAttemptService.getAttempts(session);
        boolean captcha  = loginAttemptService.isCaptchaRequired(session);

        log.info("Login failed — attempts={} captchaRequired={} session={}",
                attempts, captcha, session.getId());

        response.sendRedirect("/auth/login?error=true");
    }
}