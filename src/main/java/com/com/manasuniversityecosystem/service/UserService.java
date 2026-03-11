package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.SecretaryValidation;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.SecretaryValidationRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import com.com.manasuniversityecosystem.web.dto.auth.RegisterRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepo;
    private final FacultyRepository facultyRepo;
    private final SecretaryValidationRepository validationRepo;
    private final PasswordEncoder passwordEncoder;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public AppUser getById(UUID id) {
        return userRepo.findByIdWithProfile(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public AppUser getByEmail(String email) {
        return userRepo.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
    }

    @Transactional
    public AppUser register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());
        }
        if (!req.getPassword().equals(req.getConfirmPassword())) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        if (req.getRole() != UserRole.STUDENT && req.getRole() != UserRole.MEZUN
                && req.getRole() != UserRole.EMPLOYER) {
            throw new IllegalArgumentException("Invalid self-registration role.");
        }
        if (req.getRole() == UserRole.MEZUN && req.getGraduationYear() == null) {
            throw new IllegalArgumentException("MEZUN must provide graduation year.");
        }
        if (req.getRole() == UserRole.EMPLOYER
                && (req.getCompanyName() == null || req.getCompanyName().isBlank())) {
            throw new IllegalArgumentException("Employer must provide company name.");
        }

        Faculty faculty = null;
        if (req.getRole() != UserRole.EMPLOYER && req.getFacultyId() != null) {
            faculty = facultyRepo.findById(req.getFacultyId())
                    .orElseThrow(() -> new IllegalArgumentException("Faculty not found."));
        }

        AppUser user = AppUser.builder()
                .fullName(req.getFullName())
                .email(req.getEmail())
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .role(req.getRole())
                .status(UserStatus.PENDING)
                .faculty(faculty)
                .studentIdNumber(req.getRole() != UserRole.EMPLOYER ? req.getStudentIdNumber() : null)
                .graduationYear(req.getGraduationYear())
                .companyName(req.getCompanyName())
                .build();

        Profile profile = Profile.builder()
                .user(user)
                .totalPoints(0)
                .build();
        user.setProfile(profile);
        userRepo.save(user);

        SecretaryValidation validation = SecretaryValidation.builder()
                .user(user)
                .status(SecretaryValidation.ValidationStatus.PENDING)
                .submittedAt(LocalDateTime.now())
                .build();
        validationRepo.save(validation);

        log.info("New user registered: {} [{}] — awaiting secretary validation",
                user.getEmail(), user.getRole());
        notificationService.notifyRegistration(user.getId(), user.getFullName(), user.getRole().name());
        return user;
    }

    @Transactional
    public AppUser activate(UUID userId) {
        AppUser user = getById(userId);
        user.setStatus(UserStatus.ACTIVE);
        return userRepo.save(user);
    }

    @Transactional
    public AppUser suspend(UUID userId) {
        AppUser user = getById(userId);
        user.setStatus(UserStatus.SUSPENDED);
        AppUser saved = userRepo.save(user);
        notificationService.notifySuspended(saved.getId());
        return saved;
    }

    @Transactional
    public AppUser changeRole(UUID userId, UserRole newRole) {
        AppUser user = getById(userId);
        user.setRole(newRole);
        AppUser saved = userRepo.save(user);
        notificationService.notifyRoleChanged(saved.getId(), newRole.name());
        return saved;
    }

    /** Admin: delete a user and all their data (cascades via JPA) */
    @Transactional
    public void deleteUser(UUID userId) {
        AppUser user = getById(userId);
        userRepo.delete(user);
        log.info("User deleted by admin: {}", user.getEmail());
    }

    public void changePassword(AppUser user, String oldPassword, String newPassword) {
        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepo.save(user);
    }

    @Transactional(readOnly = true)
    public List<AppUser> getAllByRole(UserRole role) {
        return userRepo.findByRole(role);
    }

    @Transactional(readOnly = true)
    public List<AppUser> getAllByStatus(UserStatus status) {
        return userRepo.findByStatus(status);
    }
}