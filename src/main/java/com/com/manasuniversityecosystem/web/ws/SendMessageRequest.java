package com.com.manasuniversityecosystem.web.ws;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class SendMessageRequest {

    @NotBlank
    @Size(max = 4000)
    private String content;

    private String messageType; // TEXT, FILE, IMAGE — default TEXT

    /** ID of the message being replied to (optional) */
    private UUID replyToId;

    /** ID of the message being forwarded (optional) */
    private UUID forwardedFromId;
}