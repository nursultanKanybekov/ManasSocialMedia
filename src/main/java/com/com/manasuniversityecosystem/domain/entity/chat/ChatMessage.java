package com.com.manasuniversityecosystem.domain.entity.chat;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_message",
        indexes = {
                @Index(name = "idx_msg_room",   columnList = "room_id, created_at DESC"),
                @Index(name = "idx_msg_sender", columnList = "sender_id")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"room", "sender"})
public class ChatMessage {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private ChatRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private AppUser sender;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    // ── EDIT ────────────────────────────────────────────────
    @Column(nullable = false)
    @Builder.Default
    private Boolean isEdited = false;

    @Column
    private LocalDateTime editedAt;

    // ── PIN ─────────────────────────────────────────────────
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    // ── REPLY ───────────────────────────────────────────────
    /** ID of the message being replied to */
    @Column(name = "reply_to_id")
    private UUID replyToId;

    /** Snapshot of replied-to content (so it stays even if original is deleted) */
    @Column(name = "reply_to_content", length = 200)
    private String replyToContent;

    /** Snapshot of replied-to sender name */
    @Column(name = "reply_to_sender_name", length = 100)
    private String replyToSenderName;

    // ── FORWARD ─────────────────────────────────────────────
    /** Original sender name when forwarded */
    @Column(name = "forwarded_from", length = 100)
    private String forwardedFrom;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    public void softDelete() {
        this.isDeleted = true;
        this.content = "This message was deleted";
    }

    public enum MessageType {
        TEXT, FILE, IMAGE, VOICE, SYSTEM
    }
}