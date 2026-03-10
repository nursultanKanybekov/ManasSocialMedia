package com.com.manasuniversityecosystem.service.notification;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.notification.Notification;
import com.com.manasuniversityecosystem.domain.entity.notification.Notification.NotifType;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notifRepo;
    private final UserRepository userRepo;

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

    @Transactional
    public void markAllRead(UUID userId) {
        notifRepo.markAllReadForUser(userId);
    }

    @Transactional
    public void markOneRead(UUID notifId, UUID userId) {
        notifRepo.markOneRead(notifId, userId);
    }

    @Transactional
    public void clearRead(UUID userId) {
        notifRepo.deleteReadForUser(userId);
    }

    // ── Internal save — re-fetches by ID to avoid detached-entity FK issues ──

    @Transactional
    protected void saveNotification(UUID recipientId, UUID actorId,
                                    NotifType type, String message, String link, String icon) {
        AppUser recipient = userRepo.findById(recipientId).orElse(null);
        if (recipient == null) return;
        AppUser actor = (actorId != null) ? userRepo.findById(actorId).orElse(null) : null;

        Notification n = Notification.builder()
                .recipient(recipient)
                .actor(actor)
                .type(type)
                .message(message)
                .link(link)
                .icon(icon != null ? icon : "🔔")
                .isRead(false)
                .build();
        notifRepo.save(n);
        log.debug("Notification [{}] → {}: {}", type, recipient.getEmail(), message);
    }

    // ── Domain helpers — all accept IDs + strings, run @Async ─

    @Async
    @Transactional
    public void notifyRegistration(UUID newUserId, String fullName, String role) {
        String msg = "New registration: " + fullName + " (" + role + ")";
        userRepo.findByRole(UserRole.ADMIN).forEach(admin ->
                saveNotification(admin.getId(), newUserId, NotifType.ACCOUNT_REGISTERED, msg, "/secretary", "👤")
        );
        userRepo.findByRole(UserRole.SECRETARY).forEach(sec ->
                saveNotification(sec.getId(), newUserId, NotifType.ACCOUNT_REGISTERED, msg, "/secretary", "👤")
        );
    }

    @Async
    @Transactional
    public void notifyApproved(UUID userId, UUID secretaryId) {
        saveNotification(userId, secretaryId, NotifType.ACCOUNT_APPROVED,
                "Your account has been approved! You can now log in.", "/feed", "✅");
    }

    @Async
    @Transactional
    public void notifyRejected(UUID userId, UUID secretaryId, String reason) {
        String msg = "Your account was not approved" +
                (reason != null && !reason.isBlank() ? ": " + reason : ".");
        saveNotification(userId, secretaryId, NotifType.ACCOUNT_REJECTED, msg, null, "❌");
    }

    @Async
    @Transactional
    public void notifySuspended(UUID userId) {
        saveNotification(userId, null, NotifType.ACCOUNT_SUSPENDED,
                "Your account has been suspended. Contact admin for details.", null, "🚫");
    }

    @Async
    @Transactional
    public void notifyRoleChanged(UUID userId, String newRole) {
        saveNotification(userId, null, NotifType.ROLE_CHANGED,
                "Your role has been updated to " + newRole + ".",
                "/profile/" + userId, "🎭");
    }

    @Async
    @Transactional
    public void notifyJobApplied(UUID employerId, UUID applicantId,
                                 UUID jobId, String jobTitle, String applicantName) {
        saveNotification(employerId, applicantId, NotifType.JOB_APPLIED,
                applicantName + " applied for: " + jobTitle,
                "/career/jobs/" + jobId, "💼");
    }

    @Async
    @Transactional
    public void notifyApplicationStatus(UUID applicantId, String jobTitle, String newStatus) {
        String icon = switch (newStatus) {
            case "ACCEPTED" -> "🎉";
            case "REJECTED" -> "😞";
            default -> "📋";
        };
        saveNotification(applicantId, null, NotifType.APPLICATION_STATUS,
                "Your application for \"" + jobTitle + "\" is now: " + newStatus,
                "/career/my-applications", icon);
    }

    @Async
    @Transactional
    public void notifyNewJob(UUID posterId, UUID jobId, String jobTitle) {
        userRepo.findByRole(UserRole.STUDENT).forEach(student ->
                saveNotification(student.getId(), posterId, NotifType.JOB_POSTED,
                        "New job posted: " + jobTitle,
                        "/career/jobs/" + jobId, "💼")
        );
    }

    @Async
    @Transactional
    public void notifyMentorshipRequested(UUID mentorId, UUID studentId, String studentName) {
        saveNotification(mentorId, studentId, NotifType.MENTORSHIP_REQUESTED,
                studentName + " requested mentorship from you.",
                "/career/mentorship", "🤝");
    }

    @Async
    @Transactional
    public void notifyMentorshipResponse(UUID studentId, UUID mentorId,
                                         String mentorName, boolean accepted) {
        if (accepted) {
            saveNotification(studentId, mentorId, NotifType.MENTORSHIP_ACCEPTED,
                    mentorName + " accepted your mentorship request! 🎉",
                    "/career/mentorship", "✅");
        } else {
            saveNotification(studentId, mentorId, NotifType.MENTORSHIP_DECLINED,
                    mentorName + " declined your mentorship request.",
                    "/career/mentorship", "❌");
        }
    }

    @Async
    @Transactional
    public void notifyMentorshipCompleted(UUID mentorId, String mentorName,
                                          UUID studentId, String studentName) {
        saveNotification(mentorId, studentId, NotifType.MENTORSHIP_COMPLETED,
                "Mentorship with " + studentName + " is marked complete.",
                "/career/mentorship", "🏆");
        saveNotification(studentId, mentorId, NotifType.MENTORSHIP_COMPLETED,
                "Mentorship with " + mentorName + " is marked complete.",
                "/career/mentorship", "🏆");
    }

    @Async
    @Transactional
    public void notifyComment(UUID postAuthorId, UUID commenterId, String commenterName) {
        if (postAuthorId.equals(commenterId)) return;
        saveNotification(postAuthorId, commenterId, NotifType.POST_COMMENTED,
                commenterName + " commented on your post.", "/feed", "💬");
    }

    @Async
    @Transactional
    public void notifyLike(UUID postAuthorId, UUID likerId, String likerName) {
        if (postAuthorId.equals(likerId)) return;
        saveNotification(postAuthorId, likerId, NotifType.POST_LIKED,
                likerName + " liked your post.", "/feed", "❤️");
    }

    @Async
    @Transactional
    public void notifyBadgeEarned(UUID userId, String badgeName) {
        saveNotification(userId, null, NotifType.BADGE_EARNED,
                "You earned the badge: " + badgeName + "! 🏅",
                "/profile/" + userId, "🏅");
    }
}
