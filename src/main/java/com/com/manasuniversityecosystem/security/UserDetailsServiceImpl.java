package com.com.manasuniversityecosystem.security;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        AppUser user = userRepository.findByEmailWithProfile(email)
                .orElseThrow(() -> {
                    log.warn("Login attempt with unknown email: {}", email);
                    return new UsernameNotFoundException("User not found: " + email);
                });

        if (user.getStatus() == UserStatus.PENDING) {
            throw new DisabledException("Account is pending secretary validation.");
        }
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new DisabledException("Account has been suspended.");
        }

        return UserDetailsImpl.build(user);
    }
}
