package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.ErrorCode;
import com.com.manasuniversityecosystem.service.ai.GeminiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST API controller for AI chat — backed by GeminiService (Groq-first, Gemini fallback).
 */
@RestController
@RequestMapping("/api/v1/ai")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "AI Assistant", description = "Groq/Gemini-powered chat assistant and AI quiz generation")
@RequiredArgsConstructor
@Slf4j
public class ApiAiController {

    private final GeminiService geminiService;   // Groq-first, Gemini fallback

    private static final String SYSTEM_PROMPT =
            "You are a helpful AI assistant for Manas University, Bishkek, Kyrgyzstan. " +
                    "Help students with academic questions, study tips, career advice, and general knowledge. " +
                    "Be concise and encouraging. Reply in the same language as the user (EN/RU/KY/TR).";

    // ══ DTOs ══════════════════════════════════════════════════════
    public record ChatMessage(@NotBlank String role, @NotBlank String content) {}
    public record ChatRequest(@NotEmpty List<ChatMessage> messages) {}
    public record ChatResponse(String reply) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @PostMapping("/chat")
    @Operation(
            summary = "Send a message to the Manas AI assistant (Groq-powered, Gemini fallback)",
            description = "Pass full conversation history each request. role = 'user' or 'assistant'.")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(
            @Valid @RequestBody ChatRequest req) {

        try {
            // Convert DTOs to the format GeminiService expects
            List<Map<String, String>> messages = req.messages().stream()
                    .map(m -> Map.of("role", m.role(), "content", m.content()))
                    .toList();

            // GeminiService tries Groq first, then Gemini automatically
            String reply = geminiService.generate(SYSTEM_PROMPT, messages);
            log.info("API AI chat answered via GeminiService (Groq-first)");
            return ResponseEntity.ok(ApiResponse.ok(new ChatResponse(reply)));

        } catch (Exception e) {
            log.error("API AI chat error: {}", e.getMessage());
            return ResponseEntity.status(503)
                    .body(ApiResponse.error(503, ErrorCode.INTERNAL_ERROR,
                            "AI service unavailable: " + e.getMessage()));
        }
    }
}