package com.com.manasuniversityecosystem.repository;

import com.com.manasuniversityecosystem.domain.entity.Translation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TranslationRepository extends JpaRepository<Translation, Long> {

    Optional<Translation> findByMessageKeyAndLocale(String messageKey, String locale);

    List<Translation> findByLocaleOrderByMessageKeyAsc(String locale);

    List<Translation> findByMessageKeyOrderByLocaleAsc(String messageKey);

    /** All distinct keys (from any locale) */
    @Query("SELECT DISTINCT t.messageKey FROM Translation t ORDER BY t.messageKey")
    List<String> findAllDistinctKeys();

    /** Count of entries per locale — used by admin dashboard */
    @Query("SELECT t.locale, COUNT(t) FROM Translation t GROUP BY t.locale")
    List<Object[]> countByLocale();

    boolean existsByMessageKeyAndLocale(String messageKey, String locale);

    void deleteByMessageKey(String messageKey);
}