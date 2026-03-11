package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.repository.gamification.UserBadgeRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeExportService {

    private final TemplateEngine       templateEngine;
    private final UserBadgeRepository  userBadgeRepo;

    @Value("${app.base-url:http://localhost:8081}")
    private String baseUrl;

    /**
     * standard: "US" | "EU" | "ASIA"
     */
    @Transactional(readOnly = true)
    public byte[] generateResumePdf(AppUser user, String lang, String standard) {
        Profile profile = user.getProfile();

        Context ctx = new Context(Locale.forLanguageTag(lang));
        ctx.setVariable("user",    user);
        ctx.setVariable("profile", profile);
        ctx.setVariable("badges",  userBadgeRepo.findByUserIdWithBadge(user.getId()));
        ctx.setVariable("lang",    lang);
        ctx.setVariable("baseUrl", baseUrl);

        String templateName = switch (standard.toUpperCase()) {
            case "EU"   -> "resume/eu-template";
            case "ASIA" -> "resume/asia-template";
            default     -> "resume/us-template";
        };

        String html = templateEngine.process(templateName, ctx);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, baseUrl + "/");
            builder.toStream(baos);
            builder.run();
            log.info("Resume PDF ({}) generated for user: {}", standard, user.getEmail());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed for user {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("PDF generation failed.", e);
        }
    }

    // backwards compat
    @Transactional(readOnly = true)
    public byte[] generateResumePdf(AppUser user, String lang) {
        return generateResumePdf(user, lang, "US");
    }
}