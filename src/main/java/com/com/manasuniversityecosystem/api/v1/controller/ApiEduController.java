// ═══════════════════════════════════════════════════════════════════════════
// Education / Tutorials
// ═══════════════════════════════════════════════════════════════════════════
package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.edu.EduService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/edu")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Education", description = "Tutorials, resources and quizzes")
public class ApiEduController {

    private final EduService  eduService;
    private final UserService userService;

    public record TutorialResponse(
            UUID   id, String title, String description,
            String category, String mediaType, String mediaUrl,
            String authorName, int viewCount, LocalDateTime createdAt
    ) {}

    public record CreateTutorialBody(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 500) String description,
            @Size(max = 100) String category,
            String mediaUrl,
            String mediaType,    // TEXT | VIDEO | AUDIO
            @Size(max = 100_000) String content
    ) {}

    @GetMapping
    @Operation(summary = "List tutorials (paginated, searchable, filterable)")
    public ResponseEntity<ApiResponse<PageResponse<TutorialResponse>>> list(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "12") int    size,
            @RequestParam(required = false)    String q,
            @RequestParam(required = false)    String category,
            @RequestParam(required = false)    String mediaType,
            @RequestParam(defaultValue = "en") String lang) {

        var tutorials = (q != null && !q.isBlank()) || (category != null && !category.isBlank()) || (mediaType != null && !mediaType.isBlank())
                ? eduService.searchTutorials(q, mediaType, category, page)
                : (category != null && !category.isBlank()
                ? eduService.getTutorialsByCategory(category, page, size)
                : eduService.getTutorials(page, size));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                tutorials.map(t -> new TutorialResponse(
                        t.getId(), t.getLocalizedTitle(lang), t.getDescription(),
                        t.getCategory(), t.getMediaType().name(), t.getMediaUrl(),
                        t.getAuthor() != null ? t.getAuthor().getFullName() : null,
                        t.getViewCount(), t.getCreatedAt()
                ))
        )));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get full tutorial detail (increments view count)")
    public ResponseEntity<ApiResponse<TutorialResponse>> get(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "en") String lang) {

        var t = eduService.viewTutorial(id);
        return ResponseEntity.ok(ApiResponse.ok(new TutorialResponse(
                t.getId(), t.getLocalizedTitle(lang), t.getDescription(),
                t.getCategory(), t.getMediaType().name(), t.getMediaUrl(),
                t.getAuthor() != null ? t.getAuthor().getFullName() : null,
                t.getViewCount(), t.getCreatedAt()
        )));
    }

    @PostMapping
    @Operation(summary = "Create a tutorial (MEZUN / ADMIN only)")
    public ResponseEntity<ApiResponse<TutorialResponse>> create(
            @Valid @RequestBody CreateTutorialBody body,
            @RequestParam(defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        com.com.manasuniversityecosystem.domain.entity.edu.Tutorial.MediaType mt =
                com.com.manasuniversityecosystem.domain.entity.edu.Tutorial.MediaType.TEXT;
        if (body.mediaType() != null) {
            try { mt = com.com.manasuniversityecosystem.domain.entity.edu.Tutorial.MediaType
                    .valueOf(body.mediaType().toUpperCase()); } catch (Exception ignored) {}
        }
        var t = eduService.createTutorial(user, body.title(), body.content(),
                body.category(), body.mediaUrl(), mt, body.description());

        return ResponseEntity.status(201).body(ApiResponse.created(new TutorialResponse(
                t.getId(), t.getLocalizedTitle(lang), t.getDescription(),
                t.getCategory(), t.getMediaType().name(), t.getMediaUrl(),
                user.getFullName(), 0, t.getCreatedAt()
        )));
    }

    @GetMapping("/categories")
    @Operation(summary = "List all tutorial categories (for filter UI)")
    public ResponseEntity<ApiResponse<List<String>>> categories() {
        return ResponseEntity.ok(ApiResponse.ok(eduService.getAllCategories()));
    }
}