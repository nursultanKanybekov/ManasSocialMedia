package com.com.manasuniversityecosystem.config;

import com.com.manasuniversityecosystem.security.CaptchaLoginFilter;
import com.com.manasuniversityecosystem.security.JwtAuthFilter;
import com.com.manasuniversityecosystem.security.LoginFailureHandler;
import com.com.manasuniversityecosystem.security.LoginSuccessHandler;
import com.com.manasuniversityecosystem.security.UserDetailsServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter          jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final LoginSuccessHandler    loginSuccessHandler;
    private final LoginFailureHandler    loginFailureHandler;
    private final CaptchaLoginFilter     captchaLoginFilter;

    @Bean
    public TokenBasedRememberMeServices rememberMeServices() {
        TokenBasedRememberMeServices services = new TokenBasedRememberMeServices(
                "manas-remember-me-secret-key", userDetailsService);
        services.setParameter("remember-me");
        services.setTokenValiditySeconds(14 * 24 * 60 * 60); // 14 days
        return services;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers(
                        "/api/**", "/ws/**", "/quiz/**", "/games/create",
                        "/games/spectate/**", "/award-game-win", "/award-game-points",
                        "/admin/users/*/reset-password", "/auth/forgot-password",
                        "/notifications/**",
                        // Academic AJAX endpoints (JSON responses, no form redirect)
                        "/academic/courses/*/registration",
                        "/academic/grades/enter",
                        "/academic/assignments/submissions/*/grade",
                        "/academic/attendance/open",
                        "/academic/attendance/*/close",
                        "/academic/attendance/*/mark",
                        // AI feature AJAX endpoints — wildcard covers all current and future paths
                        "/ai/**",
                        // Alumni map AJAX endpoints
                        "/mezun/map/pin", "/mezun/map/pins"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(ctx -> ctx
                        .securityContextRepository(new HttpSessionSecurityContextRepository()))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                // Static & auth
                                "/auth/**", "/css/**", "/js/**", "/images/**",
                                "/fonts/**", "/favicon.ico", "/error", "/uploads/**",
                                "/.well-known/**",
                                // Swagger UI
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/swagger-ui/index.html",
                                // SpringDoc OpenAPI JSON/YAML
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/api-docs/**",
                                // Webjars (Swagger UI static assets)
                                "/webjars/**",
                                // Public API endpoints
                                "/api/v1/auth/login",
                                "/api/v1/auth/obis-login",
                                "/api/v1/auth/register"
                        ).permitAll()
                        .requestMatchers("/ws/**", "/university-verify").authenticated()
                        .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/analytics").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/analytics/faculty").hasAnyRole("ADMIN", "SUPER_ADMIN", "FACULTY_ADMIN")
                        .requestMatchers("/secretary/**").hasAnyRole("SECRETARY", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/admin/translations/**").hasAnyRole("SECRETARY", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers(
                                "/career/jobs/new", "/career/jobs/*/edit",
                                "/career/jobs/*/delete").hasAnyRole("EMPLOYER", "ADMIN", "MEZUN", "TEACHER", "FACULTY_ADMIN")
                        .requestMatchers("/career/mentorship/respond/**").hasAnyRole("MEZUN", "TEACHER", "ADMIN", "FACULTY_ADMIN")
                        .requestMatchers("/competitions/new").hasAnyRole("SECRETARY", "ADMIN", "MEZUN", "TEACHER", "EMPLOYER", "FACULTY_ADMIN")
                        .requestMatchers("/events/new").hasAnyRole("SECRETARY", "ADMIN", "MEZUN", "TEACHER", "EMPLOYER", "FACULTY_ADMIN")
                        .requestMatchers("/edu/new").hasAnyRole("MEZUN", "TEACHER", "ADMIN", "FACULTY_ADMIN")
                        .requestMatchers("/timetable/**").hasAnyRole("STUDENT", "TEACHER", "ADMIN", "SUPER_ADMIN", "SECRETARY")
                        .requestMatchers("/food/**").hasAnyRole("STUDENT", "TEACHER", "ADMIN", "SUPER_ADMIN", "SECRETARY")
                        .requestMatchers("/exams/import", "/exams/clear", "/exams/*/delete")
                        .hasAnyRole("SECRETARY", "ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/exams/**").authenticated()
                        .requestMatchers("/academic/courses/create").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/courses/*/registration").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN","SECRETARY")
                        .requestMatchers("/academic/courses/*/enroll").hasRole("STUDENT")
                        .requestMatchers("/academic/courses/*/drop").hasRole("STUDENT")
                        .requestMatchers("/academic/grades/enter").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/assignments/create").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/assignments/*/submit").hasRole("STUDENT")
                        .requestMatchers("/academic/assignments/submissions/*/grade").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/assignments/*/delete").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/attendance/open").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/attendance/*/close").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/attendance/*/mark").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/academic/attendance/my").hasRole("STUDENT")
                        .requestMatchers("/academic/**").authenticated()
                        .requestMatchers("/library/upload").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN","SECRETARY")
                        .requestMatchers("/library/*/delete").hasAnyRole("TEACHER","ADMIN","SUPER_ADMIN","SECRETARY")
                        .requestMatchers("/library/**").authenticated()
                        // AI features
                        .requestMatchers("/ai/study/**", "/ai/essay/**", "/ai/flashcards/**",
                                "/ai/faq/**", "/ai/career-advisor/**").authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/auth/login")
                        .loginProcessingUrl("/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .defaultSuccessUrl("/main", true)
                        .failureHandler(loginFailureHandler)
                        .successHandler(loginSuccessHandler)
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .rememberMeServices(rememberMeServices())
                )
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .deleteCookies("manas_token", "JSESSIONID", "remember-me")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )
                .addFilterBefore(captchaLoginFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider p = new DaoAuthenticationProvider();
        p.setUserDetailsService(userDetailsService);
        p.setPasswordEncoder(passwordEncoder());
        return p;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration cfg)
            throws Exception {
        return cfg.getAuthenticationManager();
    }
}