package com.com.manasuniversityecosystem.service.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.academic.Course;
import com.com.manasuniversityecosystem.domain.entity.academic.CourseEnrollment;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.CourseEnrollmentRepository;
import com.com.manasuniversityecosystem.repository.academic.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AcademicCourseService {

    private final CourseRepository            courseRepo;
    private final CourseEnrollmentRepository  enrollmentRepo;
    private final UserRepository              userRepo;
    private final FacultyRepository           facultyRepo;

    // ── Courses ────────────────────────────────────────────────

    public List<Course> getCoursesForFaculty(UUID facultyId) {
        return courseRepo.findByFacultyIdAndIsActiveTrueOrderByCodeAsc(facultyId);
    }

    public List<Course> getCoursesForTeacher(UUID teacherId) {
        return courseRepo.findByTeacherIdAndIsActiveTrueOrderByCodeAsc(teacherId);
    }

    public List<Course> getAll() {
        return courseRepo.findAllByOrderBySemesterDescCodeAsc();
    }

    public List<Course> search(String q) {
        return courseRepo.search(q);
    }

    public Course getById(UUID id) {
        return courseRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + id));
    }

    @Transactional
    public Course createCourse(String code, String name, String description,
                               String semester, int credits, String studyYear,
                               UUID teacherId, UUID facultyId) {
        AppUser teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));
        if (teacher.getRole() != UserRole.TEACHER && teacher.getRole() != UserRole.ADMIN
                && teacher.getRole() != UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("User is not a teacher");
        }
        Faculty faculty = facultyRepo.findById(facultyId)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));

        Course course = Course.builder()
                .code(code.toUpperCase().trim())
                .name(name.trim())
                .description(description)
                .semester(semester)
                .credits(credits)
                .studyYear(studyYear)
                .teacher(teacher)
                .faculty(faculty)
                .build();
        return courseRepo.save(course);
    }

    @Transactional
    public Course updateCourse(UUID id, String name, String description,
                               String semester, int credits, String studyYear,
                               boolean registrationOpen) {
        Course course = getById(id);
        course.setName(name);
        course.setDescription(description);
        course.setSemester(semester);
        course.setCredits(credits);
        course.setStudyYear(studyYear);
        course.setRegistrationOpen(registrationOpen);
        return courseRepo.save(course);
    }

    @Transactional
    public void toggleRegistration(UUID courseId, boolean open) {
        Course c = getById(courseId);
        c.setRegistrationOpen(open);
        courseRepo.save(c);
    }

    @Transactional
    public void deleteCourse(UUID id) {
        courseRepo.deleteById(id);
    }

    // ── Enrollment ─────────────────────────────────────────────

    public List<CourseEnrollment> getEnrollmentsForStudent(UUID studentId) {
        return enrollmentRepo.findByStudentIdOrderByCourse_SemesterDescCourse_CodeAsc(studentId);
    }

    public List<CourseEnrollment> getActiveEnrollmentsForStudent(UUID studentId) {
        return enrollmentRepo.findActiveByStudent(studentId);
    }

    public List<CourseEnrollment> getEnrolledStudents(UUID courseId) {
        return enrollmentRepo.findByCourseIdAndStatus(courseId, CourseEnrollment.EnrollmentStatus.ACTIVE);
    }

    public List<Course> getOpenCoursesForFaculty(UUID facultyId) {
        return courseRepo.findOpenForRegistration(facultyId);
    }

    @Transactional
    public CourseEnrollment enroll(UUID studentId, UUID courseId) {
        if (enrollmentRepo.existsByStudentIdAndCourseId(studentId, courseId)) {
            throw new IllegalStateException("Already enrolled in this course");
        }
        Course course = getById(courseId);
        if (!course.getRegistrationOpen()) {
            throw new IllegalStateException("Registration is not open for this course");
        }
        AppUser student = userRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        if (student.getRole() != UserRole.STUDENT) {
            throw new IllegalArgumentException("Only students can enroll");
        }
        CourseEnrollment enrollment = CourseEnrollment.builder()
                .student(student)
                .course(course)
                .build();
        return enrollmentRepo.save(enrollment);
    }

    @Transactional
    public void dropCourse(UUID studentId, UUID courseId) {
        enrollmentRepo.findByStudentIdAndCourseId(studentId, courseId)
                .ifPresent(e -> {
                    e.setStatus(CourseEnrollment.EnrollmentStatus.DROPPED);
                    enrollmentRepo.save(e);
                });
    }

    /** Admin/secretary manually enrolls or drops any student */
    @Transactional
    public void adminEnroll(UUID studentId, UUID courseId) {
        if (enrollmentRepo.existsByStudentIdAndCourseId(studentId, courseId)) return;
        Course course = getById(courseId);
        AppUser student = userRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        enrollmentRepo.save(CourseEnrollment.builder()
                .student(student).course(course).build());
    }

    @Transactional
    public void setFinalGrade(UUID enrollmentId, String letterGrade, double score) {
        CourseEnrollment e = enrollmentRepo.findById(enrollmentId)
                .orElseThrow(() -> new IllegalArgumentException("Enrollment not found"));
        e.setFinalGrade(letterGrade);
        e.setFinalScore(score);
        e.setStatus(CourseEnrollment.EnrollmentStatus.COMPLETED);
        enrollmentRepo.save(e);
    }

    public boolean isEnrolled(UUID studentId, UUID courseId) {
        return enrollmentRepo.findByStudentIdAndCourseId(studentId, courseId)
                .map(e -> e.getStatus() == CourseEnrollment.EnrollmentStatus.ACTIVE)
                .orElse(false);
    }
}