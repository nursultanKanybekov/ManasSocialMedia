package com.com.manasuniversityecosystem.domain.entity.chat;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_participant")
@IdClass(ChatParticipantId.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ChatParticipant {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false)
    @Builder.Default
    private LocalDateTime joinedAt = LocalDateTime.now();

    private LocalDateTime lastReadAt;

    /** True = this participant is a channel admin (can add/remove members) */
    @Column(nullable = false, columnDefinition = "boolean default false")
    @Builder.Default
    private Boolean isChannelAdmin = false;

    public void markRead() {
        this.lastReadAt = LocalDateTime.now();
    }
}