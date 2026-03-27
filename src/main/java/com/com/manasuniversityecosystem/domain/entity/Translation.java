package com.com.manasuniversityecosystem.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Stores a single translated string: one row per (messageKey, locale) pair.
 * Replaces the static .properties files so admins/secretaries can edit
 * translations live without a redeploy.
 */
@Entity
@Table(
        name = "translation",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_translation_key_locale",
                columnNames = {"message_key", "locale"}
        ),
        indexes = {
                @Index(name = "idx_translation_locale", columnList = "locale"),
                @Index(name = "idx_translation_key",    columnList = "message_key")
        }
)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Translation {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE,
            generator = "translation_seq")
    @SequenceGenerator(name = "translation_seq",
            sequenceName = "translation_seq", allocationSize = 50)
    private Long id;

    /** e.g. "nav.feed", "auth.login.title" */
    @Column(name = "message_key", nullable = false, length = 200)
    private String messageKey;

    /** "en", "ru", "ky", "tr" */
    @Column(nullable = false, length = 10)
    private String locale;

    /** The translated text */
    @Column(nullable = false, length = 2000)
    private String value;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}