package com.com.manasuniversityecosystem.domain.entity.career;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.JobType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "job_listing",
        indexes = {
                @Index(name = "idx_job_active", columnList = "is_active")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"postedBy", "applications"})
public class JobListing {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by", nullable = false)
    private AppUser postedBy;

    /**
     * Optional: faculty whose students/alumni this job is aimed at.
     * When set, faculty admins of that faculty will be notified.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_faculty_id")
    private com.com.manasuniversityecosystem.domain.entity.Faculty targetFaculty;

    /**
     * JSONB i18n title:
     * {"en": "Java Backend Developer", "ru": "Java разработчик", "ky": "Java иштеп чыгуучу", "tr": "Java Geliştirici"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> titleI18n = new HashMap<>();

    /**
     * JSONB i18n full description
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobType jobType;

    @Column(length = 200)
    private String location;

    @Column(length = 100)
    private String salaryRange;

    private LocalDate deadline;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "job",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<JobApplication> applications = new ArrayList<>();

    public String getLocalizedTitle(String lang) {
        if (titleI18n == null || titleI18n.isEmpty()) return "";
        return titleI18n.getOrDefault(lang, titleI18n.getOrDefault("en", ""));
    }

    public String getLocalizedDescription(String lang) {
        if (descriptionI18n == null || descriptionI18n.isEmpty()) return "";
        return descriptionI18n.getOrDefault(lang, descriptionI18n.getOrDefault("en", ""));
    }

    public boolean isExpired() {
        return deadline != null && deadline.isBefore(LocalDate.now());
    }
}
