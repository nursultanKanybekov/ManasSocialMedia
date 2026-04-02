package com.com.manasuniversityecosystem.service.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * AI service with multi-provider support.
 *
 * Priority order for TEXT requests:
 *  1. Groq  (free tier: 30 req/min, 14,400 req/day, very fast — llama models)
 *  2. Gemini (free tier: 15 req/min, 1,500 req/day — fallback)
 *
 * IMAGE / VISION requests always use Gemini (Groq free-tier has no vision support).
 *
 * Configuration (application.yml or env vars):
 *   app.groq.api-key   → GROQ_API_KEY    (get free at https://console.groq.com)
 *   app.gemini.api-key → GEMINI_API_KEY
 */
@Service
@Slf4j
public class GeminiService {

    // ── Gemini ────────────────────────────────────────────────────
    @Value("${app.gemini.api-key:}")
    private String geminiKey;

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private static final String[] GEMINI_MODELS = {
            "gemini-2.0-flash-lite",
            "gemini-2.0-flash"
    };

    // ── Groq (OpenAI-compatible) ──────────────────────────────────
    @Value("${app.groq.api-key:}")
    private String groqKey;

    private static final String GROQ_URL =
            "https://api.groq.com/openai/v1/chat/completions";

    // Ordered: fastest/cheapest first
    private static final String[] GROQ_MODELS = {
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile"
    };

    // ── Shared ────────────────────────────────────────────────────
    private final RestTemplate rest   = new RestTemplate();
    private final ObjectMapper  mapper;

    public GeminiService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    // ════════════════════════════════════════════════════════════
    // Public API — TEXT
    // ════════════════════════════════════════════════════════════

    /** Standard chat — Groq first, Gemini fallback. */
    public String generate(String systemPrompt, String userPrompt) {
        return generate(systemPrompt,
                List.of(Map.of("role", "user", "content", userPrompt)));
    }

    /** Multi-turn chat — Groq first, Gemini fallback. */
    public String generate(String systemPrompt, List<Map<String, String>> messages) {
        return call(systemPrompt, messages, 1024, 0.7);
    }

    /** Precise/structured output (low temperature) — Groq first, Gemini fallback. */
    public String generatePrecise(String systemPrompt, String userPrompt) {
        return call(systemPrompt,
                List.of(Map.of("role", "user", "content", userPrompt)), 2048, 0.2);
    }

    /**
     * Large structured output — use when the JSON response can be long
     * (e.g. 10-20 flashcards, detailed essay analysis with rewrites).
     * Uses 4096 max tokens so the response is never truncated mid-JSON.
     */
    public String generateLarge(String systemPrompt, String userPrompt) {
        return call(systemPrompt,
                List.of(Map.of("role", "user", "content", userPrompt)), 4096, 0.2);
    }

    // ════════════════════════════════════════════════════════════
    // Public API — IMAGE / VISION  (Gemini only — Groq has no vision)
    // ════════════════════════════════════════════════════════════

    /**
     * Analyse an image together with a text prompt.
     *
     * @param systemPrompt  instruction to the model (may be null)
     * @param userPrompt    text question about the image
     * @param base64Image   base64-encoded image bytes
     * @param mimeType      e.g. "image/jpeg", "image/png", "image/webp"
     * @return model's text response
     */
    public String generateWithImage(String systemPrompt,
                                    String userPrompt,
                                    String base64Image,
                                    String mimeType) {
        if (geminiKey == null || geminiKey.isBlank()) {
            throw new RuntimeException("Gemini API key is not configured. " +
                    "Image analysis requires Gemini (set app.gemini.api-key).");
        }

        // Build a multimodal contents list: [image part, text part]
        List<Map<String, Object>> parts = List.of(
                Map.of("inline_data", Map.of(
                        "mime_type", mimeType,
                        "data",      base64Image)),
                Map.of("text", userPrompt)
        );
        List<Map<String, Object>> contents =
                List.of(Map.of("role", "user", "parts", parts));

        for (String model : GEMINI_MODELS) {
            try {
                String reply = callGemini(systemPrompt, contents, model, 1024, 0.4);
                log.info("Image analysis via Gemini model={}", model);
                return reply;
            } catch (HttpClientErrorException e) {
                int code = e.getStatusCode().value();
                log.warn("Gemini vision error model={} status={}", model, code);
                if (code == 429) sleep(3000);
            } catch (Exception e) {
                log.warn("Gemini vision exception model={}: {}", model, e.getMessage());
            }
        }

        throw new RuntimeException(
                "Image analysis unavailable — Gemini is rate-limited. Please try again shortly.");
    }

    // ════════════════════════════════════════════════════════════
    // Internal dispatcher
    // ════════════════════════════════════════════════════════════

    private String call(String systemPrompt, List<Map<String, String>> messages,
                        int maxTokens, double temperature) {

        // 1. Try Groq first — larger free-tier quota, faster responses
        if (groqKey != null && !groqKey.isBlank()) {
            for (String model : GROQ_MODELS) {
                try {
                    String reply = callGroq(systemPrompt, messages, model, maxTokens, temperature);
                    log.info("AI via Groq model={}", model);
                    return reply;
                } catch (HttpClientErrorException e) {
                    log.warn("Groq error model={} status={}", model, e.getStatusCode().value());
                    if (e.getStatusCode().value() == 429) sleep(2000);
                } catch (Exception e) {
                    log.warn("Groq exception model={}: {}", model, e.getMessage());
                }
            }
            log.warn("All Groq models failed — falling back to Gemini");
        }

        // 2. Fallback: Gemini
        if (geminiKey != null && !geminiKey.isBlank()) {
            List<Map<String, Object>> contents = toGeminiContents(messages);
            for (String model : GEMINI_MODELS) {
                try {
                    String reply = callGemini(systemPrompt, contents, model, maxTokens, temperature);
                    log.info("AI via Gemini model={}", model);
                    return reply;
                } catch (HttpClientErrorException e) {
                    int code = e.getStatusCode().value();
                    log.warn("Gemini error model={} status={}", model, code);
                    if (code == 429) sleep(3000);
                } catch (Exception e) {
                    log.warn("Gemini exception model={}: {}", model, e.getMessage());
                }
            }
        }

        log.error("All AI providers exhausted");
        throw new RuntimeException(
                "AI service is temporarily unavailable — all providers are rate-limited. " +
                        "Please wait 1 minute and try again.");
    }

    // ── Groq call (OpenAI-compatible) ─────────────────────────────

    @SuppressWarnings("unchecked")
    private String callGroq(String systemPrompt, List<Map<String, String>> messages,
                            String model, int maxTokens, double temperature) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqKey);

        List<Map<String, String>> groqMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            groqMessages.add(Map.of("role", "system", "content", systemPrompt));
        }
        groqMessages.addAll(messages);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model",       model);
        body.put("messages",    groqMessages);
        body.put("max_tokens",  maxTokens);
        body.put("temperature", temperature);

        ResponseEntity<Map> resp = rest.postForEntity(
                GROQ_URL, new HttpEntity<>(body, headers), Map.class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null)
            throw new RuntimeException("Groq non-2xx: " + resp.getStatusCode());

        List<Map<String, Object>> choices =
                (List<Map<String, Object>>) resp.getBody().get("choices");
        if (choices == null || choices.isEmpty())
            throw new RuntimeException("Groq: no choices in response");

        Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
        if (msg == null) throw new RuntimeException("Groq: no message in choice");

        return (String) msg.get("content");
    }

    // ── Gemini call ───────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String callGemini(String systemPrompt, List<Map<String, Object>> contents,
                              String model, int maxTokens, double temperature) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = new LinkedHashMap<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            body.put("systemInstruction",
                    Map.of("parts", List.of(Map.of("text", systemPrompt))));
        }
        body.put("contents", contents);
        body.put("generationConfig", Map.of(
                "maxOutputTokens", maxTokens,
                "temperature",     temperature));

        String url = String.format(GEMINI_URL, model, geminiKey);
        ResponseEntity<Map> resp = rest.postForEntity(
                url, new HttpEntity<>(body, headers), Map.class);

        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null)
            throw new RuntimeException("Gemini non-2xx: " + resp.getStatusCode());

        List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) resp.getBody().get("candidates");
        if (candidates == null || candidates.isEmpty())
            throw new RuntimeException("Gemini: no candidates");

        Map<String, Object> content =
                (Map<String, Object>) candidates.get(0).get("content");
        if (content == null) throw new RuntimeException("Gemini: no content");

        List<Map<String, Object>> parts =
                (List<Map<String, Object>>) content.get("parts");
        if (parts == null || parts.isEmpty())
            throw new RuntimeException("Gemini: no parts");

        return (String) parts.get(0).get("text");
    }

    // ── Helpers ───────────────────────────────────────────────────

    private List<Map<String, Object>> toGeminiContents(List<Map<String, String>> messages) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, String> m : messages) {
            String role = "assistant".equals(m.get("role")) ? "model" : "user";
            out.add(Map.of("role",  role,
                    "parts", List.of(Map.of("text",
                            m.getOrDefault("content", "")))));
        }
        return out;
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}