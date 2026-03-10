package com.com.manasuniversityecosystem.domain.entity.edu;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
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
@Table(name = "tutorial")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"author", "quizzes"})
public class Tutorial {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    /**
     * JSONB i18n title
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> titleI18n = new HashMap<>();

    /**
     * JSONB i18n content (markdown or HTML per locale)
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> contentI18n = new HashMap<>();

    @Column(length = 100)
    private String category;

    @Column(length = 500)
    private String mediaUrl;

    /** TEXT = article/read, VIDEO = watch, AUDIO = listen */
    public enum MediaType { TEXT, VIDEO, AUDIO }

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    @Builder.Default
    private MediaType mediaType = MediaType.TEXT;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private Integer viewCount = 0;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "tutorial",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Quiz> quizzes = new ArrayList<>();

    public String getLocalizedTitle(String lang) {
        return titleI18n.getOrDefault(lang, titleI18n.getOrDefault("en", ""));
    }

    public String getLocalizedContent(String lang) {
        return contentI18n.getOrDefault(lang, contentI18n.getOrDefault("en", ""));
    }

    public void incrementViews() {
        this.viewCount++;
    }
}