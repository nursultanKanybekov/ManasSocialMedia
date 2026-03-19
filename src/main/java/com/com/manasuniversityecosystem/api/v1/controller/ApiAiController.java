package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
@RequestMapping("/api/v1/ai")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Assistant", description = "Gemini-powered chat assistant and AI quiz generation")
public class ApiAiController {

    @Value("${app.gemini.api-key:}")
    private String geminiApiKey;

    private static final String[] GEMINI_MODELS = {
            "gemini-1.5-flash", "gemini-1.5-pro", "gemini-pro"
    };
    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s";

    private final RestTemplate   restTemplate;
    private final ObjectMapper   objectMapper;

    public ApiAiController(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    // ══ DTOs ══════════════════════════════════════════════════════

    public record ChatMessage(@NotBlank String role, @NotBlank String content) {}
    public record ChatRequest(@NotEmpty List<ChatMessage> messages) {}
    public record ChatResponse(String reply) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @PostMapping("/chat")
    @Operation(
            summary = "Send a message to the Manas AI assistant (Gemini-powered)",
            description = "Pass full conversation history each request. role = \'user\' or \'model\'.")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest req) {

        List<Map<String, Object>> contents = req.messages().stream()
                .map(m -> Map.<String, Object>of(
                        "role",  "assistant".equals(m.role()) ? "model" : "user",
                        "parts", List.of(Map.of("text", m.content()))
                ))
                .toList();

        String lastError = "Gemini unavailable";
        for (String model : GEMINI_MODELS) {
            try {
                String url  = String.format(GEMINI_URL, model, geminiApiKey);
                Map<String, Object> body = Map.of("contents", contents);
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                @SuppressWarnings("unchecked")
                Map<String, Object> resp = restTemplate.postForObject(
                        url, new HttpEntity<>(body, headers), Map.class);
                if (resp != null && resp.containsKey("candidates")) {
                    @SuppressWarnings("unchecked")
                    var candidates = (List<Map<String, Object>>) resp.get("candidates");
                    @SuppressWarnings("unchecked")
                    var content = (Map<String, Object>) candidates.get(0).get("content");
                    @SuppressWarnings("unchecked")
                    var parts = (List<Map<String, Object>>) content.get("parts");
                    String reply = (String) parts.get(0).get("text");
                    return ResponseEntity.ok(ApiResponse.ok(new ChatResponse(reply)));
                }
            } catch (Exception e) { lastError = e.getMessage(); }
        }
        return ResponseEntity.status(503)
                .body(ApiResponse.error(503, com.com.manasuniversityecosystem.api.v1.common.ErrorCode.INTERNAL_ERROR,
                        "AI service unavailable: " + lastError));
    }
}