package com.com.manasuniversityecosystem.domain.entity.edu;


import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quiz")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"tutorial", "questions"})
public class Quiz {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tutorial_id")
    private Tutorial tutorial;

    /**
     * JSONB i18n title
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "title_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> titleI18n = new HashMap<>();

    /** Minimum score (0–100) to pass */
    @Column(nullable = false)
    @Builder.Default
    private Integer passScore = 70;

    @OneToMany(mappedBy = "quiz",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<QuizQuestion> questions = new ArrayList<>();

    public String getLocalizedTitle(String lang) {
        return titleI18n.getOrDefault(lang, titleI18n.getOrDefault("en", ""));
    }
}