package com.com.manasuniversityecosystem.domain.entity.chat;

import lombok.*;

import java.io.Serializable;
import java.util.UUID;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode
public class ChatParticipantId implements Serializable {
    private UUID room;
    private UUID user;
}
