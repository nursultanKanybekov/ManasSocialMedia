package com.com.manasuniversityecosystem.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Validates Google reCAPTCHA v2 tokens server-side.
 * Calls https://www.google.com/recaptcha/api/siteverify
 */
@Service
@Slf4j
public class RecaptchaService {

    @Value("${app.recaptcha.secret-key}")
    private String secretKey;

    @Value("${app.recaptcha.verify-url}")
    private String verifyUrl;

    /**
     * Returns true if the reCAPTCHA token is valid.
     * Returns true automatically if secret key is the Google test key
     * (so dev environment always passes).
     */
    public boolean verify(String token) {
        if (token == null || token.isBlank()) {
            log.warn("reCAPTCHA token missing");
            return false;
        }
        try {
            RestTemplate rest = new RestTemplate();
            String url = verifyUrl + "?secret=" + secretKey + "&response=" + token;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = rest.postForObject(url, null, Map.class);

            if (response == null) return false;

            Boolean success = (Boolean) response.get("success");
            if (!Boolean.TRUE.equals(success)) {
                log.warn("reCAPTCHA failed: {}", response.get("error-codes"));
                return false;
            }
            return true;
        } catch (Exception e) {
            log.error("reCAPTCHA verification error", e);
            return false;
        }
    }
}