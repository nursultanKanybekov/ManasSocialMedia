package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.social.Comment;
import com.com.manasuniversityecosystem.domain.entity.social.Post;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.CloudinaryService;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.web.dto.social.CreateCommentRequest;
import com.com.manasuniversityecosystem.web.dto.social.CreatePostRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/feed")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Feed", description = "Posts, likes, comments and pinning")
public class ApiFeedController {

    private final PostService        postService;
    private final UserService        userService;
    private final CloudinaryService  cloudinaryService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record PostResponse(
            UUID          id,
            AuthorSummary author,
            String        content,
            String        postType,
            String        imageUrl,
            int           likesCount,
            int           commentsCount,
            boolean       isPinned,
            boolean       likedByMe,
            LocalDateTime createdAt
    ) {}

    public record AuthorSummary(
            UUID   id,
            String fullName,
            String role,
            String avatarUrl
    ) {}

    public record CommentResponse(
            UUID          id,
            AuthorSummary author,
            String        content,
            LocalDateTime createdAt
    ) {}

    public record CreatePostBody(
            @NotBlank @Size(max = 5000) String content,
            String postType  // GENERAL | NEWS | SUCCESS_STORY | ANNOUNCEMENT
    ) {}

    public record CreateCommentBody(
            @NotBlank @Size(max = 2000) String content
    ) {}

    public record LikeResponse(boolean liked, int likesCount) {}
    public record PinResponse(boolean pinned) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping
    @Operation(summary = "Get paginated feed", description = "Returns posts newest-first, pinned posts at top.")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getFeed(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0")  int page,
            @Parameter(description = "Page size")             @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Filter by type: GENERAL, NEWS, SUCCESS_STORY, ANNOUNCEMENT")
            @RequestParam(required = false) String type,
            @Parameter(description = "Language for i18n content") @RequestParam(defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        PostType postType = parsePostType(type);
        Page<Post> posts  = postType != null
                ? postService.getFeedByType(postType, page, size)
                : postService.getFeed(page, size);

        Page<PostResponse> mapped = posts.map(p -> toPostResponse(p, lang, principal.getId()));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(mapped)));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Create a new post (with optional image)")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @RequestPart("content")           String content,
            @RequestPart(value = "post_type", required = false) String postTypeStr,
            @RequestPart(value = "image",     required = false) MultipartFile image,
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        CreatePostRequest req = new CreatePostRequest();
        req.setContent(content);
        req.setLang(lang);
        req.setPostType(parsePostType(postTypeStr) != null ? parsePostType(postTypeStr) : PostType.GENERAL);

        if (image != null && !image.isEmpty() && image.getSize() <= 10L * 1024 * 1024) {
            try { req.setImageUrl(cloudinaryService.uploadImage(image, "manas/posts", null)); }
            catch (Exception ignored) {}
        }

        Post post = postService.createPost(user, req);
        return ResponseEntity.status(201)
                .body(ApiResponse.created(toPostResponse(post, lang, principal.getId())));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "Edit own post content")
    public ResponseEntity<ApiResponse<PostResponse>> editPost(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePostBody body,
            @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        CreatePostRequest req = new CreatePostRequest();
        req.setContent(body.content());
        req.setLang(lang);
        Post updated = postService.updatePost(id, user, req);
        return ResponseEntity.ok(ApiResponse.ok(toPostResponse(updated, lang, principal.getId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a post (own or admin)")
    public ResponseEntity<ApiResponse<Void>> deletePost(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        boolean isAdmin = principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN"));
        if (isAdmin) postService.deletePost(id);
        else         postService.deletePost(id, userService.getById(principal.getId()));
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/{id}/like")
    @Operation(summary = "Toggle like on a post")
    public ResponseEntity<ApiResponse<LikeResponse>> toggleLike(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        boolean liked = postService.toggleLike(id, userService.getById(principal.getId()));
        Post    post  = postService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(new LikeResponse(liked, post.getLikesCount())));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "Get comments for a post (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getComments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Comment> comments = postService.getTopLevelComments(id, page, size);
        Page<CommentResponse> mapped = comments.map(this::toCommentResponse);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(mapped)));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a comment to a post")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody CreateCommentBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent(body.content());
        Comment comment = postService.addComment(user, id, req);
        return ResponseEntity.status(201).body(ApiResponse.created(toCommentResponse(comment)));
    }

    @DeleteMapping("/comments/{id}")
    @Operation(summary = "Delete own comment")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        postService.deleteComment(id, userService.getById(principal.getId()));
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @PostMapping("/{id}/pin")
    @Operation(summary = "Toggle pin on a post (admin/mezun only)")
    public ResponseEntity<ApiResponse<PinResponse>> togglePin(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        boolean notStudent = principal.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("ROLE_STUDENT"));
        if (!notStudent) throw new SecurityException("Students cannot pin posts.");

        postService.pinPost(id);
        boolean pinned = postService.getById(id).getIsPinned();
        return ResponseEntity.ok(ApiResponse.ok(new PinResponse(pinned)));
    }

    // ══ Mappers ═══════════════════════════════════════════════════

    private PostResponse toPostResponse(Post p, String lang, UUID currentUserId) {
        return new PostResponse(
                p.getId(),
                toAuthor(p.getAuthor()),
                p.getLocalizedContent(lang),
                p.getPostType().name(),
                p.getImageUrl(),
                p.getLikesCount(),
                p.getCommentsCount(),
                p.getIsPinned(),
                postService.isLikedByUser(p.getId(), currentUserId),
                p.getCreatedAt()
        );
    }

    private CommentResponse toCommentResponse(Comment c) {
        return new CommentResponse(
                c.getId(),
                toAuthor(c.getAuthor()),
                c.getContent(),
                c.getCreatedAt()
        );
    }

    private AuthorSummary toAuthor(AppUser u) {
        String avatar = (u.getProfile() != null) ? u.getProfile().getAvatarUrl() : null;
        return new AuthorSummary(u.getId(), u.getFullName(), u.getRole().name(), avatar);
    }

    private PostType parsePostType(String type) {
        if (type == null || type.isBlank()) return null;
        try { return PostType.valueOf(type.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}