package com.com.manasuniversityecosystem.service.career;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.career.MentorshipRequest;
import com.com.manasuniversityecosystem.domain.enums.PointReason;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.career.MentorshipRepository;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MentorshipService {

    private final MentorshipRepository mentorshipRepo;
    private final UserRepository userRepo;
    private final GamificationService gamificationService;
    private final NotificationService notificationService;

    /**
     * Smart mentor matching:
     * Finds MEZUN alumni with overlapping skills in the student's faculty.
     * Falls back to cross-faculty if no results.
     */
    @Transactional(readOnly = true)
    public List<AppUser> findMatchingMentors(AppUser student) {
        List<String> studentSkills = student.getProfile() != null
                ? student.getProfile().getSkills() : List.of();
        UUID facultyId = student.getFaculty() != null
                ? student.getFaculty().getId() : null;

        // Fetch active MEZUN from same faculty first
        List<AppUser> mentors = userRepo.findMentorsByFacultyAndSkills(
                facultyId, null, UserRole.MEZUN);

        // Filter by skill overlap in Java
        if (!studentSkills.isEmpty()) {
            List<AppUser> skilled = mentors.stream()
                    .filter(m -> m.getProfile() != null
                            && m.getProfile().getSkills() != null
                            && m.getProfile().getSkills().stream()
                            .anyMatch(studentSkills::contains))
                    .toList();
            if (!skilled.isEmpty()) return skilled;
        }

        // If no skill match, return all same-faculty MEZUN
        if (!mentors.isEmpty()) return mentors;

        // Fallback: cross-faculty
        return userRepo.findMentorsByFacultyAndSkills(null, null, UserRole.MEZUN);
    }

    @Transactional
    public MentorshipRequest sendRequest(AppUser student,
                                         UUID mentorId,
                                         String topic,
                                         String message) {
        if (student.getRole() != UserRole.STUDENT) {
            throw new IllegalStateException("Only students can send mentorship requests.");
        }

        AppUser mentor = userRepo.findByIdWithProfile(mentorId)
                .orElseThrow(() -> new IllegalArgumentException("Mentor not found."));

        if (mentor.getRole() != UserRole.MEZUN) {
            throw new IllegalStateException("Only MEZUN alumni can be mentors.");
        }

        boolean alreadyPending = mentorshipRepo.existsByStudentAndMentorAndStatus(
                student, mentor, MentorshipRequest.MentorshipStatus.PENDING);
        if (alreadyPending) {
            throw new IllegalStateException("A pending request already exists for this mentor.");
        }

        boolean alreadyActive = mentorshipRepo.existsByStudentAndMentorAndStatus(
                student, mentor, MentorshipRequest.MentorshipStatus.ACTIVE);
        if (alreadyActive) {
            throw new IllegalStateException("You already have an active mentorship with this mentor.");
        }

        MentorshipRequest req = MentorshipRequest.builder()
                .student(student)
                .mentor(mentor)
                .topic(topic)
                .message(message)
                .status(MentorshipRequest.MentorshipStatus.PENDING)
                .build();

        mentorshipRepo.save(req);
        notificationService.notifyMentorshipRequested(mentor.getId(), student.getId(), student.getFullName());
        log.info("Mentorship request: student={} → mentor={} topic={}",
                student.getEmail(), mentor.getEmail(), topic);
        return req;
    }

    @Transactional
    public MentorshipRequest respond(UUID requestId, AppUser mentor, boolean accept) {
        MentorshipRequest req = findById(requestId);

        if (!req.getMentor().getId().equals(mentor.getId())) {
            throw new SecurityException("Not authorized to respond to this request.");
        }
        if (req.getStatus() != MentorshipRequest.MentorshipStatus.PENDING) {
            throw new IllegalStateException("Request is no longer pending.");
        }

        req.setStatus(accept ? MentorshipRequest.MentorshipStatus.ACTIVE : MentorshipRequest.MentorshipStatus.DECLINED);
        req.setUpdatedAt(LocalDateTime.now());
        mentorshipRepo.save(req);

        notificationService.notifyMentorshipResponse(req.getStudent().getId(), mentor.getId(),
                mentor.getFullName(), accept);

        if (accept) {
            // Award points to mentor for accepting
            gamificationService.awardPoints(mentor, PointReason.MENTOR, requestId);
            log.info("Mentorship ACCEPTED: mentor={} student={}",
                    mentor.getEmail(), req.getStudent().getEmail());
        } else {
            log.info("Mentorship DECLINED: mentor={} student={}",
                    mentor.getEmail(), req.getStudent().getEmail());
        }
        return req;
    }

    @Transactional
    public MentorshipRequest complete(UUID requestId, AppUser initiator) {
        MentorshipRequest req = findById(requestId);

        boolean isMentor  = req.getMentor().getId().equals(initiator.getId());
        boolean isStudent = req.getStudent().getId().equals(initiator.getId());
        if (!isMentor && !isStudent) {
            throw new SecurityException("Not authorized to complete this mentorship.");
        }
        if (req.getStatus() != MentorshipRequest.MentorshipStatus.ACTIVE) {
            throw new IllegalStateException("Mentorship is not currently active.");
        }

        req.setStatus(MentorshipRequest.MentorshipStatus.COMPLETED);
        req.setUpdatedAt(LocalDateTime.now());
        mentorshipRepo.save(req);

        // Award completion bonus to mentor
        gamificationService.awardPoints(req.getMentor(), PointReason.MENTOR, requestId);
        notificationService.notifyMentorshipCompleted(
                req.getMentor().getId(), req.getMentor().getFullName(),
                req.getStudent().getId(), req.getStudent().getFullName());
        log.info("Mentorship COMPLETED: mentor={} student={}",
                req.getMentor().getEmail(), req.getStudent().getEmail());
        return req;
    }

    @Transactional(readOnly = true)
    public List<MentorshipRequest> getStudentRequests(AppUser student) {
        return mentorshipRepo.findByStudentIdWithMentor(student.getId());
    }

    @Transactional(readOnly = true)
    public List<MentorshipRequest> getPendingForMentor(AppUser mentor) {
        return mentorshipRepo.findByMentorIdAndStatusWithStudent(
                mentor.getId(), MentorshipRequest.MentorshipStatus.PENDING);
    }

    @Transactional(readOnly = true)
    public List<MentorshipRequest> getActiveForMentor(AppUser mentor) {
        return mentorshipRepo.findByMentorIdAndStatusWithStudent(
                mentor.getId(), MentorshipRequest.MentorshipStatus.ACTIVE);
    }

    private MentorshipRequest findById(UUID id) {
        return mentorshipRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Mentorship request not found: " + id));
    }
}