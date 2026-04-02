package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.service.ai.GeminiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AiChatController — conversational AI endpoint.
 *
 * Delegates to GeminiService which tries Groq first (llama-3.x models),
 * then falls back to Gemini automatically.
 *
 * POST /api/ai/chat
 * Body:  { "messages": [{"role":"user","content":"..."}] }
 * Reply: { "reply": "..." }
 *        { "error": "..." }  on failure (always HTTP 200 so JS handles it gracefully)
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiChatController {

    private final GeminiService geminiService;   // Groq-first, Gemini fallback
    private final ObjectMapper  objectMapper;

    private static final String SYSTEM_INSTRUCTION =
            "You are a helpful AI assistant for Manas University students in Bishkek, Kyrgyzstan. " +
                    "Help with academic questions, study tips, career advice, university life, and general knowledge. " +
                    "Be concise, warm, and encouraging. " +
                    "Always reply in the same language the user writes in (English, Russian, Kyrgyz, or Turkish).";

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages =
                    (List<Map<String, String>>) body.getOrDefault("messages", List.of());

            if (messages.isEmpty()) {
                return ok(Map.of("error", "No messages provided"));
            }

            // GeminiService.generate() tries Groq first, then Gemini automatically
            String reply = geminiService.generate(SYSTEM_INSTRUCTION, messages);
            log.info("AI chat answered via GeminiService (Groq-first)");
            return ok(Map.of("reply", reply));

        } catch (Exception e) {
            log.error("AI chat endpoint error: {}", e.getMessage());
            return ok(Map.of("error",
                    "The AI service is temporarily unavailable. Please try again in a moment."));
        }
    }

    private ResponseEntity<Map<String, Object>> ok(Map<String, Object> b) {
        return ResponseEntity.ok(b);
    }
}