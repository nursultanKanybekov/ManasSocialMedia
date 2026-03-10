package com.com.manasuniversityecosystem.web.dto.auth;


import com.com.manasuniversityecosystem.domain.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class JwtResponse {

    private String token;
    private String type;
    private UUID userId;
    private String email;
    private String fullName;
    private UserRole role;

    public static JwtResponse of(String token, UUID id, String email,
                                 String fullName, UserRole role) {
        return JwtResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(id)
                .email(email)
                .fullName(fullName)
                .role(role)
                .build();
    }
}
