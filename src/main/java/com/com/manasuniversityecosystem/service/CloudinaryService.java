package com.com.manasuniversityecosystem.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class CloudinaryService {

    private final Cloudinary cloudinary;

    public CloudinaryService(
            @Value("${app.cloudinary.cloud-name}") String cloudName,
            @Value("${app.cloudinary.api-key}")    String apiKey,
            @Value("${app.cloudinary.api-secret}") String apiSecret) {

        this.cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key",    apiKey,
                "api_secret", apiSecret,
                "secure",     true
        ));
        log.info("Cloudinary configured — cloud: {}", cloudName);
    }

    /**
     * Uploads an image file to Cloudinary and returns the secure URL.
     *
     * @param file     the multipart image file
     * @param folder   Cloudinary folder path (e.g. "manas/avatars")
     * @param publicId optional stable asset ID (null = auto-generate)
     */
    @SuppressWarnings("unchecked")
    public String uploadImage(MultipartFile file, String folder, String publicId) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is empty.");
        }
        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new IllegalArgumentException("Only image files are allowed.");
        }

        Map<String, Object> options = new HashMap<>();
        options.put("folder",        folder);
        options.put("overwrite",     true);
        options.put("resource_type", "image");

        // Transformation must be a Transformation object, NOT a plain Map
        options.put("transformation", new Transformation()
                .quality("auto")
                .fetchFormat("auto"));

        if (publicId != null) {
            options.put("public_id", publicId);
        }

        Map<String, Object> result = (Map<String, Object>)
                cloudinary.uploader().upload(file.getBytes(), options);

        String url = (String) result.get("secure_url");
        log.info("Cloudinary upload OK — folder={} url={}", folder, url);
        return url;
    }

    /**
     * Uploads any file (image or PDF) as a raw resource.
     * Used for employment proof documents.
     */
    @SuppressWarnings("unchecked")
    public String uploadDocument(MultipartFile file, String folder) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty.");
        Map<String, Object> options = new HashMap<>();
        options.put("folder",        folder);
        options.put("resource_type", "auto");  // handles images + PDFs
        Map<String, Object> result = (Map<String, Object>)
                cloudinary.uploader().upload(file.getBytes(), options);
        String url = (String) result.get("secure_url");
        log.info("Cloudinary doc upload OK — folder={} url={}", folder, url);
        return url;
    }

    /** Soft-deletes an asset from Cloudinary (ignores errors). */
    public void deleteImage(String publicId) {
        try {
            cloudinary.uploader().destroy(publicId, ObjectUtils.emptyMap());
            log.info("Cloudinary delete OK — {}", publicId);
        } catch (Exception e) {
            log.warn("Cloudinary delete failed for {}: {}", publicId, e.getMessage());
        }
    }
}