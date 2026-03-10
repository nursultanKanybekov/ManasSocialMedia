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

    /** Set by alumni themselves — enables them to appear in the mentorship table */
    @Column(nullable = false)
    @Builder.Default
    private Boolean canMentor = false;

    /** Job title/sphere shown in mentorship table (e.g. "Senior Software Engineer at Google") */
    @Column(length = 300)
    private String mentorJobTitle;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}