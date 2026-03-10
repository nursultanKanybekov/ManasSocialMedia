package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MezunService {

    private final UserRepository userRepo;

    public Page<AppUser> search(UUID facultyId, Integer graduationYear, String name,
                                 int page, String sortBy, String sortDir) {
        Sort sort = buildSort(sortBy, sortDir);
        // Pre-lowercase the name and wrap with % here to avoid lower(bytea) in PostgreSQL
        String namePattern = (name != null && !name.isBlank())
                ? "%" + name.trim().toLowerCase() + "%" : null;
        return userRepo.searchMezun(facultyId, graduationYear, namePattern,
                PageRequest.of(page, 20, sort));
    }

    private Sort buildSort(String sortBy, String sortDir) {
        boolean asc = !"desc".equalsIgnoreCase(sortDir);
        Sort.Direction dir = asc ? Sort.Direction.ASC : Sort.Direction.DESC;
        return switch (sortBy == null ? "name" : sortBy) {
            case "year"    -> Sort.by(dir, "graduationYear");
            case "faculty" -> Sort.by(dir, "faculty.name");
            case "points"  -> Sort.by(dir, "profile.totalPoints");
            default        -> Sort.by(dir, "fullName");
        };
    }
}
