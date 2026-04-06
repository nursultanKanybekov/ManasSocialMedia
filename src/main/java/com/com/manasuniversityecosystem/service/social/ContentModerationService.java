package com.com.manasuniversityecosystem.service.social;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.List;

/**
 * Two-layer content moderation — Groq only, no Gemini.
 *
 * LAYER 1 (local, instant, no API call):
 *   Keyword blocklist — explicit words blocked before anything leaves the server.
 *   Safe for Groq ToS because bad content never reaches their API.
 *
 * LAYER 2 (Groq API):
 *   Text  → llama-3.3-70b-versatile  (fast, structured JSON output)
 *   Image → llama-3.2-11b-vision-preview (vision model)
 *   Both FAIL OPEN — if Groq is down/slow, post is allowed through.
 *   This prevents users from being blocked due to API issues.
 */
@Service
@Slf4j
public class ContentModerationService {

    private static final String GROQ_URL      = "https://api.groq.com/openai/v1/chat/completions";
    private static final String TEXT_MODEL    = "llama-3.3-70b-versatile";
    private static final String VISION_MODEL  = "llama-3.2-11b-vision-preview";
    private static final int    MAX_IMG_SIDE  = 512;   // px — keep base64 small
    private static final int    TIMEOUT_SEC   = 15;

    // ── Keyword blocklist (Layer 1) ─────────────────────────────────────────
    // These words are caught LOCALLY — they never reach Groq.
    // This is what keeps the Groq account safe.
    private static final List<String> BLOCKED_KEYWORDS = Arrays.asList(
            // EN – sexual
            "porn", "pornography", "pornographic", "xxx", "nude", "nudity",
            "hentai", "onlyfans", "blowjob", "handjob", "cumshot",
            "masturbat", "pedophil", "child porn",
            // EN – adult sites
            "pornhub", "xvideos", "xhamster", "redtube", "youporn", "brazzers",
            // RU / KG – sexual
            "\u043f\u043e\u0440\u043d\u043e",   // порно
            "\u044d\u0440\u043e\u0442\u0438\u043a\u0430", // эротика
            "\u0448\u043b\u044e\u0445\u0430",   // шлюха
            "\u043f\u0440\u043e\u0441\u0442\u0438\u0442\u0443\u0442\u043a", // проститутк
            "\u0438\u043d\u0446\u0435\u0441\u0442",       // инцест
            "\u043f\u0435\u0434\u043e\u0444\u0438\u043b", // педофил
            // TR – sexual
            "porno", "erotik", "orospu",
            // Hate / violence
            "nigger", "faggot", "kill yourself", "kys"
    );

    // "секс" is deliberately NOT in the blocklist — it appears in academic/health
    // contexts. The Groq model handles it contextually in Layer 2.

    @Value("${app.groq.api-key:}")
    private String groqKey;

    private final HttpClient   http   = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public record ModerationResult(boolean allowed, String reason) {
        public static ModerationResult ok()              { return new ModerationResult(true,  null); }
        public static ModerationResult block(String msg) { return new ModerationResult(false, msg); }
    }

    @PostConstruct
    void init() {
        boolean hasKey = groqKey != null && !groqKey.isBlank() && !groqKey.startsWith("${");
        log.info("MODERATION: ready — Layer1=keywords, Layer2=Groq({})",
                hasKey ? "key=" + groqKey.substring(0, 8) + "..." : "NO KEY — text-only keyword filter");
    }

    // ── Public API ──────────────────────────────────────────────────────────

    public ModerationResult moderateText(String text) {
        if (text == null || text.isBlank()) return ModerationResult.ok();

        // Layer 1: keyword check — local, no API
        ModerationResult kw = keywordCheck(text);
        if (!kw.allowed()) {
            log.info("MODERATION [L1-TEXT]: blocked by keyword");
            return kw;
        }

        // Layer 2: Groq — fail OPEN so normal posts are never blocked by API issues
        if (groqKey == null || groqKey.isBlank()) return ModerationResult.ok();
        log.debug("MODERATION [L2-TEXT]: sending to Groq ({} chars)", text.length());
        try {
            return callGroq(buildTextBody(text));
        } catch (Exception e) {
            log.warn("MODERATION [L2-TEXT]: Groq unavailable — allowing post: {}", e.getMessage());
            return ModerationResult.ok(); // fail open
        }
    }

    public ModerationResult moderateTextAndImage(String text, byte[] imageBytes, String mimeType) {
        // Layer 1: always check text keywords first (never send bad text to API)
        if (text != null && !text.isBlank()) {
            ModerationResult kw = keywordCheck(text);
            if (!kw.allowed()) {
                log.info("MODERATION [L1-IMAGE]: text blocked by keyword, image not sent to API");
                return kw;
            }
        }

        if (imageBytes == null || imageBytes.length == 0) return moderateText(text);

        // Layer 2: Groq vision — fail OPEN (image API is less reliable; don't block legit posts)
        if (groqKey == null || groqKey.isBlank()) return ModerationResult.ok();
        log.debug("MODERATION [L2-IMAGE]: sending to Groq vision ({} bytes raw)", imageBytes.length);
        try {
            byte[] small  = resizeImage(imageBytes, MAX_IMG_SIDE);
            String b64    = Base64.getEncoder().encodeToString(small);
            String dataUri = "data:image/jpeg;base64," + b64;
            log.debug("MODERATION [L2-IMAGE]: resized to {} bytes", small.length);
            return callGroq(buildVisionBody(text, dataUri));
        } catch (Exception e) {
            log.warn("MODERATION [L2-IMAGE]: Groq vision unavailable — falling back to text-only: {}", e.getMessage());
            return moderateText(text); // fall back to text-only, still fail open
        }
    }

    // ── Layer 1 ─────────────────────────────────────────────────────────────

    private ModerationResult keywordCheck(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : BLOCKED_KEYWORDS) {
            if (lower.contains(kw)) {
                return ModerationResult.block(
                        "Your post contains content that is not allowed on this platform.");
            }
        }
        return ModerationResult.ok();
    }

    // ── Layer 2: Groq ────────────────────────────────────────────────────────

    private static final String SYSTEM_PROMPT =
            "You are a content moderation system for a university social platform.\n" +
                    "Analyse the post and respond ONLY with a raw JSON object — no markdown.\n\n" +
                    "BLOCK if the post contains:\n" +
                    "- Sexually explicit or 18+ content\n" +
                    "- Graphic violence or gore\n" +
                    "- Hate speech or discrimination\n" +
                    "- Severe harassment\n" +
                    "- Spam or scam links\n\n" +
                    "Respond with exactly one of:\n" +
                    "{\"allowed\":true}\n" +
                    "{\"allowed\":false,\"reason\":\"brief reason under 100 chars\"}";

    private String buildTextBody(String text) throws Exception {
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user",   "content", "Post text:\n\n" + text)
        );
        return mapper.writeValueAsString(Map.of(
                "model",           TEXT_MODEL,
                "messages",        messages,
                "max_tokens",      120,
                "temperature",     0,
                "response_format", Map.of("type", "json_object")
        ));
    }

    private String buildVisionBody(String text, String dataUri) throws Exception {
        List<Map<String, Object>> contentBlocks = new ArrayList<>();
        contentBlocks.add(Map.of(
                "type",      "image_url",
                "image_url", Map.of("url", dataUri)
        ));
        contentBlocks.add(Map.of(
                "type", "text",
                "text", "Post text: " + (text != null ? text : "(none)") +
                        "\n\nCheck BOTH the image and text. Reply with raw JSON only."
        ));
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user",   "content", contentBlocks)
        );
        // vision model does NOT support response_format json_object
        return mapper.writeValueAsString(Map.of(
                "model",       VISION_MODEL,
                "messages",    messages,
                "max_tokens",  120,
                "temperature", 0
        ));
    }

    private ModerationResult callGroq(String jsonBody) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(GROQ_URL))
                .header("Authorization", "Bearer " + groqKey)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(TIMEOUT_SEC))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (resp.statusCode() != 200) {
            log.warn("MODERATION: Groq HTTP {} — failing open. Body: {}",
                    resp.statusCode(), resp.body().substring(0, Math.min(200, resp.body().length())));
            return ModerationResult.ok(); // fail open on any API error
        }

        JsonNode root    = mapper.readTree(resp.body());
        String   content = root.path("choices").get(0)
                .path("message").path("content").asText("{}").trim();

        // Strip markdown fences if present
        content = content.replaceAll("(?s)```json\\s*", "").replaceAll("```", "").trim();
        log.debug("MODERATION: Groq replied: {}", content);

        try {
            JsonNode result  = mapper.readTree(content);
            boolean  allowed = result.path("allowed").asBoolean(true); // default true = fail open
            if (allowed) return ModerationResult.ok();
            String reason = result.path("reason").asText("Your post violates our community guidelines.");
            log.info("MODERATION: BLOCKED — {}", reason);
            return ModerationResult.block(reason);
        } catch (Exception e) {
            log.warn("MODERATION: could not parse Groq response '{}' — failing open", content);
            return ModerationResult.ok(); // fail open on parse error
        }
    }

    // ── Image resize ─────────────────────────────────────────────────────────

    private byte[] resizeImage(byte[] original, int maxSide) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(original));
            if (src == null) return original;
            int w = src.getWidth(), h = src.getHeight();
            double scale = (double) maxSide / Math.max(w, h);
            if (scale >= 1.0) return toJpeg(src);
            int nw = (int)(w * scale), nh = (int)(h * scale);
            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            return toJpeg(dst);
        } catch (Exception e) {
            log.warn("MODERATION: image resize failed: {}", e.getMessage());
            return original;
        }
    }

    private byte[] toJpeg(BufferedImage img) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", out);
        return out.toByteArray();
    }
}