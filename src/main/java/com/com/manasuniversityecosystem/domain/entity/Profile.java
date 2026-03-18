package com.com.manasuniversityecosystem.domain.entity;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "profile",
        indexes = {
                @Index(name = "idx_profile_points", columnList = "total_points DESC")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"user"})
public class Profile {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private AppUser user;

    @Column(columnDefinition = "text")
    private String bio;

    @Column(length = 500)
    private String avatarUrl;

    @Column(length = 300)
    private String headline;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> socialLinks = new HashMap<>();

//    /**
//     * JSONB: ["Java", "Spring Boot", "PostgreSQL"]
//     */
//    @Type(JsonBinaryType.class)
//    @Column(columnDefinition = "jsonb")
//    @Builder.Default
//    private List<String> skills = new ArrayList<>();
//
//    /**
//     * JSONB: {"linkedin": "https://...", "github": "https://..."}
//     */
//    @Type(JsonBinaryType.class)
//    @Column(columnDefinition = "jsonb")
//    @Builder.Default
//    private Map<String, String> socialLinks = new HashMap<>();

    @Column(nullable = false)
    @Builder.Default
    private Integer totalPoints = 0;

    private Integer rankPosition;

    /** Current year of study (1–6 for OBIS students, manually set for others).
     Set automatically from OBIS login, can also be edited by the student. */
    private Integer studyYear;

    /** Set by alumni themselves — enables them to appear in the mentorship table */
    @Column(nullable = false)
    @Builder.Default
    private Boolean canMentor = false;

    /** Job title/sphere shown in mentorship table (e.g. "Senior Software Engineer at Google") */
    @Column(length = 300)
    private String mentorJobTitle;

    /** Current job title (e.g. "Backend Developer") */
    @Column(length = 200)
    private String currentJobTitle;

    /** Current employer (e.g. "Google") */
    @Column(length = 200)
    private String currentCompany;

    // ── Resume / CV fields ──────────────────────────────────────

    @Column(length = 30)
    private String phone;

    @Column(length = 200)
    private String location; // "City, Country"

    @Column(length = 300)
    private String website;

    @Column(length = 20)
    private String dateOfBirth; // "YYYY-MM-DD"

    @Column(length = 100)
    private String nationality;

    /** [{title, company, location, startDate, endDate, current, description}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, String>> workExperience = new ArrayList<>();

    /** [{degree, institution, field, startDate, endDate, gpa}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, String>> educationList = new ArrayList<>();

    /** [{language, level}]  level = Native/C2/C1/B2/B1/A2/A1 */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, String>> languages = new ArrayList<>();

    /** [{name, issuer, year, url}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    @Builder.Default
    private List<Map<String, String>> certifications = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}