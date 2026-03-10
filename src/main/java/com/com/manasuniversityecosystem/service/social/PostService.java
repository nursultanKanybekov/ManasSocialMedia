package com.com.manasuniversityecosystem.service.social;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.social.Comment;
import com.com.manasuniversityecosystem.domain.entity.social.Post;
import com.com.manasuniversityecosystem.domain.entity.social.PostLike;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import com.com.manasuniversityecosystem.repository.social.CommentRepository;
import com.com.manasuniversityecosystem.repository.social.PostLikeRepository;
import com.com.manasuniversityecosystem.repository.social.PostRepository;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import com.com.manasuniversityecosystem.web.dto.social.CreateCommentRequest;
import com.com.manasuniversityecosystem.web.dto.social.CreatePostRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostService {

    private final PostRepository postRepo;
    private final CommentRepository commentRepo;
    private final PostLikeRepository likeRepo;
    private final GamificationService gamificationService;
    private final NotificationService notificationService;

    // ── POST CRUD ────────────────────────────────────────────

    @Transactional
    public Post createPost(AppUser author, CreatePostRequest req) {
        if (req.getContent() == null || req.getContent().isBlank()) {
            throw new IllegalArgumentException("Post content cannot be empty.");
        }

        // Build i18n content map — user writes in their language,
        // we store under their locale key. Other locales can be added by admin.
        Map<String, String> contentI18n = new HashMap<>();
        contentI18n.put(req.getLang() != null ? req.getLang() : "en", req.getContent());

        // Allow full i18n map if provided (admin/multi-language posts)
        if (req.getContentI18n() != null && !req.getContentI18n().isEmpty()) {
            contentI18n.putAll(req.getContentI18n());
        }

        Post post = Post.builder()
                .author(author)
                .contentI18n(contentI18n)
                .postType(req.getPostType() != null ? req.getPostType() : PostType.GENERAL)
                .imageUrl(req.getImageUrl())
                .build();

        postRepo.save(post);

        // Award points async
        gamificationService.awardPoints(author, PointReason.POST, post.getId());

        log.info("Post created by {}: id={}", author.getEmail(), post.getId());
        return post;
    }

    @Transactional(readOnly = true)
    public Page<Post> getFeed(int page, int size) {
        return postRepo.findFeedPosts(PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Post> getFeedByType(PostType type, int page, int size) {
        return postRepo.findByPostType(type, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Page<Post> getUserPosts(UUID authorId, int page, int size) {
        return postRepo.findByAuthorId(authorId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Post getById(UUID id) {
        return postRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + id));
    }

    @Transactional
    public Post updatePost(UUID postId, AppUser user, CreatePostRequest req) {
        Post post = postRepo.findByIdAndAuthorId(postId, user.getId())
                .orElseThrow(() -> new SecurityException("Not authorized to edit this post."));

        if (req.getContent() != null && !req.getContent().isBlank()) {
            String lang = req.getLang() != null ? req.getLang() : "en";
            post.getContentI18n().put(lang, req.getContent());
        }
        if (req.getContentI18n() != null) {
            post.getContentI18n().putAll(req.getContentI18n());
        }
        if (req.getPostType() != null) {
            post.setPostType(req.getPostType());
        }
        return postRepo.save(post);
    }

    /** Admin: delete any post without ownership check */
    @Transactional
    public void deletePost(UUID postId) {
        Post post = postRepo.findById(postId)
                .orElseThrow(() -> new IllegalArgumentException("Post not found: " + postId));
        postRepo.delete(post);
    }

    public void deletePost(UUID postId, AppUser user) {
        Post post = postRepo.findByIdAndAuthorId(postId, user.getId())
                .orElseThrow(() -> new SecurityException("Not authorized to delete this post."));
        postRepo.delete(post);
    }

    @Transactional
    public void pinPost(UUID postId) {
        Post post = getById(postId);
        post.setIsPinned(!post.getIsPinned()); // toggle
        postRepo.save(post);
    }

    // ── LIKES ────────────────────────────────────────────────

    /**
     * Toggle like: returns true if liked, false if unliked.
     */
    @Transactional
    public boolean toggleLike(UUID postId, AppUser user) {
        if (likeRepo.existsByPostIdAndUserId(postId, user.getId())) {
            // Unlike
            PostLike like = likeRepo.findByPostIdAndUserId(postId, user.getId())
                    .orElseThrow();
            likeRepo.delete(like);
            postRepo.decrementLikes(postId);
            return false;
        } else {
            // Like
            Post post = getById(postId);
            PostLike like = PostLike.builder()
                    .post(post)
                    .user(user)
                    .build();
            likeRepo.save(like);
            postRepo.incrementLikes(postId);

            // Award point to post AUTHOR (not the liker)
            gamificationService.awardPoints(
                    post.getAuthor(), PointReason.LIKE_RECEIVED, postId);
            notificationService.notifyLike(post.getAuthor().getId(), user.getId(), user.getFullName());
            return true;
        }
    }

    @Transactional(readOnly = true)
    public boolean isLikedByUser(UUID postId, UUID userId) {
        return likeRepo.existsByPostIdAndUserId(postId, userId);
    }

    // ── COMMENTS ─────────────────────────────────────────────

    @Transactional
    public Comment addComment(AppUser author, UUID postId, CreateCommentRequest req) {
        Post post = getById(postId);

        Comment parent = null;
        if (req.getParentCommentId() != null) {
            parent = commentRepo.findById(req.getParentCommentId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent comment not found."));
        }

        Comment comment = Comment.builder()
                .post(post)
                .author(author)
                .content(req.getContent())
                .parentComment(parent)
                .build();

        commentRepo.save(comment);
        postRepo.incrementComments(postId);

        // Award points to commenter
        gamificationService.awardPoints(author, PointReason.COMMENT, comment.getId());
        notificationService.notifyComment(post.getAuthor().getId(), author.getId(), author.getFullName());
        log.info("Comment added by {} on post {}", author.getEmail(), postId);
        return comment;
    }

    @Transactional(readOnly = true)
    public Page<Comment> getTopLevelComments(UUID postId, int page, int size) {
        return commentRepo.findTopLevelByPostId(postId, PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public List<Comment> getReplies(UUID parentCommentId) {
        return commentRepo.findRepliesByParentId(parentCommentId);
    }

    @Transactional
    public void deleteComment(UUID commentId, AppUser user) {
        Comment comment = commentRepo.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("Comment not found."));
        if (!comment.getAuthor().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to delete this comment.");
        }
        postRepo.decrementComments(comment.getPost().getId());
        commentRepo.delete(comment);
    }

    @Transactional(readOnly = true)
    public long countUserPosts(UUID userId) {
        return postRepo.countByAuthorId(userId);
    }
}