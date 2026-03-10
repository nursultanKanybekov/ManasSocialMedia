package com.com.manasuniversityecosystem.domain.entity.gamification;


import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "badge")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Badge {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    /**
     * JSONB i18n names:
     * {"en": "First Post", "ru": "Первый пост", "ky": "Биринчи пост", "tr": "İlk Gönderi"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "name_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> nameI18n = new HashMap<>();

    /**
     * JSONB i18n descriptions
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "description_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> descriptionI18n = new HashMap<>();

    @Column(length = 500)
    private String iconUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BadgeTier tier = BadgeTier.BRONZE;

    /** Returns localized name, falls back to EN, then code */
    public String getLocalizedName(String lang) {
        return nameI18n.getOrDefault(lang,
                nameI18n.getOrDefault("en", code));
    }

    /** Returns localized description, falls back to EN */
    public String getLocalizedDescription(String lang) {
        return descriptionI18n.getOrDefault(lang,
                descriptionI18n.getOrDefault("en", ""));
    }

    public enum BadgeTier {
        BRONZE, SILVER, GOLD, PLATINUM
    }
}

