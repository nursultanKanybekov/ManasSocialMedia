package com.com.manasuniversityecosystem.repository;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<AppUser, UUID> {

    Optional<AppUser> findByEmail(String email);

    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile WHERE u.email = :email")
    Optional<AppUser> findByEmailWithProfile(@Param("email") String email);

    boolean existsByEmail(String email);

    List<AppUser> findByRole(UserRole role);

    List<AppUser> findByStatus(UserStatus status);

    List<AppUser> findByRoleAndStatus(UserRole role, UserStatus status);

    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile WHERE u.id = :id")
    Optional<AppUser> findByIdWithProfile(@Param("id") UUID id);

    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile " +
            "WHERE u.faculty.id = :facultyId AND u.role = :role AND u.status = 'ACTIVE'")
    List<AppUser> findActiveByFacultyAndRole(
            @Param("facultyId") UUID facultyId,
            @Param("role") UserRole role);

    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile p " +
            "WHERE u.role = :role " +
            "AND u.status = com.com.manasuniversityecosystem.domain.enums.UserStatus.ACTIVE " +
            "AND (:facultyId IS NULL OR u.faculty.id = :facultyId) " +
            "ORDER BY p.totalPoints DESC")
    List<AppUser> findMentorsByFacultyAndSkills(
            @Param("facultyId") UUID facultyId,
            @Param("role") UserRole role);

    @Query("SELECT COUNT(u) FROM AppUser u WHERE u.faculty.id = :facultyId AND u.role = :role")
    long countByFacultyAndRole(@Param("facultyId") UUID facultyId, @Param("role") UserRole role);

    List<AppUser> findByFacultyIdAndStatus(UUID facultyId, UserStatus status);

    // Used by chat/share — active users only, excludes self
    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile p " +
            "WHERE u.id <> :excludeId " +
            "AND u.status = com.com.manasuniversityecosystem.domain.enums.UserStatus.ACTIVE " +
            "AND (LOWER(CAST(u.fullName AS string)) LIKE :query OR LOWER(u.email) LIKE :query) " +
            "ORDER BY u.fullName")
    List<AppUser> searchByNameOrEmail(@Param("query") String query, @Param("excludeId") UUID excludeId);

    // Used by admin panel — all users, all statuses, no exclusions
    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile p " +
            "WHERE (LOWER(CAST(u.fullName AS string)) LIKE :query OR LOWER(u.email) LIKE :query) " +
            "ORDER BY u.fullName")
    List<AppUser> adminSearchByNameOrEmail(@Param("query") String query);

    @Query("SELECT u FROM AppUser u LEFT JOIN FETCH u.profile p " +
            "WHERE u.role = 'MEZUN' " +
            "AND u.status = com.com.manasuniversityecosystem.domain.enums.UserStatus.ACTIVE " +
            "AND (:facultyId IS NULL OR u.faculty.id = :facultyId) " +
            "AND (:graduationYear IS NULL OR u.graduationYear = :graduationYear) " +
            "AND (:name IS NULL OR LOWER(CAST(u.fullName AS string)) LIKE :name) ")
    Page<AppUser> searchMezun(@Param("facultyId") UUID facultyId,
                              @Param("graduationYear") Integer graduationYear,
                              @Param("name") String name,
                              Pageable pageable);
}