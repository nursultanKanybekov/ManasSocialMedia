package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.AttendanceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceSessionRepository extends JpaRepository<AttendanceSession, UUID> {

    List<AttendanceSession> findByCourseIdOrderBySessionDateDesc(UUID courseId);

    Optional<AttendanceSession> findByQrToken(String qrToken);

    List<AttendanceSession> findByCourseIdAndSessionDate(UUID courseId, LocalDate date);

    @Query("SELECT s FROM AttendanceSession s WHERE s.course.teacher.id = :teacherId ORDER BY s.sessionDate DESC")
    List<AttendanceSession> findByTeacher(@Param("teacherId") UUID teacherId);

    @Query("SELECT s FROM AttendanceSession s WHERE s.isOpen = true")
    List<AttendanceSession> findAllOpen();
}