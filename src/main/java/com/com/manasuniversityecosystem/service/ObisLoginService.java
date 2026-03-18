package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import com.com.manasuniversityecosystem.web.dto.auth.ObisStudentInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ObisLoginService
 *
 * After OBIS authentication succeeds:
 *  1. Find user by OBIS email (stable identifier) or create new
 *  2. Set all fields from OBIS — name, studentId, studyYear are AUTHORITATIVE
 *  3. Set avatar from OBIS photo URL
 *  4. Log user into Spring Security session programmatically
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ObisLoginService {

    private final UserRepository      userRepo;
    private final FacultyRepository   facultyRepo;
    private final NotificationService notificationService;

    @Transactional
    public AppUser loginOrRegister(ObisStudentInfo info, HttpServletRequest request) {

        // Use OBIS email as the stable unique identifier
        // e.g. "2335.09016@manas.edu.kg"
        String email = resolveEmail(info);

        AppUser user = userRepo.findByEmail(email)
                .map(existing -> updateExisting(existing, info))
                .orElseGet(() -> createNew(email, info));

        programmaticLogin(user, request);

        log.info("OBIS login complete: id={} email={} name='{}' year={}",
                user.getId(), email, user.getFullName(), info.getStudyYear());
        return user;
    }

    // ── Create new user ────────────────────────────────────────────────────────

    private AppUser createNew(String email, ObisStudentInfo info) {
        log.info("OBIS: creating new user email={}", email);

        Faculty faculty = resolveFaculty(info);

        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash("OBIS_AUTH_ONLY") // no direct password login
                .fullName(info.getFullName())
                .role(UserRole.STUDENT)
                .status(UserStatus.ACTIVE)       // OBIS verified — skip secretary queue
                .studentIdNumber(info.getStudentId())
                .universityVerified(true)
                .obisUsername(info.getObisUsername())
                .faculty(faculty)
                .build();

        // Build profile with OBIS data
        Profile profile = new Profile();
        profile.setCanMentor(false);
        buildHeadline(profile, info);

        // Set avatar from OBIS photo
        if (info.getAvatarUrl() != null) {
            profile.setAvatarUrl(info.getAvatarUrl());
        }

        user.setProfile(profile);
        return userRepo.save(user);
    }

    // ── Update existing user ───────────────────────────────────────────────────

    private AppUser updateExisting(AppUser user, ObisStudentInfo info) {
        log.info("OBIS: updating existing user id={}", user.getId());

        // Always sync authoritative fields from OBIS
        user.setFullName(info.getFullName());
        user.setStudentIdNumber(info.getStudentId());
        user.setUniversityVerified(true);
        user.setObisUsername(info.getObisUsername());

        // Re-activate if was pending
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        }

        // Sync faculty from OBIS on every login
        Faculty faculty = resolveFaculty(info);
        if (faculty != null) {
            user.setFaculty(faculty);
        }

        // Update profile
        if (user.getProfile() != null) {
            buildHeadline(user.getProfile(), info);
            // Update avatar if OBIS has one and profile doesn't have a custom one
            if (info.getAvatarUrl() != null) {
                user.getProfile().setAvatarUrl(info.getAvatarUrl());
            }
        }

        return userRepo.save(user);
    }

    // ── Resolve faculty from OBIS name ────────────────────────────────────────

    /**
     * Tries to match the OBIS faculty name to an existing Faculty in the DB.
     *
     * Matching strategy (case-insensitive, trimmed):
     *  1. Exact name match
     *  2. Name contains the DB faculty name (handles abbreviations)
     *  3. DB faculty name contains the OBIS name
     *
     * If no match is found:
     *  - Fires an async superadmin notification (non-blocking)
     *  - Returns null — student can still log in, just without a faculty set
     */
    private Faculty resolveFaculty(ObisStudentInfo info) {
        String rawName = info.getFacultyName();
        if (rawName == null || rawName.isBlank()) return null;

        String normalized = rawName.trim().toLowerCase();

        Faculty matched = facultyRepo.findAll().stream()
                .filter(f -> {
                    String dbName = f.getName().trim().toLowerCase();
                    return dbName.equals(normalized)
                            || normalized.contains(dbName)
                            || dbName.contains(normalized);
                })
                .findFirst()
                .orElse(null);

        if (matched != null) {
            log.debug("OBIS: faculty '{}' matched to DB faculty '{}'", rawName, matched.getName());
            return matched;
        }

        // No match — fire notification to superadmins (async, won't block login)
        log.warn("OBIS: unknown faculty '{}' — notifying superadmins", rawName);
        notificationService.notifyNewFacultyDetected(rawName, info.getFullName());
        return null;
    }

    // ── Build headline from study year ─────────────────────────────────────────

    private void buildHeadline(Profile profile, ObisStudentInfo info) {
        if (info.getStudyYear() != null) {
            // e.g. "2nd year student · Admitted 2023"
            String yearLabel = ordinal(info.getStudyYear()) + " year student";
            if (info.getAdmissionYear() != null) {
                yearLabel += " · Admitted " + info.getAdmissionYear();
            }
            profile.setHeadline(yearLabel);
        }
    }

    private String ordinal(int n) {
        return switch (n) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> n + "th";
        };
    }

    // ── Programmatic Spring Security login ────────────────────────────────────

    private void programmaticLogin(AppUser user, HttpServletRequest request) {
        UserDetailsImpl userDetails = UserDetailsImpl.build(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);

        HttpSession session = request.getSession(true);
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                context);
        log.debug("OBIS: session login set for userId={}", user.getId());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String resolveEmail(ObisStudentInfo info) {
        // Prefer the OBIS-assigned email if scraped
        if (info.getObisEmail() != null && info.getObisEmail().contains("@")) {
            return info.getObisEmail().toLowerCase();
        }
        // Fallback: studentId@manas.edu.kg
        if (info.getStudentId() != null && !info.getStudentId().isBlank()) {
            return info.getStudentId().toLowerCase() + "@manas.edu.kg";
        }
        return info.getObisUsername().toLowerCase() + "@obis.manas.edu.kg";
    }
}