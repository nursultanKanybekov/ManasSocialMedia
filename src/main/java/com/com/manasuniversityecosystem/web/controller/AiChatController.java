package com.com.manasuniversityecosystem.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AiChatController — conversational AI endpoint backed by Gemini.
 *
 * POST /api/ai/chat
 * Body:  { "messages": [{"role":"user","content":"..."}] }
 * Reply: { "reply": "...", "model": "gemini-..." }
 *        { "error": "..." }   on failure (always HTTP 200 so JS handles it gracefully)
 */
@RestController
@RequestMapping("/api/ai")
@Slf4j
public class AiChatController {

    private final ObjectMapper objectMapper;

    @Value("${app.gemini.api-key}")
    private String geminiApiKey;

    // Up-to-date model names — ordered fastest/cheapest first
    private static final String[] GEMINI_MODELS = {
            "gemini-2.0-flash",
            "gemini-2.0-flash-lite",
            "gemini-1.5-flash-8b",
            "gemini-1.5-flash"
    };

    private static final String GEMINI_URL_TEMPLATE =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String SYSTEM_INSTRUCTION =
            "You are a helpful AI assistant for Manas University students in Bishkek, Kyrgyzstan. " +
                    "Help with academic questions, study tips, career advice, university life, and general knowledge. " +
                    "Be concise, warm, and encouraging. " +
                    "Always reply in the same language the user writes in (English, Russian, Kyrgyz, or Turkish).";

    public AiChatController(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages =
                    (List<Map<String, String>>) body.getOrDefault("messages", List.of());

            if (messages.isEmpty()) {
                return ok(Map.of("error", "No messages provided"));
            }

            List<Map<String, Object>> contents = buildContents(messages);
            String lastError = "Unknown error";

            for (String model : GEMINI_MODELS) {
                try {
                    String reply = callGemini(contents, model);
                    log.info("AI chat: answered via {}", model);
                    return ok(Map.of("reply", reply, "model", model));

                } catch (HttpClientErrorException e) {
                    String respBody = e.getResponseBodyAsString();
                    lastError = "HTTP " + e.getStatusCode() + ": " + respBody;
                    log.warn("AI chat: model={} status={} body={}", model, e.getStatusCode(), respBody);

                } catch (HttpServerErrorException e) {
                    String respBody = e.getResponseBodyAsString();
                    lastError = "HTTP " + e.getStatusCode() + ": " + respBody;
                    log.warn("AI chat: model={} server error={} body={}", model, e.getStatusCode(), respBody);

                } catch (Exception e) {
                    lastError = e.getMessage();
                    log.warn("AI chat: model={} exception: {}", model, e.getMessage());
                }
            }

            log.error("AI chat: all models failed. Last error: {}", lastError);
            return ok(Map.of("error",
                    "The AI service is temporarily unavailable. Please try again in a moment."));

        } catch (Exception e) {
            log.error("AI chat endpoint unexpected error", e);
            return ok(Map.of("error", "Internal error: " + e.getMessage()));
        }
    }

    private List<Map<String, Object>> buildContents(List<Map<String, String>> messages) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (Map<String, String> msg : messages) {
            String role    = msg.getOrDefault("role", "user");
            String content = msg.getOrDefault("content", "");
            String geminiRole = "assistant".equals(role) ? "model" : "user";
            contents.add(Map.of(
                    "role",  geminiRole,
                    "parts", List.of(Map.of("text", content))
            ));
        }
        return contents;
    }

    @SuppressWarnings("unchecked")
    private String callGemini(List<Map<String, Object>> contents, String model) throws Exception {
        RestTemplate rest = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String url = String.format(GEMINI_URL_TEMPLATE, model, geminiApiKey);

        Map<String, Object> reqBody = new LinkedHashMap<>();
        reqBody.put("systemInstruction", Map.of(
                "parts", List.of(Map.of("text", SYSTEM_INSTRUCTION))
        ));
        reqBody.put("contents", contents);
        reqBody.put("generationConfig", Map.of(
                "maxOutputTokens", 1024,
                "temperature",     0.7
        ));

        ResponseEntity<Map> resp = rest.postForEntity(
                url, new HttpEntity<>(reqBody, headers), Map.class
        );

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null)
            throw new RuntimeException("Gemini non-2xx: " + resp.getStatusCode());

        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) resp.getBody().get("candidates");

        if (candidates == null || candidates.isEmpty()) {
            Object feedback = resp.getBody().get("promptFeedback");
            throw new RuntimeException("No candidates. promptFeedback=" + feedback);
        }

        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) throw new RuntimeException("Candidate has no content field");

        List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty()) throw new RuntimeException("Content has no parts");

        return (String) parts.get(0).get("text");
    }

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> body) {
        return ResponseEntity.ok(body);
    }
}