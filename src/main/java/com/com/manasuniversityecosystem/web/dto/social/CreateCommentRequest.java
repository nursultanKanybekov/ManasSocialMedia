package com.com.manasuniversityecosystem.web.dto.social;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateCommentRequest {

    @NotBlank
    @Size(max = 2000)
    private String content;

    /** null for top-level comments, set for replies */
    private UUID parentCommentId;
}
