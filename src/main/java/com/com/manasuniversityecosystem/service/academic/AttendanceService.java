package com.com.manasuniversityecosystem.service.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.*;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepo;
    private final AttendanceRecordRepository  recordRepo;
    private final CourseRepository            courseRepo;
    private final CourseEnrollmentRepository  enrollmentRepo;
    private final UserRepository              userRepo;

    // ── Sessions ───────────────────────────────────────────────

    public List<AttendanceSession> getSessionsForCourse(UUID courseId) {
        return sessionRepo.findByCourseIdOrderBySessionDateDesc(courseId);
    }

    public List<AttendanceSession> getSessionsForTeacher(UUID teacherId) {
        return sessionRepo.findByTeacher(teacherId);
    }

    public AttendanceSession getById(UUID id) {
        return sessionRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + id));
    }

    public AttendanceSession getByToken(String token) {
        return sessionRepo.findByQrToken(token).orElse(null);
    }

    /**
     * Teacher opens a new attendance session → generates a UUID QR token.
     * Minutes = how long the QR is valid (default 15).
     */
    @Transactional
    public AttendanceSession openSession(UUID courseId, UUID teacherId,
                                         String topic, int validMinutes) {
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        AppUser teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        // Close any previously open session for this course today
        sessionRepo.findByCourseIdAndSessionDate(courseId, LocalDate.now())
                .forEach(s -> { s.setIsOpen(false); sessionRepo.save(s); });

        String token = UUID.randomUUID().toString().replace("-", "");

        AttendanceSession session = AttendanceSession.builder()
                .course(course)
                .teacher(teacher)
                .sessionDate(LocalDate.now())
                .topic(topic)
                .qrToken(token)
                .expiresAt(LocalDateTime.now().plusMinutes(validMinutes))
                .isOpen(true)
                .build();
        AttendanceSession saved = sessionRepo.save(session);
        log.info("Attendance session opened: course={} token={}", courseId, token);
        return saved;
    }

    @Transactional
    public void closeSession(UUID sessionId, UUID teacherId) {
        AttendanceSession session = getById(sessionId);
        if (!session.getTeacher().getId().equals(teacherId)) {
            throw new SecurityException("Only the session teacher can close it");
        }
        session.setIsOpen(false);
        sessionRepo.save(session);

        // Auto-mark absent all enrolled students who didn't check in
        List<CourseEnrollment> enrolled = enrollmentRepo
                .findByCourseIdAndStatus(session.getCourse().getId(),
                        CourseEnrollment.EnrollmentStatus.ACTIVE);
        for (CourseEnrollment e : enrolled) {
            if (!recordRepo.existsBySessionIdAndStudentId(sessionId, e.getStudent().getId())) {
                recordRepo.save(AttendanceRecord.builder()
                        .session(session)
                        .student(e.getStudent())
                        .status(AttendanceRecord.AttendanceStatus.ABSENT)
                        .method(AttendanceRecord.CheckInMethod.MANUAL)
                        .build());
            }
        }
    }

    // ── Check-in ───────────────────────────────────────────────

    /**
     * Student scans QR → calls this.
     * Returns the record on success, throws on failure.
     */
    @Transactional
    public AttendanceRecord checkIn(String token, UUID studentId) {
        AttendanceSession session = sessionRepo.findByQrToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid QR code"));

        if (!session.canCheckIn()) {
            throw new IllegalStateException("This session is closed or the QR code has expired");
        }
        if (recordRepo.existsBySessionIdAndStudentId(session.getId(), studentId)) {
            throw new IllegalStateException("You have already checked in for this session");
        }

        AppUser student = userRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        if (student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Only students can check in");
        }

        AttendanceRecord record = AttendanceRecord.builder()
                .session(session)
                .student(student)
                .status(AttendanceRecord.AttendanceStatus.PRESENT)
                .method(AttendanceRecord.CheckInMethod.QR)
                .build();
        return recordRepo.save(record);
    }

    /** Teacher manually marks a student present/absent/excused */
    @Transactional
    public AttendanceRecord manualMark(UUID sessionId, UUID studentId,
                                       AttendanceRecord.AttendanceStatus status, String note) {
        AttendanceSession session = getById(sessionId);
        AppUser student = userRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));

        AttendanceRecord record = recordRepo
                .findBySessionIdAndStudentId(sessionId, studentId)
                .orElse(AttendanceRecord.builder()
                        .session(session).student(student)
                        .method(AttendanceRecord.CheckInMethod.MANUAL).build());
        record.setStatus(status);
        record.setNote(note);
        return recordRepo.save(record);
    }

    // ── Reports ────────────────────────────────────────────────

    public List<AttendanceRecord> getRecordsForSession(UUID sessionId) {
        return recordRepo.findBySessionIdOrderByCheckedInAtAsc(sessionId);
    }

    public List<AttendanceRecord> getRecordsForStudent(UUID studentId) {
        return recordRepo.findByStudentIdOrderBySession_SessionDateDesc(studentId);
    }

    /** Returns attendance % (0-100) for a student in a course */
    public double getAttendanceRate(UUID studentId, UUID courseId) {
        long total   = recordRepo.countSessionsForCourse(courseId);
        long present = recordRepo.countPresentForStudentInCourse(studentId, courseId);
        if (total == 0) return 100.0;
        return Math.round((present * 100.0 / total) * 10.0) / 10.0;
    }

    /** Map of studentId → attendance % for a course (for teacher's dashboard) */
    public Map<UUID, Double> getAttendanceRatesByCourse(UUID courseId,
                                                        List<CourseEnrollment> enrollments) {
        return enrollments.stream().collect(Collectors.toMap(
                e -> e.getStudent().getId(),
                e -> getAttendanceRate(e.getStudent().getId(), courseId)
        ));
    }
}