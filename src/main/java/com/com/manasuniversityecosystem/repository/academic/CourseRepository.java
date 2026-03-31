package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CourseRepository extends JpaRepository<Course, UUID> {

    List<Course> findByFacultyIdAndIsActiveTrueOrderByCodeAsc(UUID facultyId);

    List<Course> findByTeacherIdAndIsActiveTrueOrderByCodeAsc(UUID teacherId);

    List<Course> findByFacultyIdAndSemesterAndIsActiveTrueOrderByCodeAsc(UUID facultyId, String semester);

    @Query("SELECT c FROM Course c WHERE c.faculty.id = :facultyId AND c.registrationOpen = true AND c.isActive = true")
    List<Course> findOpenForRegistration(@Param("facultyId") UUID facultyId);

    @Query("SELECT c FROM Course c LEFT JOIN FETCH c.teacher LEFT JOIN FETCH c.faculty " +
            "WHERE LOWER(c.name) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(c.code) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<Course> search(@Param("q") String query);

    List<Course> findAllByOrderBySemesterDescCodeAsc();
}