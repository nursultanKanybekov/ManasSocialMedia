package com.com.manasuniversityecosystem.config;

import com.com.manasuniversityecosystem.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.Locale;
import java.util.Optional;

/**
 * Custom MessageSource that:
 * 1. Looks up the key in the database (via TranslationService)
 * 2. Falls back to the static .properties files if not found in DB
 *
 * Thymeleaf #{...} expressions automatically use this bean because
 * it is registered as the primary MessageSource in LocaleConfig.
 */
@Slf4j
public class DbMessageSource implements MessageSource {

    private final TranslationService translationService;
    private final ResourceBundleMessageSource fallback;

    public DbMessageSource(TranslationService translationService) {
        this.translationService = translationService;
        this.fallback = new ResourceBundleMessageSource();
        this.fallback.setBasename("i18n/messages");
        this.fallback.setDefaultEncoding("UTF-8");
        this.fallback.setFallbackToSystemLocale(false);
        this.fallback.setUseCodeAsDefaultMessage(true);
    }

    @Override
    public String getMessage(String code, Object[] args, String defaultMessage, Locale locale) {
        String lang = resolveLocale(locale);
        try {
            Optional<String> db = translationService.get(code, lang);
            if (db.isPresent()) {
                return format(db.get(), args);
            }
        } catch (Exception e) {
            log.debug("[i18n] DB lookup failed for key={} locale={}: {}", code, lang, e.getMessage());
        }
        return fallback.getMessage(code, args, defaultMessage, locale);
    }

    @Override
    public String getMessage(String code, Object[] args, Locale locale) throws NoSuchMessageException {
        String lang = resolveLocale(locale);
        try {
            Optional<String> db = translationService.get(code, lang);
            if (db.isPresent()) {
                return format(db.get(), args);
            }
        } catch (Exception e) {
            log.debug("[i18n] DB lookup failed for key={} locale={}: {}", code, lang, e.getMessage());
        }
        return fallback.getMessage(code, args, locale);
    }

    @Override
    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        String[] codes = resolvable.getCodes();
        if (codes != null) {
            for (String code : codes) {
                String result = getMessage(code, resolvable.getArguments(),
                        (String) null, locale);
                if (result != null) return result;
            }
        }
        return fallback.getMessage(resolvable, locale);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────

    /** Normalise locale to 2-char language code */
    private String resolveLocale(Locale locale) {
        if (locale == null) return "en";
        String lang = locale.getLanguage();
        return switch (lang) {
            case "ru", "ky", "tr" -> lang;
            default -> "en";
        };
    }

    /** Apply MessageFormat-style arguments if any */
    private String format(String pattern, Object[] args) {
        if (args == null || args.length == 0) return pattern;
        try {
            return java.text.MessageFormat.format(pattern, args);
        } catch (Exception e) {
            return pattern;
        }
    }
}