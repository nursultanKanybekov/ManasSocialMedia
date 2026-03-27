package com.com.manasuniversityecosystem.repository.exam;

import com.com.manasuniversityecosystem.domain.entity.exam.ExamSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface ExamScheduleRepository extends JpaRepository<ExamSchedule, UUID> {

    /** All exams for a faculty */
    List<ExamSchedule> findByFacultyIdOrderByExamDateAsc(UUID facultyId);

    /** Exams today for a faculty */
    List<ExamSchedule> findByFacultyIdAndExamDateOrderByExamTimeAsc(UUID facultyId, LocalDate examDate);

    /** All exams on a given date (cross-faculty search) */
    List<ExamSchedule> findByExamDateOrderByFacultyIdAscExamTimeAsc(LocalDate examDate);

    /** All exams across all faculties ordered by date */
    List<ExamSchedule> findAllByOrderByExamDateAscExamTimeAsc();

    /** Exams where teacher name matches (for TEACHER role today notification) */
    @Query("SELECT e FROM ExamSchedule e WHERE e.examDate = :date AND LOWER(e.teacherName) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<ExamSchedule> findTodayExamsByTeacherName(@Param("date") LocalDate date, @Param("name") String name);

    /** Search by course name, code, teacher, room (cross-faculty) */
    @Query("SELECT e FROM ExamSchedule e WHERE " +
            "LOWER(e.courseName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.courseCode) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.teacherName) LIKE LOWER(CONCAT('%', :q, '%')) OR " +
            "LOWER(e.examRoom) LIKE LOWER(CONCAT('%', :q, '%')) " +
            "ORDER BY e.examDate ASC")
    List<ExamSchedule> search(@Param("q") String query);

    /** Delete all exams for a faculty (before re-import) */
    void deleteByFacultyId(UUID facultyId);

    /** Count exams today for a faculty */
    long countByFacultyIdAndExamDate(UUID facultyId, LocalDate date);
}