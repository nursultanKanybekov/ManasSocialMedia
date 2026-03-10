package com.com.manasuniversityecosystem.service;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.SecretaryValidation;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.SecretaryValidationRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecretaryService {

    private final SecretaryValidationRepository validationRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<SecretaryValidation> getPendingValidations() {
        return validationRepo.findPendingWithUsers(SecretaryValidation.ValidationStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public long countPending() {
        return validationRepo.countByStatus(SecretaryValidation.ValidationStatus.PENDING);
    }

    @Transactional
    public void approve(UUID validationId, AppUser secretary) {
        SecretaryValidation val = findById(validationId);
        if (val.getStatus() != SecretaryValidation.ValidationStatus.PENDING) {
            throw new IllegalStateException("Validation is not in PENDING state.");
        }
        val.setStatus(SecretaryValidation.ValidationStatus.APPROVED);
        val.setReviewedBy(secretary);
        val.setReviewedAt(LocalDateTime.now());
        validationRepo.save(val);

        AppUser user = val.getUser();
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);

        notificationService.notifyApproved(user.getId(), secretary.getId());

        log.info("Secretary {} approved user: {} [{}]",
                secretary.getEmail(), user.getEmail(), user.getRole());
    }

    @Transactional
    public void reject(UUID validationId, AppUser secretary, String reason) {
        SecretaryValidation val = findById(validationId);
        if (val.getStatus() != SecretaryValidation.ValidationStatus.PENDING) {
            throw new IllegalStateException("Validation is not in PENDING state.");
        }
        val.setStatus(SecretaryValidation.ValidationStatus.REJECTED);
        val.setReviewedBy(secretary);
        val.setNotes(reason);
        val.setReviewedAt(LocalDateTime.now());
        validationRepo.save(val);

        AppUser user = val.getUser();
        user.setStatus(UserStatus.SUSPENDED);
        userRepo.save(user);

        notificationService.notifyRejected(user.getId(), secretary.getId(), reason);

        log.warn("Secretary {} rejected user: {} — Reason: {}",
                secretary.getEmail(), user.getEmail(), reason);
    }

    @Transactional(readOnly = true)
    public SecretaryValidation getValidationForUser(UUID userId) {
        return validationRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No validation record for user: " + userId));
    }

    private SecretaryValidation findById(UUID id) {
        return validationRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Validation record not found: " + id));
    }
}