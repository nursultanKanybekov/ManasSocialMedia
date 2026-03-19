package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.social.Post;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.ProfileService;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.web.dto.profile.ProfileUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Profile", description = "View and edit user profiles, avatar upload, posts")
public class ApiProfileController {

    private final UserService    userService;
    private final ProfileService profileService;
    private final PostService    postService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record ProfileResponse(
            UUID                  id,
            String                fullName,
            String                role,
            String                email,
            String                facultyName,
            Integer               graduationYear,
            Integer               studyYear,
            boolean               universityVerified,
            String                headline,
            String                bio,
            String                avatarUrl,
            String                currentJobTitle,
            String                currentCompany,
            String                location,
            String                website,
            String                phone,
            List<String>          skills,
            int                   totalPoints,
            Integer               rankPosition,
            boolean               canMentor,
            Map<String, String>   socialLinks,
            List<Map<String,String>> workExperience,
            List<Map<String,String>> educationList,
            List<Map<String,String>> certifications,
            List<Map<String,String>> languages,
            LocalDateTime         updatedAt
    ) {}

    public record UpdateProfileBody(
            @Size(min = 2, max = 100) String fullName,
            @Size(max = 200)          String headline,
            @Size(max = 2000)         String bio,
            String currentJobTitle,
            String currentCompany,
            String location,
            String website,
            String phone,
            String nationality,
            String dateOfBirth,
            Integer studyYear,
            Boolean canMentor,
            String  mentorJobTitle,
            List<String>             skills,
            Map<String, String>      socialLinks,
            List<Map<String,String>> workExperience,
            List<Map<String,String>> educationList,
            List<Map<String,String>> certifications,
            List<Map<String,String>> languages
    ) {}

    public record AvatarResponse(String avatarUrl) {}

    public record PostSummary(
            UUID          id,
            String        content,
            String        imageUrl,
            String        postType,
            int           likesCount,
            int           commentsCount,
            LocalDateTime createdAt
    ) {}

    // ══ Endpoints ════════════════════════════════════════════════

    @GetMapping("/me")
    @Operation(summary = "Get own full profile")
    public ResponseEntity<ApiResponse<ProfileResponse>> getMyProfile(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get any user's public profile by ID")
    public ResponseEntity<ApiResponse<ProfileResponse>> getProfile(@PathVariable UUID id) {
        AppUser user = userService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(user)));
    }

    @PutMapping("/me")
    @Operation(summary = "Update own profile fields")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());

        // Map record → existing DTO (reuses service layer without changes)
        ProfileUpdateRequest req = new ProfileUpdateRequest();
        if (body.fullName()       != null) req.setFullName(body.fullName());
        if (body.headline()       != null) req.setHeadline(body.headline());
        if (body.bio()            != null) req.setBio(body.bio());
        if (body.currentJobTitle()!= null) req.setCurrentJobTitle(body.currentJobTitle());
        if (body.currentCompany() != null) req.setCurrentCompany(body.currentCompany());
        if (body.location()       != null) req.setLocation(body.location());
        if (body.website()        != null) req.setWebsite(body.website());
        if (body.phone()          != null) req.setPhone(body.phone());
        if (body.studyYear()      != null) req.setStudyYear(body.studyYear());
        if (body.canMentor()      != null) req.setCanMentor(body.canMentor());
        if (body.mentorJobTitle() != null) req.setMentorJobTitle(body.mentorJobTitle());
        if (body.skills()         != null) req.setSkills(body.skills());
        if (body.socialLinks()    != null) req.setSocialLinks(body.socialLinks());

        profileService.update(user, req);
        return ResponseEntity.ok(ApiResponse.ok(toResponse(userService.getById(principal.getId()))));
    }

    @PostMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a new avatar image (max 5 MB, jpg/png/webp)")
    public ResponseEntity<ApiResponse<AvatarResponse>> uploadAvatar(
            @RequestPart("avatar") MultipartFile file,
            @AuthenticationPrincipal UserDetailsImpl principal) throws java.io.IOException {

        AppUser user = userService.getById(principal.getId());
        String url = profileService.uploadAvatar(user, file);
        return ResponseEntity.ok(ApiResponse.ok(new AvatarResponse(url)));
    }

    @GetMapping("/{id}/posts")
    @Operation(summary = "Get posts by a specific user (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<PostSummary>>> getUserPosts(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(defaultValue = "en") String lang) {

        Page<Post> posts = postService.getUserPosts(id, page, size);
        Page<PostSummary> mapped = posts.map(p -> new PostSummary(
                p.getId(), p.getLocalizedContent(lang), p.getImageUrl(),
                p.getPostType().name(), p.getLikesCount(), p.getCommentsCount(), p.getCreatedAt()
        ));
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(mapped)));
    }

    // ══ Mapper ═══════════════════════════════════════════════════

    private ProfileResponse toResponse(AppUser u) {
        Profile p = u.getProfile();
        return new ProfileResponse(
                u.getId(), u.getFullName(), u.getRole().name(), u.getEmail(),
                u.getFaculty() != null ? u.getFaculty().getName() : null,
                u.getGraduationYear(),
                p != null ? p.getStudyYear() : null,
                u.isUniversityVerified(),
                p != null ? p.getHeadline() : null,
                p != null ? p.getBio() : null,
                p != null ? p.getAvatarUrl() : null,
                p != null ? p.getCurrentJobTitle() : null,
                p != null ? p.getCurrentCompany() : null,
                p != null ? p.getLocation() : null,
                p != null ? p.getWebsite() : null,
                p != null ? p.getPhone() : null,
                p != null ? p.getSkills() : List.of(),
                p != null ? p.getTotalPoints() : 0,
                p != null ? p.getRankPosition() : null,
                p != null && Boolean.TRUE.equals(p.getCanMentor()),
                p != null ? p.getSocialLinks() : Map.of(),
                p != null ? p.getWorkExperience() : List.of(),
                p != null ? p.getEducationList() : List.of(),
                p != null ? p.getCertifications() : List.of(),
                p != null ? p.getLanguages() : List.of(),
                p != null ? p.getUpdatedAt() : null
        );
    }
}