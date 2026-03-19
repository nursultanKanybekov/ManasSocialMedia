package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.social.Post;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.web.dto.social.CreatePostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/news")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "News & Articles",
        description = "University news (UNIVERSITY_NEWS posts) and community articles (NEWS posts)")
public class ApiNewsController {

    private final PostService postService;
    private final UserService userService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record NewsResponse(
            UUID   id, String title, String content, String imageUrl,
            String authorName, String postType, LocalDateTime createdAt
    ) {}

    public record CreateArticleBody(
            @NotBlank @Size(max = 200)    String title,
            @NotBlank @Size(max = 100000) String content,
            String imageUrl
    ) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping("/university")
    @Operation(summary = "List university news (UNIVERSITY_NEWS type posts, paginated)")
    public ResponseEntity<ApiResponse<PageResponse<NewsResponse>>> universityNews(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "10") int    size,
            @RequestParam(defaultValue = "en") String lang) {

        var posts = postService.getFeedByType(PostType.UNIVERSITY_NEWS, page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                posts.map(p -> toResponse(p, lang)))));
    }

    @GetMapping("/articles")
    @Operation(summary = "List community articles (NEWS type posts, paginated)")
    public ResponseEntity<ApiResponse<PageResponse<NewsResponse>>> articles(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "10") int    size,
            @RequestParam(defaultValue = "en") String lang) {

        var posts = postService.getFeedByType(PostType.NEWS, page, size);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                posts.map(p -> toResponse(p, lang)))));
    }

    @GetMapping("/articles/{id}")
    @Operation(summary = "Get a single article/news post by ID")
    public ResponseEntity<ApiResponse<NewsResponse>> getArticle(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "en") String lang) {

        Post p = postService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(p, lang)));
    }

    @PostMapping("/articles")
    @Operation(summary = "Publish a community article (any authenticated user)")
    public ResponseEntity<ApiResponse<NewsResponse>> createArticle(
            @Valid @RequestBody CreateArticleBody body,
            @RequestParam(defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser author = userService.getById(principal.getId());
        CreatePostRequest req = new CreatePostRequest();
        req.setContent("**" + body.title() + "**\n\n" + body.content());
        req.setLang(lang);
        req.setPostType(PostType.NEWS);
        req.setImageUrl(body.imageUrl());

        Post post = postService.createPost(author, req);
        return ResponseEntity.status(201).body(ApiResponse.created(toResponse(post, lang)));
    }

    // ══ Mapper ═══════════════════════════════════════════════════

    private NewsResponse toResponse(Post p, String lang) {
        String raw = p.getLocalizedContent(lang);
        // Extract title from **bold** prefix if present
        String title   = raw.startsWith("**") && raw.contains("**\n")
                ? raw.substring(2, raw.indexOf("**\n"))
                : p.getPostType().name();
        String content = raw.startsWith("**") && raw.contains("**\n\n")
                ? raw.substring(raw.indexOf("**\n\n") + 4)
                : raw;

        return new NewsResponse(
                p.getId(), title, content, p.getImageUrl(),
                p.getAuthor() != null ? p.getAuthor().getFullName() : "Admin",
                p.getPostType().name(), p.getCreatedAt()
        );
    }
}