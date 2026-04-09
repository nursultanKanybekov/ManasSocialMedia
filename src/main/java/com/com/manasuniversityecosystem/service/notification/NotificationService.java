package com.com.manasuniversityecosystem.service.notification;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.notification.Notification;
import com.com.manasuniversityecosystem.domain.entity.notification.Notification.NotifType;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.service.chat.ChatService;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final UserRepository         userRepo;
    private final SimpMessagingTemplate  messagingTemplate;
    private final ChatService             chatService;

    // ── Read ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Notification> getForUser(UUID userId, int page) {
        return notifRepo.findByRecipientId(userId, PageRequest.of(page, 20));
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnread(UUID userId) {
        return notifRepo.findUnreadByRecipientId(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return notifRepo.countByRecipientIdAndIsReadFalse(userId);
    }

    // ── Mark read ─────────────────────────────────────────────

    /** Mark a single notification read — validates it belongs to userId */
    @Transactional
    public void markOneRead(UUID notifId, UUID userId) {
        notifRepo.findById(notifId).ifPresent(n -> {
            if (n.getRecipient().getId().equals(userId)) {
                n.setIsRead(true);
                notifRepo.save(n);
            }
        });
    }

    /** Mark all notifications read for a user */
    @Transactional
    public void markAllRead(UUID userId) {
        notifRepo.markAllReadForUser(userId);
    }

    /** Delete all already-read notifications for a user */
    @Transactional
    public void clearRead(UUID userId) {
        notifRepo.deleteReadForUser(userId);
    }

    // ── Account lifecycle ─────────────────────────────────────

    @Async @Transactional
    public void notifyRegistration(UUID newUserId, String userName, String role) {
        String msg = "New user registered: " + userName + " (" + role + ")";
        userRepo.findByRole(UserRole.ADMIN).forEach(a ->
                saveNotification(a.getId(), newUserId, NotifType.ACCOUNT_REGISTERED, msg, "/secretary", "👤"));
        userRepo.findByRole(UserRole.SECRETARY).forEach(s ->
                saveNotification(s.getId(), newUserId, NotifType.ACCOUNT_REGISTERED, msg, "/secretary", "👤"));
        userRepo.findByRole(UserRole.SUPER_ADMIN).forEach(sa ->
                saveNotification(sa.getId(), newUserId, NotifType.ACCOUNT_REGISTERED, msg, "/secretary", "👤"));
    }

    @Async @Transactional
    public void notifyApproved(UUID userId, UUID secretaryId) {
        saveNotification(userId, secretaryId, NotifType.ACCOUNT_APPROVED,
                "Your account has been approved! Welcome to ManasMezun.", "/main", "✅");
    }

    @Async @Transactional
    public void notifyRejected(UUID userId, UUID secretaryId, String reason) {
        String msg = "Your account registration was rejected." + (reason != null ? " Reason: " + reason : "");
        saveNotification(userId, secretaryId, NotifType.ACCOUNT_REJECTED, msg, null, "❌");
    }

    @Async @Transactional
    public void notifySuspended(UUID userId) {
        saveNotification(userId, null, NotifType.ACCOUNT_SUSPENDED,
                "Your account has been suspended. Contact an administrator.", null, "⛔");
    }

    @Async @Transactional
    public void notifyRoleChanged(UUID userId, String newRole) {
        saveNotification(userId, null, NotifType.ROLE_CHANGED,
                "Your role has been updated to: " + newRole, "/profile", "🔄");
    }

    // ── Social ────────────────────────────────────────────────

    @Async @Transactional
    public void notifyLike(UUID postAuthorId, UUID likerId, String likerName) {
        saveNotification(postAuthorId, likerId, NotifType.POST_LIKED,
                likerName + " liked your post.", "/feed", "❤️");
    }

    @Async @Transactional
    public void notifyComment(UUID postAuthorId, UUID commenterId, String commenterName) {
        saveNotification(postAuthorId, commenterId, NotifType.POST_COMMENTED,
                commenterName + " commented on your post.", "/feed", "💬");
    }

    // ── Career ────────────────────────────────────────────────

    /** Called as: notifyNewJob(poster.getId(), job.getId(), titleString) */
    @Async @Transactional
    public void notifyNewJob(UUID posterId, UUID jobId, String jobTitle) {
        String msg  = "New job posted: " + jobTitle;
        String link = "/career/jobs/" + jobId;
        userRepo.findByRole(UserRole.STUDENT).forEach(u ->
                saveNotification(u.getId(), posterId, NotifType.JOB_POSTED, msg, link, "💼"));
        userRepo.findByRole(UserRole.MEZUN).forEach(u ->
                saveNotification(u.getId(), posterId, NotifType.JOB_POSTED, msg, link, "💼"));
    }

    /**
     * Notifies FACULTY_ADMIN users of a specific faculty about a new targeted job posting.
     */
    @Async @Transactional
    public void notifyFacultyAdminsNewJob(UUID posterId, UUID jobId, String jobTitle, UUID facultyId) {
        String msg  = "📢 New job posted targeting your faculty: " + jobTitle;
        String link = "/career/jobs/" + jobId;
        userRepo.findByRoleAndFacultyId(UserRole.FACULTY_ADMIN, facultyId).forEach(fa ->
                saveNotification(fa.getId(), posterId, NotifType.JOB_POSTED, msg, link, "🏛️"));
        log.info("[Career] Faculty admins of faculty {} notified about job {}", facultyId, jobId);
    }

    /** Called as: notifyJobApplied(employerId, applicantId, jobId, jobTitle, applicantName) */
    @Async @Transactional
    public void notifyJobApplied(UUID employerId, UUID applicantId,
                                 UUID jobId, String jobTitle, String applicantName) {
        String link = jobId != null ? "/career/jobs/" + jobId : "/career/jobs";
        saveNotification(employerId, applicantId, NotifType.JOB_APPLIED,
                applicantName + " applied for: " + jobTitle, link, "📋");
    }

    /** Called as: notifyApplicationStatus(applicantId, jobTitle, statusName) */
    @Async @Transactional
    public void notifyApplicationStatus(UUID applicantId, String jobTitle, String status) {
        saveNotification(applicantId, null, NotifType.APPLICATION_STATUS,
                "Your application for '" + jobTitle + "' is now: " + status, "/career/jobs", "📊");
    }

    // ── Mentorship ────────────────────────────────────────────

    /** Called as: notifyMentorshipRequested(mentorId, studentId, studentName) */
    @Async @Transactional
    public void notifyMentorshipRequested(UUID mentorId, UUID studentId, String studentName) {
        saveNotification(mentorId, studentId, NotifType.MENTORSHIP_REQUESTED,
                studentName + " requested mentorship from you.", "/career/mentorship", "🤝");
    }

    /** Called as: notifyMentorshipResponse(studentId, mentorId, mentorName, accept) */
    @Async @Transactional
    public void notifyMentorshipResponse(UUID studentId, UUID mentorId,
                                         String mentorName, boolean accepted) {
        NotifType type = accepted ? NotifType.MENTORSHIP_ACCEPTED : NotifType.MENTORSHIP_DECLINED;
        String icon    = accepted ? "✅" : "❌";
        String status  = accepted ? "accepted" : "declined";
        saveNotification(studentId, mentorId, type,
                mentorName + " has " + status + " your mentorship request.", "/career/mentorship", icon);
    }

    /** Called as: notifyMentorshipCompleted(mentorId, mentorName, studentId, studentName) */
    @Async @Transactional
    public void notifyMentorshipCompleted(UUID mentorId, String mentorName,
                                          UUID studentId, String studentName) {
        saveNotification(studentId, mentorId, NotifType.MENTORSHIP_COMPLETED,
                "Your mentorship session with " + mentorName + " has been completed.", "/career/mentorship", "🎓");
        saveNotification(mentorId, studentId, NotifType.MENTORSHIP_COMPLETED,
                "Mentorship session with " + studentName + " completed.", "/career/mentorship", "🎓");
    }

    // ── Password Reset Request ─────────────────────────────────

    @Async @Transactional
    public void notifyPasswordResetRequest(UUID requestingUserId, String userName, String userEmail) {
        String notifMsg = "Password reset requested by: " + userName + " (" + userEmail + "). Go to Admin Users to reset.";
        String chatMsg  = "🔑 Password Reset Request\n\n" +
                "User: " + userName + "\n" +
                "Email: " + userEmail + "\n\n" +
                "Please go to Admin → Users, find this user and reset their password.";
        String link = "/admin/users";
        String icon = "🔑";

        AppUser requester = userRepo.findById(requestingUserId).orElse(null);

        // Collect all staff who should be notified
        java.util.List<AppUser> staff = new java.util.ArrayList<>();
        staff.addAll(userRepo.findByRole(UserRole.ADMIN));
        staff.addAll(userRepo.findByRole(UserRole.SECRETARY));
        staff.addAll(userRepo.findByRole(UserRole.SUPER_ADMIN));

        for (AppUser staffMember : staff) {
            // Bell notification
            saveNotification(staffMember.getId(), requestingUserId,
                    NotifType.PASSWORD_RESET_REQUEST, notifMsg, link, icon);

            // Direct chat message — from the requesting user to each staff member
            if (requester != null) {
                try {
                    var room = chatService.getOrCreateDirectRoom(requester, staffMember);
                    chatService.sendMessage(requester, room.getId(), chatMsg, "TEXT");
                } catch (Exception e) {
                    log.warn("[Auth] Could not send chat message to {}: {}", staffMember.getEmail(), e.getMessage());
                }
            }
        }
        log.info("[Auth] Password reset notification + chat sent for user={}", userEmail);
    }

    // ── Faculty detection ─────────────────────────────────────────────────────

    /**
     * Fired when OBIS returns a faculty name not found in the DB.
     * Notifies all SUPER_ADMINs with a link to /super-admin dashboard.
     */
    @Async @Transactional
    public void notifyNewFacultyDetected(String facultyName, String studentName) {
        String msg  = "\uD83C\uDFDB New faculty detected from OBIS: \"" + facultyName + "\" "
                + "(triggered by student: " + studentName + "). "
                + "Please review and add it to the system.";
        String link = "/super-admin";
        String icon = "\uD83C\uDFDB";

        userRepo.findByRole(UserRole.SUPER_ADMIN).forEach(sa ->
                saveNotification(sa.getId(), null,
                        NotifType.NEW_FACULTY_DETECTED, msg, link, icon));

        log.info("[Faculty] New faculty detected from OBIS: '{}' — superadmins notified.", facultyName);
    }

    // ── Graduation ────────────────────────────────────────────

    /**
     * Sent to the student when they are auto-promoted to MEZUN.
     */
    @Async @Transactional
    public void notifyGraduationDetected(UUID userId, int graduationYear) {
        String msg = "🎓 Congratulations! You have been recognized as a graduate (Mezun) "
                + "of the class of " + graduationYear + ". "
                + "Your role has been updated from Student to Mezun.";
        saveNotification(userId, null, NotifType.GRADUATION_DETECTED, msg, "/profile", "🎓");
    }

    /**
     * Sent to all SUPER_ADMINs when a student is auto-promoted to MEZUN.
     */
    @Async @Transactional
    public void notifySuperAdminsGraduationDetected(String fullName, String email, int graduationYear) {
        String msg = "🎓 Student auto-promoted to Mezun: " + fullName
                + " (" + email + "), graduation year " + graduationYear + ".";
        userRepo.findByRole(UserRole.SUPER_ADMIN).forEach(sa ->
                saveNotification(sa.getId(), null,
                        NotifType.GRADUATION_DETECTED, msg, "/super-admin/users", "🎓"));
        log.info("[GradDetect] Superadmins notified: {} promoted to Mezun ({})", fullName, graduationYear);
    }

    // ── System / Badge ────────────────────────────────────────

    @Async @Transactional
    public void notifyBadgeEarned(UUID userId, String badgeName) {
        saveNotification(userId, null, NotifType.BADGE_EARNED,
                "You earned a new badge: " + badgeName + "!", "/gamification", "🏅");
    }

    @Async @Transactional
    public void notifySystem(UUID userId, String message, String link) {
        saveNotification(userId, null, NotifType.SYSTEM, message, link, "🔔");
    }

    // ── Core save + WebSocket push ────────────────────────────

    @Transactional
    protected void saveNotification(UUID recipientId, UUID actorId,
                                    NotifType type, String message, String link, String icon) {
        AppUser recipient = userRepo.findById(recipientId).orElse(null);
        if (recipient == null) return;
        AppUser actor = (actorId != null) ? userRepo.findById(actorId).orElse(null) : null;

        Notification n = Notification.builder()
                .recipient(recipient).actor(actor).type(type)
                .message(message).link(link).icon(icon != null ? icon : "🔔")
                .build();
        notifRepo.save(n);

        try {
            messagingTemplate.convertAndSendToUser(
                    recipientId.toString(), "/queue/notifications",
                    Map.of("type", "NEW_NOTIFICATION", "message", message,
                            "icon", icon != null ? icon : "🔔", "link", link != null ? link : "")
            );
        } catch (Exception e) {
            log.debug("WS push failed for {}: {}", recipientId, e.getMessage());
        }
    }
}