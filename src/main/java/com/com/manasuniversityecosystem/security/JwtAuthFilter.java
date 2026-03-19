package com.com.manasuniversityecosystem.security;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtTokenProvider      tokenProvider;
    private final UserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        try {
            tokenProvider.resolveToken(req).ifPresent(token -> {
                if (tokenProvider.validateToken(token)) {
                    String email = tokenProvider.getEmailFromToken(token);
                    UserDetails ud = userDetailsService.loadUserByUsername(email);
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    ud, null, ud.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            });
        } catch (Exception e) {
            log.error("Auth filter error: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(req, res);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String p = req.getServletPath();
        return p.startsWith("/auth/")
                || p.startsWith("/css/")
                || p.startsWith("/js/")
                || p.startsWith("/images/")
                || p.startsWith("/fonts/")
                || p.equals("/favicon.ico")
                // Swagger / OpenAPI — skip JWT filter so docs load without a token
                || p.startsWith("/swagger-ui")
                || p.startsWith("/v3/api-docs")
                || p.startsWith("/api-docs")
                || p.startsWith("/webjars/");
    }
}