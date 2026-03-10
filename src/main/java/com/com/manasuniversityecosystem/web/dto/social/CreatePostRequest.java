package com.com.manasuniversityecosystem.web.dto.social;


import com.com.manasuniversityecosystem.domain.enums.PostType;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Map;

@Data
public class CreatePostRequest {

    @NotBlank(message = "Post content cannot be empty.")
    private String content;

    /** Language code of the content above: en, ru, ky, tr */
    private String lang;

    /** Optional: full i18n map for multi-language posts */
    private Map<String, String> contentI18n;

    private PostType postType;

    /** URL of uploaded image (set by controller after saving file) */
    private String imageUrl;
}