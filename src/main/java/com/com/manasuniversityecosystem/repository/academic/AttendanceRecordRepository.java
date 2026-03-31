package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.AttendanceRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    boolean existsBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    List<AttendanceRecord> findBySessionIdOrderByCheckedInAtAsc(UUID sessionId);

    List<AttendanceRecord> findByStudentIdOrderBySession_SessionDateDesc(UUID studentId);

    @Query("SELECT COUNT(r) FROM AttendanceRecord r WHERE r.student.id = :studentId " +
            "AND r.session.course.id = :courseId AND r.status = 'PRESENT'")
    long countPresentForStudentInCourse(@Param("studentId") UUID studentId,
                                        @Param("courseId") UUID courseId);

    @Query("SELECT COUNT(s) FROM AttendanceSession s WHERE s.course.id = :courseId")
    long countSessionsForCourse(@Param("courseId") UUID courseId);
}