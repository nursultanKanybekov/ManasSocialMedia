package com.com.manasuniversityecosystem.domain.entity.notification;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification",
        indexes = {
                @Index(name = "idx_notif_recipient",  columnList = "recipient_id"),
                @Index(name = "idx_notif_is_read",    columnList = "is_read"),
                @Index(name = "idx_notif_created_at", columnList = "created_at")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Notification {

    public enum NotifType {
        // Account lifecycle
        ACCOUNT_REGISTERED,
        ACCOUNT_APPROVED,
        ACCOUNT_REJECTED,
        ACCOUNT_SUSPENDED,
        ROLE_CHANGED,

        // Social
        POST_LIKED,
        POST_COMMENTED,
        POST_CREATED,

        // Career
        JOB_APPLIED,            // employer receives when someone applies
        APPLICATION_STATUS,     // student receives when application status changes
        JOB_POSTED,             // students receive when new job is posted

        // Mentorship
        MENTORSHIP_REQUESTED,   // mentor receives
        MENTORSHIP_ACCEPTED,    // student receives
        MENTORSHIP_DECLINED,    // student receives
        MENTORSHIP_COMPLETED,   // both receive

        // System
        BADGE_EARNED,
        SYSTEM,
        PASSWORD_RESET_REQUEST,

        // Faculty
        NEW_FACULTY_DETECTED,   // superadmin notified when OBIS returns unknown faculty name

        // Exam schedule
        EXAM_TODAY              // student/teacher notified when they have an exam today
    }

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /** Who receives this notification */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private AppUser recipient;

    /** Who triggered it (null = system) */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private AppUser actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private NotifType type;

    /** Human-readable message */
    @Column(nullable = false, length = 500)
    private String message;

    /** Optional deep-link URL */
    @Column(length = 300)
    private String link;

    /** Optional emoji icon */
    @Column(length = 10)
    @Builder.Default
    private String icon = "🔔";

    @Column(nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}