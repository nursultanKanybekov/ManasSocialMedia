package com.com.manasuniversityecosystem.domain.entity.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "library_book",
        indexes = {
                @Index(name = "idx_book_faculty",   columnList = "faculty_id"),
                @Index(name = "idx_book_category",  columnList = "category"),
                @Index(name = "idx_book_uploader",  columnList = "uploaded_by")
        })
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"uploadedBy", "faculty"})
public class LibraryBook {

    public enum BookCategory {
        TEXTBOOK, LECTURE_NOTES, JOURNAL, THESIS, REFERENCE, EBOOK, OTHER
    }

    @Id
    @UuidGenerator
    @Column(updatable = false, nullable = false)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(length = 300)
    private String author;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private BookCategory category = BookCategory.EBOOK;

    /** Cloudinary URL for the PDF/file */
    @Column(length = 500)
    private String fileUrl;

    /** Cloudinary URL for cover image */
    @Column(length = 500)
    private String coverUrl;

    /** ISBN or course code it belongs to */
    @Column(length = 50)
    private String isbn;

    /** Publication year */
    @Column(length = 10)
    private String publishYear;

    @Column(length = 50)
    private String language;

    /** If null, visible to all faculties */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "faculty_id")
    private Faculty faculty;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private AppUser uploadedBy;

    /** If false, only the uploading faculty can see it */
    @Column(nullable = false)
    @Builder.Default
    private Boolean isPublic = true;

    @Column(nullable = false)
    @Builder.Default
    private Integer downloadCount = 0;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}