package com.com.manasuniversityecosystem.config;

import com.com.manasuniversityecosystem.domain.entity.Translation;
import com.com.manasuniversityecosystem.repository.TranslationRepository;
import com.com.manasuniversityecosystem.service.TranslationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Runs at startup (ORDER 1 — before DataInitializer).
 * If the translation table is empty, seeds it from the 4 .properties files.
 * After the first seed, the DB is the source of truth — .properties act as fallback only.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class TranslationSeeder implements CommandLineRunner {

    private static final Map<String, String> LOCALE_FILES = Map.of(
            "en", "i18n/messages.properties",
            "ky", "i18n/messages_ky.properties",
            "ru", "i18n/messages_ru.properties",
            "tr", "i18n/messages_tr.properties"
    );

    private final TranslationRepository translationRepo;
    private final TranslationService    translationService;

    @Override
    public void run(String... args) {
        if (translationRepo.count() > 0) {
            log.info("[i18n] Translation table already seeded ({} rows) — skipping.",
                    translationRepo.count());
            return;
        }

        log.info("[i18n] Seeding translations from .properties files...");
        List<Translation> batch = new ArrayList<>();

        for (Map.Entry<String, String> entry : LOCALE_FILES.entrySet()) {
            String locale   = entry.getKey();
            String filePath = entry.getValue();
            try {
                Properties props = loadProperties(filePath);
                for (String key : props.stringPropertyNames()) {
                    String value = props.getProperty(key, "").trim();
                    if (!value.isBlank()) {
                        batch.add(Translation.builder()
                                .messageKey(key)
                                .locale(locale)
                                .value(value)
                                .build());
                    }
                }
                log.info("[i18n] Loaded {} keys for locale '{}'", props.size(), locale);
            } catch (Exception e) {
                log.warn("[i18n] Could not load {}: {}", filePath, e.getMessage());
            }
        }

        translationService.saveAll(batch);
        log.info("[i18n] ✅ Seeded {} translation entries into DB.", batch.size());
    }

    private Properties loadProperties(String classpathPath) throws Exception {
        Properties props = new Properties();
        ClassPathResource resource = new ClassPathResource(classpathPath);
        try (InputStream is = resource.getInputStream();
             InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
            props.load(reader);
        }
        return props;
    }
}