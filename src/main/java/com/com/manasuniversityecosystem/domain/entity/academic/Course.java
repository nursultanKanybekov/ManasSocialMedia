package com.com.manasuniversityecosystem.domain.entity.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "course",
        indexes = {
                @Index(name = "idx_course_faculty",  columnList = "faculty_id"),
                @Index(name = "idx_course_teacher",  columnList = "teacher_id"),
                @Index(name = "idx_course_semester", columnList = "semester")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"teacher", "faculty", "enrollments", "assignments"})
public class Course {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 30)
    private String code;           // e.g. "BLG-201"

    @Column(nullable = false, length = 200)
    private String name;           // e.g. "Data Structures"

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 50)
    private String semester;       // e.g. "2025-2026 BAHAR"

    @Column(nullable = false)
    @Builder.Default
    private Integer credits = 3;

    @Column(nullable = false, length = 20)
    private String studyYear;      // "1", "2", "3", "4"

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private AppUser teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id", nullable = false)
    private Faculty faculty;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /** Registration is open for students */
    @Column(nullable = false)
    @Builder.Default
    private Boolean registrationOpen = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CourseEnrollment> enrollments = new ArrayList<>();

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Assignment> assignments = new ArrayList<>();

    public int getEnrolledCount() {
        return (int) enrollments.stream()
                .filter(e -> e.getStatus() == CourseEnrollment.EnrollmentStatus.ACTIVE)
                .count();
    }
}