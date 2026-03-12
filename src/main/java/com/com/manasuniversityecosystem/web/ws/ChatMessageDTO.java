package com.com.manasuniversityecosystem.web.ws;

import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    private UUID id;
    private UUID roomId;
    private UUID senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private String messageType;
    private LocalDateTime createdAt;
    private String time; // formatted HH:mm

    // ── action (for WS events) ───────────────────────────────
    /** NEW | EDIT | DELETE | PIN | UNPIN */
    @Builder.Default
    private String action = "NEW";

    // ── state flags ──────────────────────────────────────────
    private boolean isDeleted;
    private boolean isEdited;
    private boolean isPinned;

    // ── reply ────────────────────────────────────────────────
    private UUID replyToId;
    private String replyToContent;
    private String replyToSenderName;

    // ── forward ──────────────────────────────────────────────
    private String forwardedFrom;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");

    public static ChatMessageDTO from(ChatMessage msg) {
        return ChatMessageDTO.builder()
                .id(msg.getId())
                .roomId(msg.getRoom().getId())
                .senderId(msg.getSender().getId())
                .senderName(msg.getSender().getFullName())
                .senderAvatarUrl(
                        msg.getSender().getProfile() != null
                                ? msg.getSender().getProfile().getAvatarUrl()
                                : null)
                .content(msg.getContent())
                .messageType(msg.getMessageType().name())
                .createdAt(msg.getCreatedAt())
                .time(msg.getCreatedAt() != null ? msg.getCreatedAt().format(FMT) : "")
                .action("NEW")
                .isDeleted(Boolean.TRUE.equals(msg.getIsDeleted()))
                .isEdited(Boolean.TRUE.equals(msg.getIsEdited()))
                .isPinned(Boolean.TRUE.equals(msg.getIsPinned()))
                .replyToId(msg.getReplyToId())
                .replyToContent(msg.getReplyToContent())
                .replyToSenderName(msg.getReplyToSenderName())
                .forwardedFrom(msg.getForwardedFrom())
                .build();
    }

    public static ChatMessageDTO fromWithAction(ChatMessage msg, String action) {
        ChatMessageDTO dto = from(msg);
        dto.setAction(action);
        return dto;
    }
}