package com.com.manasuniversityecosystem.repository;


import com.com.manasuniversityecosystem.domain.entity.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProfileRepository extends JpaRepository<Profile, UUID> {

    Optional<Profile> findByUserId(UUID userId);

    List<Profile> findAllByOrderByTotalPointsDesc();

    Page<Profile> findAllByOrderByTotalPointsDesc(Pageable pageable);

    /** Global leaderboard top N */
    @Query("SELECT p FROM Profile p JOIN FETCH p.user u " +
            "WHERE u.status = 'ACTIVE' ORDER BY p.totalPoints DESC")
    List<Profile> findTopProfiles(Pageable pageable);

    /** Faculty leaderboard */
    @Query("SELECT p FROM Profile p JOIN FETCH p.user u " +
            "WHERE u.faculty.id = :facultyId AND u.status = 'ACTIVE' " +
            "ORDER BY p.totalPoints DESC")
    List<Profile> findTopByFaculty(@Param("facultyId") UUID facultyId, Pageable pageable);

    @Modifying
    @Query("UPDATE Profile p SET p.totalPoints = p.totalPoints + :pts WHERE p.user.id = :userId")
    int addPoints(@Param("userId") UUID userId, @Param("pts") int pts);

    @Query("SELECT p FROM Profile p WHERE p.user.id = :userId")
    Optional<Profile> findByUserIdForUpdate(@Param("userId") UUID userId);

    /** Find all mentorship-enabled alumni profiles */
    @Query("SELECT p FROM Profile p JOIN FETCH p.user u " +
            "WHERE p.canMentor = true AND u.status = 'ACTIVE' " +
            "ORDER BY p.totalPoints DESC")
    List<Profile> findAvailableMentors();

    /** Find mentors filtered by faculty */
    @Query("SELECT p FROM Profile p JOIN FETCH p.user u " +
            "WHERE p.canMentor = true AND u.status = 'ACTIVE' " +
            "AND u.faculty.id = :facultyId ORDER BY p.totalPoints DESC")
    List<Profile> findAvailableMentorsByFaculty(@Param("facultyId") UUID facultyId);

    @Modifying
    @Query("DELETE FROM Profile p WHERE p.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    /** All alumni who opted in to the world map, with their user and faculty */
    @Query("""
            SELECT p FROM Profile p
            JOIN FETCH p.user u
            LEFT JOIN FETCH u.faculty
            WHERE p.showOnMap = true
              AND u.status = 'ACTIVE'
              AND u.role = 'MEZUN'
              AND p.mapLat IS NOT NULL
              AND p.mapLng IS NOT NULL
            ORDER BY u.fullName
            """)
    List<Profile> findAllMapPins();

}