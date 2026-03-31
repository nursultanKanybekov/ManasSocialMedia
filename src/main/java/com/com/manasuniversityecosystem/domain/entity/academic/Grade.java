package com.com.manasuniversityecosystem.domain.entity.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "grade",
        indexes = {
                @Index(name = "idx_grade_student", columnList = "student_id"),
                @Index(name = "idx_grade_course",  columnList = "course_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Grade {

    public enum GradeType { MIDTERM, FINAL, QUIZ, PROJECT, HOMEWORK, ATTENDANCE }

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private AppUser student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private AppUser teacher;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GradeType type;

    /** 0–100 */
    @Column(nullable = false)
    private Double score;

    /** Maximum possible score */
    @Column(nullable = false)
    @Builder.Default
    private Double maxScore = 100.0;

    /** Letter grade: A, A-, B+, B, B-, C+, C, D, F */
    @Column(length = 5)
    private String letterGrade;

    @Column(length = 500)
    private String feedback;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    public void computeLetterGrade() {
        if (maxScore == null || maxScore == 0) return;
        double pct = score / maxScore * 100.0;
        if      (pct >= 90) letterGrade = "A";
        else if (pct >= 85) letterGrade = "A-";
        else if (pct >= 80) letterGrade = "B+";
        else if (pct >= 75) letterGrade = "B";
        else if (pct >= 70) letterGrade = "B-";
        else if (pct >= 65) letterGrade = "C+";
        else if (pct >= 60) letterGrade = "C";
        else if (pct >= 50) letterGrade = "D";
        else                letterGrade = "F";
    }
}