package com.com.manasuniversityecosystem.service;


import com.com.manasuniversityecosystem.security.JwtTokenProvider;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.web.dto.auth.JwtResponse;
import com.com.manasuniversityecosystem.web.dto.auth.LoginRequest;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider tokenProvider;

    public JwtResponse login(LoginRequest req, HttpServletResponse response) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getEmail(), req.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
        UserDetailsImpl principal = (UserDetailsImpl) auth.getPrincipal();
        String token = tokenProvider.generateToken(auth);

        // Set JWT as HttpOnly cookie for Thymeleaf sessions
        Cookie cookie = new Cookie(tokenProvider.getCookieName(), token);
        cookie.setHttpOnly(true);
        cookie.setSecure(false); // set true in production with HTTPS
        cookie.setPath("/");
        cookie.setMaxAge((int) (tokenProvider.getExpirationMs() / 1000));
        response.addCookie(cookie);

        log.info("User logged in: {}", principal.getEmail());
        return JwtResponse.of(token, principal.getId(),
                principal.getEmail(), principal.getFullName(), principal.getRole());
    }

    public void logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(tokenProvider.getCookieName(), "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        SecurityContextHolder.clearContext();
    }
}