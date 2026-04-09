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
import com.com.manasuniversityecosystem.web.dto.auth.MabisTeacherInfo;
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
 * MabisLoginService
 *
 * After MABIS authentication succeeds:
 *  1. Find user by MABIS email (stable identifier) or create new
 *  2. Set role = TEACHER — always authoritative from MABIS
 *  3. Set all fields from MABIS — name, employeeNumber, department
 *  4. Log user into Spring Security session programmatically
 *
 * NOTE: Teachers are NOT inserted into any alumni (mezun) table.
 *       They receive TEACHER role and are immediately ACTIVE.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MabisLoginService {

    private final UserRepository      userRepo;
    private final FacultyRepository   facultyRepo;
    private final NotificationService notificationService;

    @Transactional
    public AppUser loginOrRegister(MabisTeacherInfo info, HttpServletRequest request) {

        String email = resolveEmail(info);

        AppUser user = userRepo.findByEmail(email)
                .map(existing -> updateExisting(existing, info))
                .orElseGet(() -> createNew(email, info));

        programmaticLogin(user, request);

        log.info("MABIS login complete: id={} email={} name='{}' dept='{}'",
                user.getId(), email, user.getFullName(), info.getDepartmentName());
        return user;
    }

    // ── Create new teacher ─────────────────────────────────────────────────────

    private AppUser createNew(String email, MabisTeacherInfo info) {
        log.info("MABIS: creating new TEACHER user email={}", email);

        Faculty faculty = resolveFaculty(info);

        // Parse first/last name from fullName
        String fullName = info.getFullName() != null ? info.getFullName().trim() : "";
        String firstName = null, lastName = null;
        if (!fullName.isBlank()) {
            int idx = fullName.indexOf(' ');
            if (idx > 0) {
                firstName = fullName.substring(0, idx);
                lastName  = fullName.substring(idx + 1);
            } else {
                firstName = fullName;
            }
        }

        AppUser user = AppUser.builder()
                .email(email)
                .passwordHash("MABIS_AUTH_ONLY")   // no direct password login
                .fullName(fullName)
                .firstName(firstName)
                .lastName(lastName)
                .role(UserRole.TEACHER)              // always TEACHER from MABIS
                .status(UserStatus.ACTIVE)           // MABIS verified — no secretary queue
                .studentIdNumber(info.getEmployeeNumber())
                .universityVerified(true)
                .obisUsername(info.getMabisUsername())
                .faculty(faculty)
                .build();

        // Build profile
        Profile profile = new Profile();
        profile.setCanMentor(true); // Teachers can mentor by default
        if (!isBlank(info.getDepartmentName())) {
            profile.setHeadline("Teacher · " + info.getDepartmentName());
        } else {
            profile.setHeadline("Teacher · Manas University");
        }
        if (info.getAvatarUrl() != null) {
            profile.setAvatarUrl(info.getAvatarUrl());
        }

        user.setProfile(profile);
        return userRepo.save(user);
    }

    // ── Update existing teacher ────────────────────────────────────────────────

    private AppUser updateExisting(AppUser user, MabisTeacherInfo info) {
        log.info("MABIS: updating existing user id={}", user.getId());

        // Always upgrade to TEACHER if somehow set differently
        user.setRole(UserRole.TEACHER);
        user.setFullName(info.getFullName());
        user.setStudentIdNumber(info.getEmployeeNumber());
        user.setUniversityVerified(true);
        user.setObisUsername(info.getMabisUsername());

        // Re-activate if was pending/suspended
        if (user.getStatus() != UserStatus.ACTIVE) {
            user.setStatus(UserStatus.ACTIVE);
        }

        // Sync faculty
        Faculty faculty = resolveFaculty(info);
        if (faculty != null) {
            user.setFaculty(faculty);
        }

        // Update profile
        if (user.getProfile() != null) {
            if (!isBlank(info.getDepartmentName())) {
                user.getProfile().setHeadline("Teacher · " + info.getDepartmentName());
            }
            if (info.getAvatarUrl() != null) {
                user.getProfile().setAvatarUrl(info.getAvatarUrl());
            }
        }

        return userRepo.save(user);
    }

    // ── Resolve faculty from department name ───────────────────────────────────

    private Faculty resolveFaculty(MabisTeacherInfo info) {
        String rawName = info.getDepartmentName();
        if (isBlank(rawName)) return null;

        String trimmed    = rawName.trim();
        String normalized = trimmed.toLowerCase();

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
            log.debug("MABIS: dept '{}' matched to DB faculty '{}'", rawName, matched.getName());
            return matched;
        }

        // Auto-create faculty from MABIS data
        String code = generateFacultyCode(trimmed);
        if (facultyRepo.existsByCode(code)) {
            code = code + "_" + System.currentTimeMillis() % 1000;
        }

        Faculty created = Faculty.builder()
                .name(trimmed)
                .code(code)
                .build();
        created = facultyRepo.save(created);

        log.info("MABIS: auto-created new faculty '{}' (code='{}') from MABIS data",
                trimmed, code);

        notificationService.notifyNewFacultyDetected(trimmed, info.getFullName());
        return created;
    }

    private String generateFacultyCode(String name) {
        String[] words = name.trim().split("\\s+");
        StringBuilder code = new StringBuilder();
        for (String word : words) {
            if (word.length() > 2) {
                code.append(Character.toUpperCase(word.charAt(0)));
            }
        }
        String result = code.toString();
        if (result.isBlank()) {
            result = name.replaceAll("[^a-zA-ZА-Яа-яÇçĞğİıÖöŞşÜü]", "")
                    .toUpperCase();
            result = result.length() > 5 ? result.substring(0, 5) : result;
        }
        return result.isEmpty() ? "FAC" : result;
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
        log.debug("MABIS: session login set for userId={}", user.getId());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String resolveEmail(MabisTeacherInfo info) {
        if (!isBlank(info.getMabisEmail()) && info.getMabisEmail().contains("@")) {
            return info.getMabisEmail().toLowerCase();
        }
        if (!isBlank(info.getEmployeeNumber())) {
            return info.getEmployeeNumber().toLowerCase() + "@manas.edu.kg";
        }
        return info.getMabisUsername().toLowerCase() + "@mabis.manas.edu.kg";
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}