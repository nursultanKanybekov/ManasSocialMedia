package com.com.manasuniversityecosystem.repository;

import com.com.manasuniversityecosystem.domain.entity.Faculty;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FacultyRepository extends JpaRepository<Faculty, UUID> {

    Optional<Faculty> findByCode(String code);

    List<Faculty> findAllByOrderByNameAsc();

    boolean existsByCode(String code);
}
