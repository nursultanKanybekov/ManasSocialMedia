package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.Translation;
import com.com.manasuniversityecosystem.repository.TranslationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TranslationService {

    private static final List<String> SUPPORTED_LOCALES = List.of("en", "ru", "ky", "tr");

    private final TranslationRepository repo;

    /** All translations for a given locale — cached per locale */
    @Cacheable(value = "translations", key = "#locale")
    public Map<String, String> getAll(String locale) {
        Map<String, String> map = new LinkedHashMap<>();
        repo.findByLocaleOrderByMessageKeyAsc(locale)
                .forEach(t -> map.put(t.getMessageKey(), t.getValue()));
        return map;
    }

    /** Single value lookup — used by DbMessageSource */
    @Cacheable(value = "translation-entry", key = "#locale + ':' + #key")
    public Optional<String> get(String key, String locale) {
        return repo.findByMessageKeyAndLocale(key, locale)
                .map(Translation::getValue);
    }

    /** Save or update a single translation, then evict related caches */
    @Transactional
    @CacheEvict(value = {"translations", "translation-entry"}, allEntries = true)
    public Translation save(String key, String locale, String value) {
        Translation t = repo.findByMessageKeyAndLocale(key, locale)
                .orElse(Translation.builder()
                        .messageKey(key)
                        .locale(locale)
                        .build());
        t.setValue(value);
        Translation saved = repo.save(t);
        log.info("Translation saved: [{}][{}] = \"{}\"", locale, key, value);
        return saved;
    }

    /** Bulk save — used during seed from .properties files */
    @Transactional
    @CacheEvict(value = {"translations", "translation-entry"}, allEntries = true)
    public void saveAll(List<Translation> translations) {
        repo.saveAll(translations);
        log.info("Bulk saved {} translations", translations.size());
    }

    /** Saves multiple locales for one key at once (from admin form) */
    @Transactional
    @CacheEvict(value = {"translations", "translation-entry"}, allEntries = true)
    public void saveAllLocalesForKey(String key, Map<String, String> localeValues) {
        for (Map.Entry<String, String> entry : localeValues.entrySet()) {
            String locale = entry.getKey();
            String value  = entry.getValue();
            if (value != null && !value.isBlank()) {
                save(key, locale, value.trim());
            }
        }
    }

    /** All distinct keys sorted */
    public List<String> getAllKeys() {
        return repo.findAllDistinctKeys();
    }

    /** All translations for ONE key across all locales */
    public Map<String, String> getByKey(String key) {
        Map<String, String> result = new LinkedHashMap<>();
        // Ensure all locales are present (even if empty)
        SUPPORTED_LOCALES.forEach(l -> result.put(l, ""));
        repo.findByMessageKeyOrderByLocaleAsc(key)
                .forEach(t -> result.put(t.getLocale(), t.getValue()));
        return result;
    }

    public List<String> getSupportedLocales() {
        return SUPPORTED_LOCALES;
    }

    /** Count of translations per locale for admin dashboard */
    public Map<String, Long> getCountByLocale() {
        Map<String, Long> counts = new LinkedHashMap<>();
        repo.countByLocale().forEach(row -> counts.put((String) row[0], (Long) row[1]));
        return counts;
    }

    public long getTotalKeys() {
        return repo.findAllDistinctKeys().size();
    }
}