package com.com.manasuniversityecosystem.repository.social;

import com.com.manasuniversityecosystem.domain.entity.social.Post;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PostRepository extends JpaRepository<Post, UUID> {

    /**
     * All posts newest-first (pinned on top).
     * @EntityGraph references the NamedEntityGraph on Post — this is the only
     * reliable way to eagerly load a nested association (author -> profile)
     * alongside Spring Data pagination in Hibernate 6.
     */
    @EntityGraph("Post.withAuthorProfile")
    @Query("SELECT p FROM Post p ORDER BY p.isPinned DESC, p.createdAt DESC")
    Page<Post> findFeedPosts(Pageable pageable);

    @EntityGraph("Post.withAuthorProfile")
    @Query("SELECT p FROM Post p WHERE p.postType = :type ORDER BY p.isPinned DESC, p.createdAt DESC")
    Page<Post> findByPostType(@Param("type") PostType type, Pageable pageable);

    @EntityGraph("Post.withAuthorProfile")
    @Query("SELECT p FROM Post p WHERE p.author.id = :authorId ORDER BY p.createdAt DESC")
    Page<Post> findByAuthorId(@Param("authorId") UUID authorId, Pageable pageable);

    Optional<Post> findByIdAndAuthorId(UUID id, UUID authorId);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.id = :id")
    void incrementLikes(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = p.likesCount - 1 WHERE p.id = :id AND p.likesCount > 0")
    void decrementLikes(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.commentsCount = p.commentsCount + 1 WHERE p.id = :id")
    void incrementComments(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE Post p SET p.commentsCount = p.commentsCount - 1 WHERE p.id = :id AND p.commentsCount > 0")
    void decrementComments(@Param("id") UUID id);

    long countByAuthorId(UUID authorId);

    @Modifying
    @Query("DELETE FROM Post p WHERE p.author.id = :authorId")
    void deleteByAuthorId(@Param("authorId") UUID authorId);
}