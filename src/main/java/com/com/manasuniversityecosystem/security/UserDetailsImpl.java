package com.com.manasuniversityecosystem.security;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class UserDetailsImpl implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final UserRole role;
    private final String fullName;
    private final String avatarUrl;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserDetailsImpl build(AppUser user) {
        return UserDetailsImpl.builder()
                .id(user.getId())
                .email(user.getEmail())
                .password(user.getPasswordHash())
                .role(user.getRole())
                .fullName(user.getFullName())
                .avatarUrl(user.getProfile() != null ? user.getProfile().getAvatarUrl() : null)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())))
                .build();
    }

    @Override public String getUsername()                                      { return email; }
    @Override public String getPassword()                                      { return password; }
    @Override public Collection<? extends GrantedAuthority> getAuthorities()  { return authorities; }
    @Override public boolean isAccountNonExpired()                             { return true; }
    @Override public boolean isAccountNonLocked()                              { return true; }
    @Override public boolean isCredentialsNonExpired()                         { return true; }
    @Override public boolean isEnabled()                                       { return true; }
}