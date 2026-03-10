package com.com.manasuniversityecosystem.service;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.career.JobApplication;
import com.com.manasuniversityecosystem.domain.entity.gamification.UserBadge;
import com.com.manasuniversityecosystem.repository.career.JobApplicationRepository;
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
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Slf4j
public class ResumeExportService {

    private final TemplateEngine templateEngine;
    private final UserBadgeRepository userBadgeRepo;
    private final JobApplicationRepository applicationRepo;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Generates a PDF resume for the given user.
     * Uses Thymeleaf to render an HTML template, then converts to PDF via OpenHTMLtoPDF.
     */
    @Transactional(readOnly = true)
    public byte[] generateResumePdf(AppUser user, String lang) {
        Profile profile = user.getProfile();
        List<UserBadge> badges = userBadgeRepo.findByUserIdWithBadge(user.getId());
        List<JobApplication> applications = applicationRepo.findByApplicantIdWithJob(user.getId());

        // Build Thymeleaf context
        Context ctx = new Context(Locale.forLanguageTag(lang));
        ctx.setVariable("user", user);
        ctx.setVariable("profile", profile);
        ctx.setVariable("badges", badges);
        ctx.setVariable("applications", applications);
        ctx.setVariable("lang", lang);
        ctx.setVariable("baseUrl", baseUrl);

        // Render HTML template
        String html = templateEngine.process("resume/pdf-template", ctx);

        // Convert HTML → PDF
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, baseUrl + "/");
            builder.toStream(baos);
            builder.run();
            log.info("Resume PDF generated for user: {}", user.getEmail());
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate PDF for user {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("PDF generation failed.", e);
        }
    }
}
