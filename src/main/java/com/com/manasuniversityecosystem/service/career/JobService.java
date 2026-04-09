package com.com.manasuniversityecosystem.service.career;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.career.JobApplication;
import com.com.manasuniversityecosystem.domain.entity.career.JobListing;
import com.com.manasuniversityecosystem.domain.enums.JobType;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.career.JobApplicationRepository;
import com.com.manasuniversityecosystem.repository.career.JobRepository;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import com.com.manasuniversityecosystem.web.dto.career.CreateJobRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepo;
    private final JobApplicationRepository applicationRepo;
    private final GamificationService gamificationService;
    private final NotificationService notificationService;
    private final FacultyRepository facultyRepo;

    // ── JOB LISTINGS ─────────────────────────────────────────

    @Transactional
    public JobListing createJob(AppUser poster, CreateJobRequest req) {
        // Resolve optional target faculty
        com.com.manasuniversityecosystem.domain.entity.Faculty targetFaculty = null;
        if (req.getTargetFacultyId() != null) {
            targetFaculty = facultyRepo.findById(req.getTargetFacultyId()).orElse(null);
        }

        JobListing job = JobListing.builder()
                .postedBy(poster)
                .titleI18n(req.getTitleI18n())
                .descriptionI18n(req.getDescriptionI18n())
                .jobType(req.getJobType())
                .location(req.getLocation())
                .salaryRange(req.getSalaryRange())
                .deadline(req.getDeadline())
                .targetFaculty(targetFaculty)
                .isActive(true)
                .build();
        jobRepo.save(job);

        String title = req.getTitleI18n().getOrDefault("en", "New job");

        // Notify students/mezuns (available in CareerHub for all)
        notificationService.notifyNewJob(poster.getId(), job.getId(), title);

        // Notify faculty admins of the targeted faculty
        if (targetFaculty != null) {
            notificationService.notifyFacultyAdminsNewJob(poster.getId(), job.getId(), title, targetFaculty.getId());
        }

        log.info("Job posted by {}: {}", poster.getEmail(), req.getTitleI18n());
        return job;
    }

    @Transactional(readOnly = true)
    public Page<JobListing> getActiveJobs(int page, int size) {
        return getActiveJobs(page, size, "newest");
    }

    public Page<JobListing> getActiveJobs(int page, int size, String sort) {
        var p = PageRequest.of(page, size);
        return switch (sort == null ? "newest" : sort) {
            case "oldest"   -> jobRepo.findActiveJobsOldest(p);
            case "deadline" -> jobRepo.findActiveJobsByDeadline(p);
            case "salary"   -> jobRepo.findActiveJobsBySalary(p);
            default         -> jobRepo.findActiveJobs(p);
        };
    }

    @Transactional(readOnly = true)
    public Page<JobListing> getActiveJobsByType(JobType type, int page, int size) {
        return getActiveJobsByType(type, page, size, "newest");
    }

    @Transactional(readOnly = true)
    public Page<JobListing> getActiveJobsByType(JobType type, int page, int size, String sort) {
        var p = PageRequest.of(page, size);
        return switch (sort == null ? "newest" : sort) {
            case "oldest"   -> jobRepo.findActiveJobsByTypeOldest(type, p);
            case "deadline" -> jobRepo.findActiveJobsByTypeDeadline(type, p);
            case "salary"   -> jobRepo.findActiveJobsByTypeSalary(type, p);
            default         -> jobRepo.findActiveJobsByType(type, p);
        };
    }

    @Transactional(readOnly = true)
    public JobListing getById(UUID id) {
        return jobRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + id));
    }

    @Transactional
    public JobListing updateJob(UUID jobId, AppUser user, CreateJobRequest req) {
        JobListing job = getById(jobId);
        if (!job.getPostedBy().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to edit this job.");
        }
        if (req.getTitleI18n() != null)       job.setTitleI18n(req.getTitleI18n());
        if (req.getDescriptionI18n() != null) job.setDescriptionI18n(req.getDescriptionI18n());
        if (req.getJobType() != null)         job.setJobType(req.getJobType());
        if (req.getLocation() != null)        job.setLocation(req.getLocation());
        if (req.getSalaryRange() != null)     job.setSalaryRange(req.getSalaryRange());
        if (req.getDeadline() != null)        job.setDeadline(req.getDeadline());
        return jobRepo.save(job);
    }

    @Transactional
    public void closeJob(UUID jobId, AppUser user) {
        JobListing job = getById(jobId);
        if (!job.getPostedBy().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to close this job.");
        }
        job.setIsActive(false);
        jobRepo.save(job);
    }

    @Transactional(readOnly = true)
    public List<JobListing> getWidgetJobs() {
        return jobRepo.findLatestActive(PageRequest.of(0, 5));
    }

    @Transactional(readOnly = true)
    public List<JobListing> getMyJobs(UUID userId) {
        return jobRepo.findByPostedByIdOrderByCreatedAtDesc(userId);
    }

    // ── APPLICATIONS ─────────────────────────────────────────

    @Transactional
    public JobApplication apply(AppUser applicant, UUID jobId, String coverLetter) {
        if (applicationRepo.existsByJobIdAndApplicantId(jobId, applicant.getId())) {
            throw new IllegalStateException("You have already applied for this job.");
        }
        JobListing job = getById(jobId);
        if (!job.getIsActive() || job.isExpired()) {
            throw new IllegalStateException("This job listing is no longer accepting applications.");
        }

        JobApplication application = JobApplication.builder()
                .job(job)
                .applicant(applicant)
                .coverLetter(coverLetter)
                .status(JobApplication.ApplicationStatus.APPLIED)
                .build();
        applicationRepo.save(application);

        // Award points for applying
        gamificationService.awardPoints(applicant, PointReason.JOB_HELP, jobId);

        // Notify employer
        notificationService.notifyJobApplied(job.getPostedBy().getId(), applicant.getId(),
                jobId, job.getLocalizedTitle("en"), applicant.getFullName());

        log.info("Application submitted by {} for job {}", applicant.getEmail(), jobId);
        return application;
    }

    @Transactional
    public JobApplication updateApplicationStatus(UUID applicationId,
                                                  AppUser employer,
                                                  JobApplication.ApplicationStatus newStatus) {
        JobApplication app = applicationRepo.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));
        if (!app.getJob().getPostedBy().getId().equals(employer.getId())) {
            throw new SecurityException("Not authorized to update this application.");
        }
        app.setStatus(newStatus);
        JobApplication saved = applicationRepo.save(app);

        // Notify applicant of status change
        notificationService.notifyApplicationStatus(app.getApplicant().getId(),
                app.getJob().getLocalizedTitle("en"), newStatus.name());

        return saved;
    }

    @Transactional(readOnly = true)
    public List<JobApplication> getApplicationsForJob(UUID jobId, AppUser employer) {
        JobListing job = getById(jobId);
        if (!job.getPostedBy().getId().equals(employer.getId())) {
            throw new SecurityException("Not authorized to view these applications.");
        }
        return applicationRepo.findByJobIdWithApplicants(jobId);
    }

    @Transactional(readOnly = true)
    public List<JobApplication> getMyApplications(UUID userId) {
        return applicationRepo.findByApplicantIdWithJob(userId);
    }

    @Transactional(readOnly = true)
    public boolean hasApplied(UUID jobId, UUID userId) {
        return applicationRepo.existsByJobIdAndApplicantId(jobId, userId);
    }
}