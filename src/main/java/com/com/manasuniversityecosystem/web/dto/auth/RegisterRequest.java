package com.com.manasuniversityecosystem.web.dto.auth;


import com.com.manasuniversityecosystem.domain.enums.UserRole;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterRequest {

    @NotBlank(message = "{validation.fullname.required}")
    @Size(min = 2, max = 100)
    private String fullName;

    @NotBlank(message = "{validation.email.required}")
    @Email(message = "{validation.email.invalid}")
    private String email;

    @NotBlank(message = "{validation.password.required}")
    @Size(min = 8, message = "{validation.password.min}")
    private String password;

    @NotBlank(message = "{validation.confirm.required}")
    private String confirmPassword;

    @NotNull(message = "{validation.role.required}")
    private UserRole role;

    @NotBlank(message = "{validation.studentid.required}")
    private String studentIdNumber;

    private Integer graduationYear;

    @NotNull(message = "{validation.faculty.required}")
    private UUID facultyId;
}