package com.com.manasuniversityecosystem.domain.entity.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "attendance_record",
        uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "student_id"}),
        indexes = {
                @Index(name = "idx_att_record_student", columnList = "student_id"),
                @Index(name = "idx_att_record_session", columnList = "session_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AttendanceRecord {

    public enum CheckInMethod { QR, MANUAL }
    public enum AttendanceStatus { PRESENT, ABSENT, EXCUSED, LATE }

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private AttendanceSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AttendanceStatus status = AttendanceStatus.PRESENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private CheckInMethod method = CheckInMethod.QR;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime checkedInAt = LocalDateTime.now();

    @Column(length = 300)
    private String note;
}