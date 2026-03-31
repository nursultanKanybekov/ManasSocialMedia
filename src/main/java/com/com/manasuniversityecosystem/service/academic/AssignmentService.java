package com.com.manasuniversityecosystem.service.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.Assignment;
import com.com.manasuniversityecosystem.domain.entity.academic.AssignmentSubmission;
import com.com.manasuniversityecosystem.domain.entity.academic.Course;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.AssignmentRepository;
import com.com.manasuniversityecosystem.repository.academic.CourseRepository;
import com.com.manasuniversityecosystem.repository.academic.SubmissionRepository;
import com.com.manasuniversityecosystem.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentService {

    private final AssignmentRepository assignmentRepo;
    private final SubmissionRepository submissionRepo;
    private final CourseRepository     courseRepo;
    private final UserRepository       userRepo;
    private final CloudinaryService    cloudinary;

    // ── Assignments ────────────────────────────────────────────

    public Assignment getById(UUID id) {
        return assignmentRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Assignment not found: " + id));
    }

    public List<Assignment> getForCourse(UUID courseId) {
        return assignmentRepo.findByCourseIdOrderByDeadlineAsc(courseId);
    }

    public List<Assignment> getForStudent(UUID studentId) {
        return assignmentRepo.findForStudent(studentId);
    }

    public List<Assignment> getUpcomingForStudent(UUID studentId) {
        return assignmentRepo.findUpcomingForStudent(studentId, LocalDateTime.now());
    }

    public List<Assignment> getForTeacher(UUID teacherId) {
        return assignmentRepo.findByTeacher(teacherId);
    }

    @Transactional
    public Assignment create(UUID courseId, UUID teacherId,
                             String title, String description,
                             Assignment.AssignmentType type, LocalDateTime deadline,
                             double maxScore, MultipartFile attachmentFile) throws IOException {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        AppUser teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        String attachmentUrl = null;
        if (attachmentFile != null && !attachmentFile.isEmpty()) {
            attachmentUrl = cloudinary.uploadDocument(attachmentFile, "manas/assignments");
        }

        return assignmentRepo.save(Assignment.builder()
                .course(course)
                .createdBy(teacher)
                .title(title)
                .description(description)
                .type(type)
                .deadline(deadline)
                .maxScore(maxScore)
                .attachmentUrl(attachmentUrl)
                .build());
    }

    @Transactional
    public void delete(UUID id, UUID requesterId) {
        Assignment a = getById(id);
        if (!a.getCreatedBy().getId().equals(requesterId)) {
            throw new SecurityException("Only the creator can delete this assignment");
        }
        assignmentRepo.deleteById(id);
    }

    // ── Submissions ────────────────────────────────────────────

    public List<AssignmentSubmission> getSubmissionsForAssignment(UUID assignmentId) {
        return submissionRepo.findByAssignmentIdOrderBySubmittedAtAsc(assignmentId);
    }

    public AssignmentSubmission getSubmission(UUID studentId, UUID assignmentId) {
        return submissionRepo.findByStudentIdAndAssignmentId(studentId, assignmentId)
                .orElse(null);
    }

    public List<AssignmentSubmission> getSubmissionsForStudent(UUID studentId) {
        return submissionRepo.findByStudentIdOrderBySubmittedAtDesc(studentId);
    }

    @Transactional
    public AssignmentSubmission submit(UUID studentId, UUID assignmentId,
                                       String textContent, MultipartFile file) throws IOException {
        Assignment assignment = getById(assignmentId);
        AppUser student = userRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        AssignmentSubmission sub = submissionRepo
                .findByStudentIdAndAssignmentId(studentId, assignmentId)
                .orElse(AssignmentSubmission.builder()
                        .student(student).assignment(assignment).build());

        sub.setTextContent(textContent);
        sub.setStatus(assignment.isPastDeadline()
                ? AssignmentSubmission.SubmissionStatus.LATE
                : AssignmentSubmission.SubmissionStatus.SUBMITTED);
        sub.setSubmittedAt(LocalDateTime.now());

        if (file != null && !file.isEmpty()) {
            String url = cloudinary.uploadDocument(file, "manas/submissions");
            sub.setFileUrl(url);
            sub.setFileName(file.getOriginalFilename());
        }
        return submissionRepo.save(sub);
    }

    @Transactional
    public AssignmentSubmission grade(UUID submissionId, UUID teacherId,
                                      double score, String feedback) {
        AssignmentSubmission sub = submissionRepo.findById(submissionId)
                .orElseThrow(() -> new IllegalArgumentException("Submission not found"));
        AppUser teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        sub.setScore(score);
        sub.setFeedback(feedback);
        sub.setGradedBy(teacher);
        sub.setGradedAt(LocalDateTime.now());
        sub.setStatus(AssignmentSubmission.SubmissionStatus.GRADED);
        return submissionRepo.save(sub);
    }
}