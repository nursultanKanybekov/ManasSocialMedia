package com.com.manasuniversityecosystem.web.ws;


import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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
                .build();
    }
}