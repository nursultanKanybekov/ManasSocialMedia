package com.com.manasuniversityecosystem.api.v1.controller;

import com.com.manasuniversityecosystem.api.v1.common.ApiResponse;
import com.com.manasuniversityecosystem.api.v1.common.PageResponse;
import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.career.MentorshipRequest;
import com.com.manasuniversityecosystem.domain.enums.JobType;
import com.com.manasuniversityecosystem.repository.ProfileRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.career.JobService;
import com.com.manasuniversityecosystem.service.career.MentorshipService;
import com.com.manasuniversityecosystem.service.ProfileService;
import com.com.manasuniversityecosystem.web.dto.career.CreateJobRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/career")
@RequiredArgsConstructor
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Career", description = "Job listings, applications and mentorship")
public class ApiCareerController {

    private final JobService         jobService;
    private final MentorshipService  mentorshipService;
    private final UserService        userService;
    private final ProfileRepository  profileRepo;
    private final ProfileService     profileService;

    // ══ DTOs ══════════════════════════════════════════════════════

    public record JobResponse(
            UUID id, String title, String company, String location,
            String jobType, String description, String salary,
            boolean isActive, int applicationCount, LocalDateTime createdAt
    ) {}

    public record CreateJobBody(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Size(max = 200) String company,
            @Size(max = 100)           String location,
            String jobType,
            @Size(max = 5000)          String description,
            String salary,
            LocalDate deadline
    ) {}

    public record ApplyBody(@Size(max = 3000) String coverLetter) {}

    public record ApplicationResponse(
            UUID id, UUID jobId, String jobTitle, String status, LocalDateTime appliedAt
    ) {}

    public record MentorResponse(
            UUID id, String fullName, String avatarUrl, String headline,
            String faculty, Integer graduationYear, List<String> skills, int totalPoints
    ) {}

    public record MentorshipRequestBody(
            @NotNull UUID mentorId,
            @Size(max = 200)  String topic,
            @Size(max = 1000) String message
    ) {}

    public record MentorshipResponse(
            UUID id, String mentorName, String studentName,
            String topic, String status, LocalDateTime createdAt
    ) {}

    // ══ Jobs ═════════════════════════════════════════════════════

    @GetMapping("/jobs")
    @Operation(summary = "List active job postings (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<JobResponse>>> listJobs(
            @RequestParam(defaultValue = "0")       int    page,
            @RequestParam(defaultValue = "10")      int    size,
            @RequestParam(required = false)         String type,
            @RequestParam(defaultValue = "newest")  String sort,
            @RequestParam(defaultValue = "en")      String lang) {

        var jobs = jobService.getActiveJobs(page, size, sort);

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                jobs.map(j -> new JobResponse(
                        j.getId(),
                        j.getLocalizedTitle(lang),
                        j.getPostedBy() != null ? j.getPostedBy().getFullName() : null,
                        j.getLocation(),
                        j.getJobType() != null ? j.getJobType().name() : null,
                        j.getLocalizedDescription(lang),
                        j.getSalaryRange(),
                        Boolean.TRUE.equals(j.getIsActive()),
                        j.getApplications().size(),
                        j.getCreatedAt()
                ))
        )));
    }

    @GetMapping("/jobs/{id}")
    @Operation(summary = "Get a single job detail")
    public ResponseEntity<ApiResponse<JobResponse>> getJob(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "en") String lang) {

        var j = jobService.getById(id);
        return ResponseEntity.ok(ApiResponse.ok(new JobResponse(
                j.getId(),
                j.getLocalizedTitle(lang),
                j.getPostedBy() != null ? j.getPostedBy().getFullName() : null,
                j.getLocation(),
                j.getJobType() != null ? j.getJobType().name() : null,
                j.getLocalizedDescription(lang),
                j.getSalaryRange(),
                Boolean.TRUE.equals(j.getIsActive()),
                j.getApplications().size(),
                j.getCreatedAt()
        )));
    }

    @PostMapping("/jobs")
    @Operation(summary = "Create a job posting (EMPLOYER / MEZUN / ADMIN only)")
    public ResponseEntity<ApiResponse<JobResponse>> createJob(
            @Valid @RequestBody CreateJobBody body,
            @RequestParam(defaultValue = "en") String lang,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());

        CreateJobRequest req = new CreateJobRequest();
        // Wrap the plain-text title and description into i18n maps keyed by lang
        req.setTitleI18n(Map.of(lang, body.title()));
        req.setDescriptionI18n(Map.of(lang, body.description() != null ? body.description() : ""));
        req.setJobType(body.jobType() != null ? JobType.valueOf(body.jobType()) : null);
        req.setLocation(body.location());
        req.setSalaryRange(body.salary());
        req.setDeadline(body.deadline());

        var j = jobService.createJob(user, req);
        return ResponseEntity.status(201).body(ApiResponse.created(new JobResponse(
                j.getId(),
                j.getLocalizedTitle(lang),
                j.getPostedBy() != null ? j.getPostedBy().getFullName() : null,
                j.getLocation(),
                j.getJobType() != null ? j.getJobType().name() : null,
                j.getLocalizedDescription(lang),
                j.getSalaryRange(),
                true,
                0,
                j.getCreatedAt()
        )));
    }

    @PostMapping("/jobs/{id}/apply")
    @Operation(summary = "Apply for a job")
    public ResponseEntity<ApiResponse<ApplicationResponse>> applyForJob(
            @PathVariable UUID id,
            @Valid @RequestBody ApplyBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        var app = jobService.apply(user, id, body.coverLetter());
        return ResponseEntity.status(201).body(ApiResponse.created(new ApplicationResponse(
                app.getId(), id,
                app.getJob().getLocalizedTitle("en"),
                app.getStatus().name(), app.getAppliedAt()
        )));
    }

    @PostMapping("/jobs/{id}/close")
    @Operation(summary = "Close a job posting (owner / admin)")
    public ResponseEntity<ApiResponse<Void>> closeJob(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        jobService.closeJob(id, userService.getById(principal.getId()));
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @GetMapping("/my-applications")
    @Operation(summary = "Get current user's job applications")
    public ResponseEntity<ApiResponse<List<ApplicationResponse>>> myApplications(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        var result = jobService.getMyApplications(principal.getId()).stream()
                .map(a -> new ApplicationResponse(
                        a.getId(), a.getJob().getId(),
                        a.getJob().getLocalizedTitle("en"),
                        a.getStatus().name(), a.getAppliedAt()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ══ Mentorship ════════════════════════════════════════════════

    @GetMapping("/mentors")
    @Operation(summary = "List available mentors (paginated)")
    public ResponseEntity<ApiResponse<PageResponse<MentorResponse>>> listMentors(
            @RequestParam(defaultValue = "0")  int    page,
            @RequestParam(defaultValue = "20") int    size,
            @RequestParam(required = false)    String search,
            @RequestParam(required = false)    String sortBy) {

        List<Profile> all = profileRepo.findAvailableMentors();

        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            all = all.stream()
                    .filter(p -> p.getUser().getFullName().toLowerCase().contains(q)
                            || (p.getMentorJobTitle() != null
                            && p.getMentorJobTitle().toLowerCase().contains(q))
                            || p.getSkills().stream().anyMatch(s -> s.toLowerCase().contains(q)))
                    .toList();
        }

        if ("points".equals(sortBy))
            all = all.stream().sorted((a, b) -> Integer.compare(b.getTotalPoints(), a.getTotalPoints())).toList();
        else if ("name".equals(sortBy))
            all = all.stream().sorted((a, b) -> a.getUser().getFullName().compareTo(b.getUser().getFullName())).toList();

        int from = page * size, to = Math.min(from + size, all.size());
        List<Profile> slice = from >= all.size() ? List.of() : all.subList(from, to);
        var paged = new PageImpl<>(slice, PageRequest.of(page, size), all.size());

        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(
                paged.map(p -> new MentorResponse(
                        p.getUser().getId(), p.getUser().getFullName(),
                        p.getAvatarUrl(), p.getHeadline(),
                        p.getUser().getFaculty() != null ? p.getUser().getFaculty().getName() : null,
                        p.getUser().getGraduationYear(), p.getSkills(), p.getTotalPoints()
                ))
        )));
    }

    @PostMapping("/mentorship/request")
    @Operation(summary = "Send a mentorship request to an alumnus")
    public ResponseEntity<ApiResponse<MentorshipResponse>> requestMentorship(
            @Valid @RequestBody MentorshipRequestBody body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser student = userService.getById(principal.getId());
        MentorshipRequest req = mentorshipService.sendRequest(
                student, body.mentorId(), body.topic(), body.message());

        return ResponseEntity.status(201).body(ApiResponse.created(new MentorshipResponse(
                req.getId(), req.getMentor().getFullName(),
                req.getStudent().getFullName(), req.getTopic(),
                req.getStatus().name(), req.getCreatedAt()
        )));
    }

    @PostMapping("/mentorship/{id}/respond")
    @Operation(summary = "Accept or decline a mentorship request (MEZUN only)")
    public ResponseEntity<ApiResponse<Void>> respond(
            @PathVariable UUID id,
            @RequestParam boolean accept,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        mentorshipService.respond(id, userService.getById(principal.getId()), accept);
        return ResponseEntity.ok(ApiResponse.noContent());
    }

    @GetMapping("/mentorship/my-requests")
    @Operation(summary = "Get my mentorship requests (sent as student, received as mentor)")
    public ResponseEntity<ApiResponse<List<MentorshipResponse>>> myRequests(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        AppUser user = userService.getById(principal.getId());
        List<MentorshipRequest> list = user.getRole().name().equals("STUDENT")
                ? mentorshipService.getStudentRequests(user)
                : mentorshipService.getPendingForMentor(user);

        return ResponseEntity.ok(ApiResponse.ok(list.stream()
                .map(r -> new MentorshipResponse(
                        r.getId(), r.getMentor().getFullName(),
                        r.getStudent().getFullName(), r.getTopic(),
                        r.getStatus().name(), r.getCreatedAt()))
                .toList()));
    }

    @PostMapping("/mentorship/toggle-availability")
    @Operation(summary = "Toggle own mentorship availability (MEZUN only)")
    public ResponseEntity<ApiResponse<Void>> toggleAvailability(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        profileService.toggleMentorAvailability(principal.getId());
        return ResponseEntity.ok(ApiResponse.noContent());
    }
}