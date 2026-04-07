package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import com.com.manasuniversityecosystem.web.dto.auth.ObisStudentInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * GraduationDetectionService
 *
 * Automatically identifies students who have completed their programme and
 * promotes them from STUDENT → MEZUN (graduate) role.
 *
 * Two trigger points:
 *
 *  1. On every OBIS login — {@link #checkAndPromote(AppUser, ObisStudentInfo)}
 *     Called immediately after the user authenticates. Uses the freshly-scraped
 *     programme duration from OBIS so the check is always based on real data.
 *
 *  2. Nightly scheduled job — {@link #runNightlyGraduationScan()}
 *     Runs at 02:00 every day and scans ALL active students whose
 *     admissionYear + DEFAULT_PROGRAMME_YEARS < currentAcademicYear.
 *     This catches students who haven't logged in since they graduated.
 *
 * Graduation logic:
 *   Academic year starts in September. A student admitted in year A
 *   studying a 4-year programme graduates after academic year (A+4).
 *   They should be promoted once we enter academic year A+4+1, i.e.:
 *     currentAcademicYear > admissionYear + programmeYears
 *
 *   Example:
 *     Admitted 2020, 4-year programme → graduates after 2023/24 → promoted Sep 2024
 *     currentAcademicYear = (month >= 9) ? year : year - 1
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GraduationDetectionService {

    /**
     * Default programme duration used by the nightly scan.
     * Most bachelor programmes at Manas are 4 years.
     * Students on master/PhD programmes get the correct value from OBIS
     * on their next login via {@link #checkAndPromote}.
     */
    private static final int DEFAULT_PROGRAMME_YEARS = 4;

    private final UserRepository      userRepo;
    private final NotificationService notificationService;

    // ── 1. Login-time check ───────────────────────────────────────────────────

    /**
     * Called on every login (OBIS and regular).
     * - OBIS login: passes freshly-scraped {@link ObisStudentInfo} with real programmeYears
     * - Regular login: passes {@code null} — falls back to admissionYear stored on user
     *   and the default 4-year programme duration.
     * No-op if already MEZUN, suspended, or admissionYear is unknown.
     */
    @Transactional
    public void checkAndPromote(AppUser user, ObisStudentInfo info) {
        if (user.getRole() != UserRole.STUDENT) return;
        if (user.getStatus() != UserStatus.ACTIVE)  return;

        Integer admissionYear = (info != null && info.getAdmissionYear() != null)
                ? info.getAdmissionYear() : user.getAdmissionYear();
        int programmeYears = (info != null && info.getProgrammeYears() != null)
                ? info.getProgrammeYears() : DEFAULT_PROGRAMME_YEARS;

        if (admissionYear == null) {
            log.debug("GradDetect: skipping check for {} — admissionYear unknown", user.getEmail());
            return;
        }

        int currentAcademicYear = currentAcademicYear();
        boolean graduated = currentAcademicYear > admissionYear + programmeYears;

        log.debug("GradDetect [login]: user={} admYear={} progYears={} acadYear={} → graduated={}",
                user.getEmail(), admissionYear, programmeYears, currentAcademicYear, graduated);

        if (graduated) {
            promoteToMezun(user, admissionYear + programmeYears);
        }
    }

    // ── 2. Nightly scan ───────────────────────────────────────────────────────

    /**
     * Runs every day at 02:00 server time.
     * Finds all ACTIVE STUDENTs whose admissionYear is set and who are
     * past their expected graduation year using the default programme length.
     *
     * Students on longer programmes (master/PhD) will be corrected on their
     * next login via the login-time check which uses the OBIS-scraped value.
     */
    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void runNightlyGraduationScan() {
        int currentAcademicYear = currentAcademicYear();

        log.info("GradDetect [nightly]: starting scan, academicYear={}", currentAcademicYear);

        List<AppUser> candidates = userRepo.findStudentsToGraduate(
                DEFAULT_PROGRAMME_YEARS, currentAcademicYear);

        if (candidates.isEmpty()) {
            log.info("GradDetect [nightly]: no candidates found.");
            return;
        }

        log.info("GradDetect [nightly]: {} candidate(s) to promote", candidates.size());

        int promoted = 0;
        for (AppUser student : candidates) {
            try {
                int expectedGradYear = student.getAdmissionYear() + DEFAULT_PROGRAMME_YEARS;
                promoteToMezun(student, expectedGradYear);
                promoted++;
            } catch (Exception e) {
                log.error("GradDetect [nightly]: failed to promote userId={}: {}",
                        student.getId(), e.getMessage(), e);
            }
        }

        log.info("GradDetect [nightly]: promoted {} student(s) to MEZUN", promoted);
    }

    // ── Core promotion logic ──────────────────────────────────────────────────

    /**
     * Promotes a single student to MEZUN.
     *
     * Sets:
     *  - role         → MEZUN
     *  - graduationYear → the academic year they graduated in
     *  - graduationDetectedAt → now (audit timestamp)
     *
     * Sends the user an in-app notification informing them of the role change.
     * Notifies superadmins so they are aware.
     */
    @Transactional
    public void promoteToMezun(AppUser user, int graduationYear) {
        if (user.getRole() == UserRole.MEZUN) {
            log.debug("GradDetect: {} is already MEZUN — skipping", user.getEmail());
            return;
        }

        log.info("GradDetect: promoting {} (admYear={}) to MEZUN, gradYear={}",
                user.getEmail(), user.getAdmissionYear(), graduationYear);

        user.setRole(UserRole.MEZUN);
        user.setGraduationYear(graduationYear);
        user.setGraduationDetectedAt(LocalDateTime.now());
        userRepo.save(user);

        // Notify the graduate themselves
        notificationService.notifyGraduationDetected(user.getId(), graduationYear);

        // Notify superadmins (non-blocking — best effort)
        try {
            notificationService.notifySuperAdminsGraduationDetected(
                    user.getFullName(), user.getEmail(), graduationYear);
        } catch (Exception e) {
            log.warn("GradDetect: superadmin notification failed for {}: {}", user.getEmail(), e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Returns the current academic year start.
     * Academic year runs Sep–Aug, so:
     *   April 2026 → academic year 2025 (started Sep 2025)
     *   October 2026 → academic year 2026 (started Sep 2026)
     */
    private int currentAcademicYear() {
        LocalDate today = LocalDate.now();
        return today.getMonthValue() >= 9 ? today.getYear() : today.getYear() - 1;
    }
}