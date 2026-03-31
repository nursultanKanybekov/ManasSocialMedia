package com.com.manasuniversityecosystem.domain.entity.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "attendance_session",
        indexes = {
                @Index(name = "idx_att_session_course", columnList = "course_id"),
                @Index(name = "idx_att_session_date",   columnList = "session_date"),
                @Index(name = "idx_att_session_token",  columnList = "qr_token")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"course", "teacher", "records"})
public class AttendanceSession {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private AppUser teacher;

    @Column(nullable = false)
    private LocalDate sessionDate;

    @Column(length = 100)
    private String topic;

    /** UUID used as QR code payload */
    @Column(nullable = false, unique = true, length = 50)
    private String qrToken;

    /** When the QR check-in window closes */
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isOpen = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AttendanceRecord> records = new ArrayList<>();

    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }

    public boolean canCheckIn() {
        return isOpen && !isExpired();
    }
}