package com.com.manasuniversityecosystem.web.controller;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/feed")
@RequiredArgsConstructor
@Slf4j
public class FeedController {

    private final PostService postService;
    private final UserService userService;
    private final CloudinaryService cloudinaryService;

    @GetMapping("/posts")
    public String getPosts(@RequestParam(defaultValue = "0") int page,
                           @RequestParam(required = false) String type,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           Model model) {
        PostType postType = null;
        if (type != null && !type.isBlank()) {
            try { postType = PostType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        Page<Post> posts = postType != null
                ? postService.getFeedByType(postType, page, 10)
                : postService.getFeed(page, 10);

        model.addAttribute("posts",         posts.getContent());
        model.addAttribute("nextPage",      posts.hasNext() ? page + 1 : -1);
        model.addAttribute("currentUserId", principal.getId());
        model.addAttribute("likedPostIds",
                posts.getContent().stream()
                        .filter(p -> postService.isLikedByUser(p.getId(), principal.getId()))
                        .map(p -> p.getId().toString())
                        .toList());

        return "feed/fragments/post-list :: postList";
    }

    @PostMapping("/post/create")
    public String createPost(@RequestParam("content") String content,
                             @RequestParam(value = "postType", defaultValue = "GENERAL") String postTypeStr,
                             @RequestParam(value = "image", required = false) MultipartFile image,
                             @AuthenticationPrincipal UserDetailsImpl principal,
                             @RequestHeader(value = "Accept-Language", defaultValue = "en") String lang,
                             Model model) {

        if (content == null || content.isBlank()) {
            model.addAttribute("error", "Post content cannot be empty.");
            return "feed/fragments/post-error :: error";
        }

        AppUser user = userService.getById(principal.getId());

        CreatePostRequest req = new CreatePostRequest();
        req.setContent(content);
        req.setLang(resolveLang(lang));
        try {
            req.setPostType(PostType.valueOf(postTypeStr.toUpperCase()));
        } catch (Exception e) {
            req.setPostType(PostType.GENERAL);
        }

        if (image != null && !image.isEmpty()) {
            try {
                if (image.getSize() <= 10L * 1024 * 1024) {
                    String url = cloudinaryService.uploadImage(image, "manas/posts", null);
                    req.setImageUrl(url);
                } else {
                    log.warn("Post image exceeds 10MB, skipping upload");
                }
            } catch (Exception e) {
                log.warn("Post image upload to Cloudinary failed: {}", e.getMessage());
            }
        }

        Post post = postService.createPost(user, req);
        model.addAttribute("post",          post);
        model.addAttribute("currentUserId", principal.getId());
        model.addAttribute("liked",         false);
        return "feed/fragments/post-card :: postCard";
    }

    @DeleteMapping("/post/{id}")
    @ResponseBody
    public ResponseEntity<Void> deletePost(@PathVariable UUID id,
                                           @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        postService.deletePost(id, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/post/{id}/like")
    public String toggleLike(@PathVariable UUID id,
                             @AuthenticationPrincipal UserDetailsImpl principal,
                             Model model) {
        AppUser user = userService.getById(principal.getId());
        boolean liked = postService.toggleLike(id, user);
        Post post = postService.getById(id);
        model.addAttribute("postId", id);
        model.addAttribute("liked",  liked);
        model.addAttribute("count",  post.getLikesCount());
        return "feed/fragments/like-btn :: likeBtn";
    }

    @GetMapping("/post/{id}/comments")
    public String getComments(@PathVariable UUID id,
                              @RequestParam(defaultValue = "0") int page,
                              Model model) {
        model.addAttribute("comments", postService.getTopLevelComments(id, page, 10));
        model.addAttribute("postId",   id);
        return "feed/fragments/comment-list :: commentList";
    }

    @PostMapping("/post/{id}/comment")
    public String addComment(@PathVariable UUID id,
                             @RequestParam("content") String content,
                             @AuthenticationPrincipal UserDetailsImpl principal,
                             Model model) {
        if (content == null || content.isBlank()) {
            return "feed/fragments/comment-card :: commentCard";
        }
        AppUser user = userService.getById(principal.getId());
        CreateCommentRequest req = new CreateCommentRequest();
        req.setContent(content);
        Comment comment = postService.addComment(user, id, req);
        model.addAttribute("comment", comment);
        return "feed/fragments/comment-card :: commentCard";
    }

    @DeleteMapping("/comment/{id}")
    @ResponseBody
    public ResponseEntity<Void> deleteComment(@PathVariable UUID id,
                                              @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser user = userService.getById(principal.getId());
        postService.deleteComment(id, user);
        return ResponseEntity.ok().build();
    }

    private String resolveLang(String acceptLang) {
        if (acceptLang == null) return "en";
        String l = acceptLang.split(",")[0].split("-")[0].toLowerCase();
        return List.of("en", "ru", "ky", "tr").contains(l) ? l : "en";
    }
}
