package com.com.manasuniversityecosystem.domain.entity.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "assignment_submission",
        uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "assignment_id"}),
        indexes = {
                @Index(name = "idx_submission_student",    columnList = "student_id"),
                @Index(name = "idx_submission_assignment", columnList = "assignment_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AssignmentSubmission {

    public enum SubmissionStatus { SUBMITTED, LATE, GRADED, RETURNED }

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    private Assignment assignment;

    @Column(columnDefinition = "text")
    private String textContent;

    @Column(length = 500)
    private String fileUrl;

    @Column(length = 200)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SubmissionStatus status = SubmissionStatus.SUBMITTED;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime submittedAt = LocalDateTime.now();

    /** Score given by teacher */
    private Double score;

    /** Teacher feedback */
    @Column(columnDefinition = "text")
    private String feedback;

    private LocalDateTime gradedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "graded_by")
    private AppUser gradedBy;
}