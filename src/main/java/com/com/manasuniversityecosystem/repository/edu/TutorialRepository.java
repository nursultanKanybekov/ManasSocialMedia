package com.com.manasuniversityecosystem.repository.edu;

import com.com.manasuniversityecosystem.domain.entity.edu.Tutorial;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TutorialRepository extends JpaRepository<Tutorial, UUID> {

    Page<Tutorial> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<Tutorial> findByCategoryOrderByCreatedAtDesc(String category, Pageable pageable);

    List<Tutorial> findByCategoryOrderByViewCountDesc(String category);

    @Modifying
    @Query("UPDATE Tutorial t SET t.viewCount = t.viewCount + 1 WHERE t.id = :id")
    void incrementViews(@Param("id") UUID id);

    @Query("SELECT DISTINCT t.category FROM Tutorial t WHERE t.category IS NOT NULL ORDER BY t.category")
    List<String> findAllCategories();

    @Query(value = "SELECT * FROM tutorial t WHERE " +
            "(:mediaType IS NULL OR t.media_type = :mediaType) AND " +
            "(:category IS NULL OR t.category = :category) AND " +
            "(:query IS NULL OR " +
            "  LOWER(t.title_i18n->>'en') LIKE LOWER(CONCAT('%',:query,'%')) OR " +
            "  LOWER(t.title_i18n->>'ru') LIKE LOWER(CONCAT('%',:query,'%')) OR " +
            "  LOWER(t.title_i18n->>'ky') LIKE LOWER(CONCAT('%',:query,'%'))) " +
            "ORDER BY t.created_at DESC",
            countQuery = "SELECT COUNT(*) FROM tutorial t WHERE " +
                    "(:mediaType IS NULL OR t.media_type = :mediaType) AND " +
                    "(:category IS NULL OR t.category = :category) AND " +
                    "(:query IS NULL OR " +
                    "  LOWER(t.title_i18n->>'en') LIKE LOWER(CONCAT('%',:query,'%')) OR " +
                    "  LOWER(t.title_i18n->>'ru') LIKE LOWER(CONCAT('%',:query,'%')) OR " +
                    "  LOWER(t.title_i18n->>'ky') LIKE LOWER(CONCAT('%',:query,'%')))",
            nativeQuery = true)
    Page<Tutorial> search(@Param("mediaType") String mediaType,
                          @Param("category") String category,
                          @Param("query") String query,
                          Pageable pageable);
}