package com.com.manasuniversityecosystem.web.controller;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.career.JobApplication;
import com.com.manasuniversityecosystem.domain.entity.career.JobListing;
import com.com.manasuniversityecosystem.domain.entity.career.MentorshipRequest;
import com.com.manasuniversityecosystem.domain.enums.JobType;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.ProfileService;
import com.com.manasuniversityecosystem.repository.ProfileRepository;
import com.com.manasuniversityecosystem.service.career.JobService;
import com.com.manasuniversityecosystem.service.career.MentorshipService;
import com.com.manasuniversityecosystem.web.dto.career.CreateJobRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/career")
@RequiredArgsConstructor
@Slf4j
public class CareerController {

    private final JobService jobService;
    private final MentorshipService mentorshipService;
    private final UserService userService;
    private final ProfileService profileService;
    private final ProfileRepository profileRepo;

    // ─────────────────────────── JOBS ────────────────────────

    // GET /career/jobs
    @GetMapping("/jobs")
    public String jobsPage(@RequestParam(defaultValue = "0")       int    page,
                           @RequestParam(defaultValue = "10")      int    size,
                           @RequestParam(required = false)         String type,
                           @RequestParam(defaultValue = "newest")  String sort,
                           @RequestParam(defaultValue = "en")      String lang,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           Model model) {
        JobType jobType = null;
        if (type != null && !type.isBlank()) {
            try { jobType = JobType.valueOf(type.toUpperCase()); }
            catch (IllegalArgumentException ignored) {}
        }

        Page<JobListing> jobs = jobType != null
                ? jobService.getActiveJobsByType(jobType, page, size, sort)
                : jobService.getActiveJobs(page, size, sort);

        model.addAttribute("jobs",          jobs);
        model.addAttribute("jobTypes",      JobType.values());
        model.addAttribute("lang",          lang);
        model.addAttribute("currentUserId", principal.getId());
        return "career/jobs";
    }

    // GET /career/jobs/{id}
    @GetMapping("/jobs/{id}")
    public String jobDetail(@PathVariable UUID id,
                            @RequestParam(defaultValue = "en") String lang,
                            @AuthenticationPrincipal UserDetailsImpl principal,
                            Model model) {
        JobListing job = jobService.getById(id);
        model.addAttribute("job",      job);
        model.addAttribute("jobId",    id);
        model.addAttribute("lang",     lang);
        model.addAttribute("applied",  jobService.hasApplied(id, principal.getId()));
        return "career/job-detail";
    }

    // GET /career/jobs/new
    @GetMapping("/jobs/new")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN','MEZUN')")
    public String newJobPage(Model model) {
        model.addAttribute("createJobRequest", new CreateJobRequest());
        model.addAttribute("jobTypes", JobType.values());
        return "career/job-form";
    }

    // POST /career/jobs/new
    @PostMapping("/jobs/new")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN','MEZUN')")
    public String createJob(@Valid @ModelAttribute CreateJobRequest req,
                            BindingResult result,
                            @AuthenticationPrincipal UserDetailsImpl principal,
                            RedirectAttributes redirectAttributes,
                            Model model) {
        if (result.hasErrors()) {
            model.addAttribute("jobTypes", JobType.values());
            return "career/job-form";
        }
        AppUser user = userService.getById(principal.getId());
        jobService.createJob(user, req);
        redirectAttributes.addFlashAttribute("successMsg", "career.job.created");
        return "redirect:/career/jobs";
    }

    // POST /career/jobs/{id}/apply  (HTMX)
    @PostMapping("/jobs/{id}/apply")
    public String applyForJob(@PathVariable UUID id,
                              @RequestParam(required = false) String coverLetter,
                              @AuthenticationPrincipal UserDetailsImpl principal,
                              RedirectAttributes redirectAttributes) {
        AppUser user = userService.getById(principal.getId());
        try {
            jobService.apply(user, id, coverLetter);
            redirectAttributes.addFlashAttribute("toastSuccess",
                    "\u2705 Application sent! The employer has been notified.");
        } catch (IllegalStateException e) {
            redirectAttributes.addFlashAttribute("toastError", e.getMessage());
        }
        return "redirect:/career/jobs";
    }

    // POST /career/jobs/{id}/close
    @PostMapping("/jobs/{id}/close")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN','MEZUN')")
    public String closeJob(@PathVariable UUID id,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           RedirectAttributes redirectAttributes) {
        AppUser user = userService.getById(principal.getId());
        jobService.closeJob(id, user);
        redirectAttributes.addFlashAttribute("successMsg", "career.job.closed");
        return "redirect:/career/jobs";
    }

    // GET /career/jobs/widget  (HTMX sidebar)
    @GetMapping("/jobs/widget")
    public String jobWidget(@RequestParam(defaultValue = "en") String lang, Model model) {
        model.addAttribute("jobs", jobService.getWidgetJobs());
        model.addAttribute("lang", lang);
        return "career/fragments/job-widget :: jobWidget";
    }

    // GET /career/my-applications
    @GetMapping("/my-applications")
    public String myApplications(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        model.addAttribute("applications", jobService.getMyApplications(principal.getId()));
        return "career/my-applications";
    }

    // GET /career/jobs/{id}/applicants — employer sees all applicants for their job
    @GetMapping("/jobs/{id}/applicants")
    public String viewApplicants(@PathVariable UUID id,
                                 @AuthenticationPrincipal UserDetailsImpl principal,
                                 Model model) {
        AppUser employer = userService.getById(principal.getId());
        com.com.manasuniversityecosystem.domain.entity.career.JobListing job = jobService.getById(id);
        // Only poster, admin, or secretary can view
        boolean isOwner = job.getPostedBy().getId().equals(principal.getId());
        boolean isAdmin  = employer.getRole().name().equals("ADMIN") || employer.getRole().name().equals("SUPER_ADMIN");
        if (!isOwner && !isAdmin) return "redirect:/career/jobs/" + id;
        model.addAttribute("job",          job);
        // Use job poster for authorization check in service, or bypass via repo directly for admin
        java.util.List<com.com.manasuniversityecosystem.domain.entity.career.JobApplication> apps =
                isOwner ? jobService.getApplicationsForJob(id, employer)
                        : jobService.getApplicationsForJob(id, job.getPostedBy());
        model.addAttribute("applications", apps);
        model.addAttribute("lang",         "en");
        return "career/applicants";
    }

    // POST /career/applications/{id}/status
    @PostMapping("/applications/{id}/status")
    @PreAuthorize("hasAnyRole('EMPLOYER','ADMIN','MEZUN')")
    public String updateApplicationStatus(
            @PathVariable UUID id,
            @RequestParam String status,
            @AuthenticationPrincipal UserDetailsImpl principal,
            RedirectAttributes redirectAttributes) {
        AppUser employer = userService.getById(principal.getId());
        JobApplication.ApplicationStatus s =
                JobApplication.ApplicationStatus.valueOf(status.toUpperCase());
        com.com.manasuniversityecosystem.domain.entity.career.JobApplication updated =
                jobService.updateApplicationStatus(id, employer, s);
        redirectAttributes.addFlashAttribute("successMsg", "career.application.updated");
        return "redirect:/career/jobs/" + updated.getJob().getId() + "/applicants";
    }

    // ─────────────────────────── MENTORSHIP ──────────────────

    // GET /career/mentorship
    @GetMapping("/mentorship")
    public String mentorshipPage(@AuthenticationPrincipal UserDetailsImpl principal,
                                 @RequestParam(required = false) String sortBy,
                                 @RequestParam(required = false) String faculty,
                                 @RequestParam(required = false) String search,
                                 Model model) {
        AppUser user = userService.getById(principal.getId());
        model.addAttribute("currentUser", user);

        // All roles see the mentor table
        java.util.List<com.com.manasuniversityecosystem.domain.entity.Profile> mentorProfiles;
        if (faculty != null && !faculty.isBlank()) {
            try {
                java.util.UUID facultyId = java.util.UUID.fromString(faculty);
                mentorProfiles = profileRepo.findAvailableMentorsByFaculty(facultyId);
            } catch (Exception e) {
                mentorProfiles = profileRepo.findAvailableMentors();
            }
        } else {
            mentorProfiles = profileRepo.findAvailableMentors();
        }

        // Filter by search
        if (search != null && !search.isBlank()) {
            String q = search.toLowerCase();
            mentorProfiles = mentorProfiles.stream()
                    .filter(p -> p.getUser().getFullName().toLowerCase().contains(q)
                            || (p.getMentorJobTitle() != null && p.getMentorJobTitle().toLowerCase().contains(q))
                            || p.getSkills().stream().anyMatch(s -> s.toLowerCase().contains(q)))
                    .toList();
        }

        // Sort
        if ("points".equals(sortBy)) {
            mentorProfiles = mentorProfiles.stream()
                    .sorted((a,b) -> Integer.compare(b.getTotalPoints(), a.getTotalPoints())).toList();
        } else if ("name".equals(sortBy)) {
            mentorProfiles = mentorProfiles.stream()
                    .sorted((a,b) -> a.getUser().getFullName().compareTo(b.getUser().getFullName())).toList();
        }

        model.addAttribute("mentorProfiles", mentorProfiles);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("searchQ", search);

        // My own mentorship data
        if (user.getRole().name().equals("STUDENT")) {
            model.addAttribute("myRequests", mentorshipService.getStudentRequests(user));
        }
        if (user.getRole().name().equals("MEZUN")) {
            model.addAttribute("pendingRequests", mentorshipService.getPendingForMentor(user));
            model.addAttribute("activeRequests", mentorshipService.getActiveForMentor(user));
            // Own profile - check if they have mentor enabled
            try {
                com.com.manasuniversityecosystem.domain.entity.Profile myProfile = profileService.getByUserId(user.getId());
                model.addAttribute("myProfile", myProfile);
            } catch (Exception ignored) {}
        }
        return "career/mentorship";
    }

    // POST /career/mentorship/request  (HTMX)
    @PostMapping("/mentorship/request")
    public String sendMentorshipRequest(
            @RequestParam UUID mentorId,
            @RequestParam(required = false) String topic,
            @RequestParam(required = false) String message,
            @AuthenticationPrincipal UserDetailsImpl principal,
            Model model) {
        AppUser student = userService.getById(principal.getId());
        try {
            MentorshipRequest req = mentorshipService.sendRequest(
                    student, mentorId, topic, message);
            model.addAttribute("request", req);
            model.addAttribute("success", true);
        } catch (IllegalStateException e) {
            model.addAttribute("error", e.getMessage());
        }
        return "career/fragments/mentorship-status :: mentorshipStatus";
    }

    // POST /career/mentorship/respond/{id}
    @PostMapping("/mentorship/respond/{id}")
    @PreAuthorize("hasAnyRole('MEZUN','ADMIN')")
    public String respondToMentorship(@PathVariable UUID id,
                                      @RequestParam boolean accept,
                                      @AuthenticationPrincipal UserDetailsImpl principal,
                                      RedirectAttributes redirectAttributes) {
        AppUser mentor = userService.getById(principal.getId());
        mentorshipService.respond(id, mentor, accept);
        redirectAttributes.addFlashAttribute("successMsg",
                accept ? "mentorship.accepted" : "mentorship.declined");
        return "redirect:/career/mentorship";
    }

    // POST /career/mentorship/complete/{id}
    @PostMapping("/mentorship/complete/{id}")
    public String completeMentorship(@PathVariable UUID id,
                                     @AuthenticationPrincipal UserDetailsImpl principal,
                                     RedirectAttributes redirectAttributes) {
        AppUser user = userService.getById(principal.getId());
        mentorshipService.complete(id, user);
        redirectAttributes.addFlashAttribute("successMsg", "mentorship.completed");
        return "redirect:/career/mentorship";
    }

    // POST /career/mentorship/toggle-availability  (MEZUN only)
    @PostMapping("/mentorship/toggle-availability")
    @PreAuthorize("hasRole('MEZUN')")
    public String toggleMentorAvailability(@AuthenticationPrincipal UserDetailsImpl principal,
                                           RedirectAttributes redirectAttributes) {
        boolean enabled = profileService.toggleMentorAvailability(principal.getId());
        redirectAttributes.addFlashAttribute("successMsg",
                enabled ? "mentorship.availability.enabled" : "mentorship.availability.disabled");
        return "redirect:/career/mentorship";
    }
}