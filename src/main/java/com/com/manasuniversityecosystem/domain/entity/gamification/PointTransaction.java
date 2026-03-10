package com.com.manasuniversityecosystem.domain.entity.gamification;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "point_transaction",
        indexes = {
                @Index(name = "idx_pt_user",   columnList = "user_id"),
                @Index(name = "idx_pt_reason", columnList = "reason")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PointTransaction {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PointReason reason;

    /** UUID of the entity that triggered this transaction (post, quiz, job, etc.) */
    private UUID refId;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}