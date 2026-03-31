package com.com.manasuniversityecosystem.service.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.Course;
import com.com.manasuniversityecosystem.domain.entity.academic.Grade;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.CourseRepository;
import com.com.manasuniversityecosystem.repository.academic.GradeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GradeService {

    private final GradeRepository  gradeRepo;
    private final CourseRepository courseRepo;
    private final UserRepository   userRepo;

    public List<Grade> getGradesForStudent(UUID studentId) {
        return gradeRepo.findByStudentIdOrderByCourse_CodeAscTypeAsc(studentId);
    }

    public List<Grade> getGradesForCourse(UUID courseId) {
        return gradeRepo.findByCourseIdOrderByStudent_FullNameAscTypeAsc(courseId);
    }

    public List<Grade> getGradesForStudentInCourse(UUID studentId, UUID courseId) {
        return gradeRepo.findByStudentIdAndCourseId(studentId, courseId);
    }

    public Double getStudentGpa(UUID studentId) {
        Double avg = gradeRepo.avgScoreForStudent(studentId);
        return avg != null ? Math.round(avg * 100.0) / 100.0 : 0.0;
    }

    public Double getCourseAverage(UUID courseId) {
        Double avg = gradeRepo.avgScoreForCourse(courseId);
        return avg != null ? Math.round(avg * 100.0) / 100.0 : 0.0;
    }

    @Transactional
    public Grade enterGrade(UUID studentId, UUID courseId, UUID teacherId,
                            Grade.GradeType type, double score, double maxScore, String feedback) {
        AppUser student = userRepo.findById(studentId)
                .orElseThrow(() -> new IllegalArgumentException("Student not found"));
        Course course = courseRepo.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found"));
        AppUser teacher = userRepo.findById(teacherId)
                .orElseThrow(() -> new IllegalArgumentException("Teacher not found"));

        // Upsert: update if same type already exists for this student/course
        Grade grade = gradeRepo.findByStudentIdAndCourseIdAndType(studentId, courseId, type)
                .orElse(Grade.builder()
                        .student(student).course(course).teacher(teacher).type(type).build());

        grade.setScore(score);
        grade.setMaxScore(maxScore);
        grade.setFeedback(feedback);
        grade.setTeacher(teacher);
        return gradeRepo.save(grade);
    }

    @Transactional
    public void deleteGrade(UUID gradeId) {
        gradeRepo.deleteById(gradeId);
    }
}