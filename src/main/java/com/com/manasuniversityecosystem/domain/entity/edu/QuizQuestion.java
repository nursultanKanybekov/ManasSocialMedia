package com.com.manasuniversityecosystem.domain.entity.edu;


import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "quiz_question")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"quiz"})
public class QuizQuestion {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    /**
     * JSONB i18n question text:
     * {"en": "What is Java?", "ru": "Что такое Java?", ...}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> questionI18n = new HashMap<>();

    /**
     * JSONB i18n options (list of strings per language):
     * {"en": ["Option A", "Option B", "Option C", "Option D"],
     *  "ru": ["Вариант А", "Вариант Б", ...], ...}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "options_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, List<String>> optionsI18n = new HashMap<>();

    /** 0-based index of the correct option */
    @Column(nullable = false)
    private Integer correctOption;

    public String getLocalizedQuestion(String lang) {
        return questionI18n.getOrDefault(lang, questionI18n.getOrDefault("en", ""));
    }

    public List<String> getLocalizedOptions(String lang) {
        List<String> opts = optionsI18n.get(lang);
        if (opts != null) return opts;
        return optionsI18n.getOrDefault("en", List.of());
    }

    public boolean isCorrect(int selectedOption) {
        return this.correctOption == selectedOption;
    }
}