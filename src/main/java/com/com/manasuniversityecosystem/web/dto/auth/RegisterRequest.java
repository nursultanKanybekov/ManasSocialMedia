package com.com.manasuniversityecosystem.web.dto.auth;


import com.com.manasuniversityecosystem.domain.enums.UserRole;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.UUID;

@Data
public class RegisterRequest {

    @NotBlank(message = "{validation.firstname.required}")
    @Size(min = 2, max = 100)
    private String firstName;

    @NotBlank(message = "{validation.lastname.required}")
    @Size(min = 2, max = 100)
    private String lastName;

    /** Derived from firstName + lastName */
    public String getFullName() {
        String fn = firstName != null ? firstName.trim() : "";
        String ln = lastName  != null ? lastName.trim()  : "";
        return (fn + " " + ln).trim();
    }

    /** Kept for backwards-compatibility with templates that bind fullName directly */
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

    /** MALE / FEMALE / OTHER */
    @NotBlank(message = "{validation.gender.required}")
    private String gender;

    // Optional — only for STUDENT / MEZUN / TEACHER / FACULTY_ADMIN
    private String studentIdNumber;

    private Integer graduationYear;

    private UUID facultyId;

    // Optional — only for EMPLOYER
    private String companyName;

    /** Field of operation for EMPLOYER (e.g. Technology, Healthcare) */
    private String companyField;

    /** MEZUN: place of work */
    private String workPlace;
}
