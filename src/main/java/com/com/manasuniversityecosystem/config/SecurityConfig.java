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
import org.springframework.security.web.authentication.rememberme.TokenBasedRememberMeServices.RememberMeTokenAlgorithm;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsServiceImpl userDetailsService;
    private final LoginSuccessHandler loginSuccessHandler;
    private final LoginFailureHandler loginFailureHandler;
    private final CaptchaLoginFilter captchaLoginFilter;

    /** Secret key for hash-based remember-me tokens */
    static final String REMEMBER_ME_KEY = "manas-mezun-rmb-secret-2025!";
    /** 3 days in seconds */
    static final int    REMEMBER_ME_TTL = 3 * 24 * 60 * 60;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**", "/ws/**", "/quiz/**", "/games/create", "/games/spectate/**", "/award-game-win", "/award-game-points", "/admin/users/*/reset-password", "/auth/forgot-password", "/notifications/**"))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(ctx -> ctx
                        .securityContextRepository(new HttpSessionSecurityContextRepository()))
                .authenticationProvider(authenticationProvider())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/**","/css/**","/js/**","/images/**",
                                "/fonts/**","/favicon.ico","/error","/uploads/**",
                                "/.well-known/**").permitAll()
                        .requestMatchers("/ws/**", "/university-verify").authenticated()
                        .requestMatchers("/super-admin/**").hasRole("SUPER_ADMIN")
                        .requestMatchers("/secretary/**").hasAnyRole("SECRETARY","ADMIN","SUPER_ADMIN")
                        .requestMatchers("/admin/**").hasAnyRole("ADMIN","SUPER_ADMIN")
                        .requestMatchers("/career/jobs/new","/career/jobs/*/edit",
                                "/career/jobs/*/delete").hasAnyRole("EMPLOYER","ADMIN","MEZUN")
                        .requestMatchers("/career/mentorship/respond/**").hasAnyRole("MEZUN","ADMIN")
                        .requestMatchers("/competitions/new").hasAnyRole("SECRETARY","ADMIN","MEZUN")
                        .requestMatchers("/events/new").hasAnyRole("SECRETARY","ADMIN","MEZUN")
                        .requestMatchers("/edu/new").hasAnyRole("MEZUN","ADMIN")
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
                .logout(logout -> logout
                        .logoutUrl("/auth/logout")
                        .logoutSuccessUrl("/auth/login?logout=true")
                        .deleteCookies("manas_token","JSESSIONID","remember-me")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .permitAll()
                )
                .rememberMe(rm -> rm
                        .rememberMeServices(rememberMeServices())
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

    /**
     * Hash-based remember-me: stores a signed token in cookie "remember-me".
     * Token = md5(username + ":" + expiry + ":" + passwordHash + ":" + key)
     * 3-day TTL — cookie survives browser close.
     */
    @Bean
    public TokenBasedRememberMeServices rememberMeServices() {
        TokenBasedRememberMeServices svc = new TokenBasedRememberMeServices(
                REMEMBER_ME_KEY, userDetailsService, RememberMeTokenAlgorithm.SHA256);
        svc.setTokenValiditySeconds(REMEMBER_ME_TTL);
        svc.setAlwaysRemember(false);           // only when checkbox is ticked
        svc.setParameter("remember-me");         // matches form field name
        svc.setCookieName("remember-me");
        return svc;
    }
}