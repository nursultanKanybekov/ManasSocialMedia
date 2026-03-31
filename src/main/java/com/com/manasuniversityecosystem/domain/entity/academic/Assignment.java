package com.com.manasuniversityecosystem.domain.entity.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "assignment",
        indexes = {
                @Index(name = "idx_assignment_course",  columnList = "course_id"),
                @Index(name = "idx_assignment_deadline", columnList = "deadline")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"course", "createdBy", "submissions"})
public class Assignment {

    public enum AssignmentType { HOMEWORK, PROJECT, QUIZ, LAB, ESSAY }

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private AppUser createdBy;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private AssignmentType type = AssignmentType.HOMEWORK;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(nullable = false)
    @Builder.Default
    private Double maxScore = 100.0;

    /** Teacher-attached file URL (instructions PDF etc.) */
    @Column(length = 500)
    private String attachmentUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean allowFileSubmission = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean allowTextSubmission = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "assignment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<AssignmentSubmission> submissions = new ArrayList<>();

    public boolean isPastDeadline() {
        return LocalDateTime.now().isAfter(deadline);
    }
}