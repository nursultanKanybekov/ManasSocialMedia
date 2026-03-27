package com.com.manasuniversityecosystem.domain.entity.exam;

import com.com.manasuniversityecosystem.domain.entity.Faculty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "exam_schedule",
        indexes = {
                @Index(name = "idx_exam_date",    columnList = "exam_date"),
                @Index(name = "idx_exam_faculty",  columnList = "faculty_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ExamSchedule {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    /** e.g. "BLG-102 Grafik ve Animasyon" */
    @Column(nullable = false, length = 300)
    private String courseName;

    /** e.g. "BLG-102" */
    @Column(length = 50)
    private String courseCode;

    /** Teacher/instructor name */
    @Column(length = 200)
    private String teacherName;

    /** Proctor name */
    @Column(length = 200)
    private String proctorName;

    /** Exam date */
    @Column(nullable = false)
    private LocalDate examDate;

    /** Time string e.g. "9:50-12:00" */
    @Column(length = 50)
    private String examTime;

    /** Room / location e.g. "MYO-135" or "ONLİNE" */
    @Column(length = 200)
    private String examRoom;

    /** Program/class name e.g. "BİLGİSAYAR PROGRAMCILIĞI PROGRAMI 1. SINIF" */
    @Column(length = 300)
    private String programName;

    /** Which faculty this exam belongs to */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    /** Semester label e.g. "2025-2026 BAHAR" */
    @Column(length = 100)
    private String semester;
}