package com.com.manasuniversityecosystem.domain.entity.social;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@NamedEntityGraph(
        name = "Post.withAuthorProfile",
        attributeNodes = @NamedAttributeNode(value = "author", subgraph = "author-subgraph"),
        subgraphs = @NamedSubgraph(
                name = "author-subgraph",
                attributeNodes = @NamedAttributeNode("profile")
        )
)
@Entity
@Table(name = "post",
        indexes = {
                @Index(name = "idx_post_author",  columnList = "author_id"),
                @Index(name = "idx_post_created", columnList = "created_at DESC")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"author", "comments", "likes"})
public class Post {

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id", nullable = false)
    private AppUser author;

    /**
     * JSONB i18n content:
     * {"en": "Hello world", "ru": "Привет мир", "ky": "Салам дүйнө", "tr": "Merhaba dünya"}
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "content_i18n", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private Map<String, String> contentI18n = new HashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PostType postType = PostType.GENERAL;

    @Column(nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer commentsCount = 0;

    /** Optional image attached to post (stored under /uploads/posts/) */
    @Column(name = "image_url")
    private String imageUrl;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isPinned = false;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "post",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<Comment> comments = new ArrayList<>();

    @OneToMany(mappedBy = "post",
            cascade = CascadeType.ALL,
            orphanRemoval = true,
            fetch = FetchType.LAZY)
    @Builder.Default
    private List<PostLike> likes = new ArrayList<>();

    /**
     * Returns localized content. Falls back: lang -> en -> first available -> empty.
     */
    public String getLocalizedContent(String lang) {
        if (contentI18n == null || contentI18n.isEmpty()) return "";
        String content = contentI18n.get(lang);
        if (content != null) return content;
        content = contentI18n.get("en");
        if (content != null) return content;
        return contentI18n.values().iterator().next();
    }

    /** Convenience builder method to set content for a language */
    public Post putContent(String lang, String content) {
        if (this.contentI18n == null) this.contentI18n = new HashMap<>();
        this.contentI18n.put(lang, content);
        return this;
    }
}