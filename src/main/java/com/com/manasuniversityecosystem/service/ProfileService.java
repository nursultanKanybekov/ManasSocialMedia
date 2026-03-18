package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.ProfileRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.web.dto.profile.ProfileUpdateRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository  profileRepo;
    private final UserRepository     userRepo;
    private final FacultyRepository  facultyRepo;
    private final CloudinaryService  cloudinaryService;
    private final ObjectMapper       objectMapper;

    private static final TypeReference<List<Map<String,String>>> LIST_MAP_TYPE =
            new TypeReference<>() {};

    @Transactional(readOnly = true)
    public Profile getByUserId(UUID userId) {
        return profileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user: " + userId));
    }

    @Transactional
    public Profile update(AppUser user, ProfileUpdateRequest req) {
        Profile profile = getByUserId(user.getId());

        // ── Account fields ────────────────────────────────────
        if (req.getFullName() != null && !req.getFullName().isBlank())
            user.setFullName(req.getFullName().trim());

        if (req.getEmail() != null && !req.getEmail().isBlank()
                && !req.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepo.existsByEmail(req.getEmail()))
                throw new IllegalArgumentException("Email already in use.");
            user.setEmail(req.getEmail().trim().toLowerCase());
        }

        if (req.getFacultyId() != null) {
            Faculty faculty = facultyRepo.findById(req.getFacultyId()).orElse(null);
            if (faculty != null) user.setFaculty(faculty);
        }

        if (req.getGraduationYear() != null)
            user.setGraduationYear(req.getGraduationYear());

        userRepo.save(user);

        // ── Profile basic fields ───────────────────────────────
        if (req.getBio()              != null) profile.setBio(req.getBio());
        if (req.getHeadline()         != null) profile.setHeadline(req.getHeadline());
        if (req.getCurrentJobTitle()  != null) profile.setCurrentJobTitle(req.getCurrentJobTitle());
        if (req.getCurrentCompany()   != null) profile.setCurrentCompany(req.getCurrentCompany());
        if (req.getPhone()            != null) profile.setPhone(req.getPhone());
        if (req.getLocation()         != null) profile.setLocation(req.getLocation());
        if (req.getWebsite()          != null) profile.setWebsite(req.getWebsite());
        if (req.getDateOfBirth()      != null) profile.setDateOfBirth(req.getDateOfBirth());
        if (req.getNationality()      != null) profile.setNationality(req.getNationality());
        if (req.getSkills()           != null) profile.setSkills(req.getSkills());
        if (req.getSocialLinks()      != null) profile.setSocialLinks(req.getSocialLinks());
        if (req.getStudyYear()        != null) profile.setStudyYear(req.getStudyYear());
        if (req.getCanMentor()        != null) profile.setCanMentor(req.getCanMentor());
        if (req.getMentorJobTitle()   != null) profile.setMentorJobTitle(req.getMentorJobTitle());

        // ── Resume JSON sections ───────────────────────────────
        profile.setWorkExperience(parseList(req.getWorkExperienceJson(),  profile.getWorkExperience()));
        profile.setEducationList(parseList(req.getEducationJson(),        profile.getEducationList()));
        profile.setLanguages(parseList(req.getLanguagesJson(),            profile.getLanguages()));
        profile.setCertifications(parseList(req.getCertificationsJson(),  profile.getCertifications()));

        return profileRepo.save(profile);
    }

    private List<Map<String,String>> parseList(String json, List<Map<String,String>> fallback) {
        if (json == null || json.isBlank() || json.equals("[]")) return new ArrayList<>();
        try {
            return objectMapper.readValue(json, LIST_MAP_TYPE);
        } catch (Exception e) {
            log.warn("Could not parse JSON list: {}", e.getMessage());
            return fallback != null ? fallback : new ArrayList<>();
        }
    }

    @Transactional
    public String uploadAvatar(AppUser user, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty.");
        long maxBytes = 5L * 1024 * 1024;
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("File exceeds 5MB limit.");
        try {
            String publicId = "avatar_" + user.getId();
            String url = cloudinaryService.uploadImage(file, "manas/avatars", publicId);
            Profile profile = getByUserId(user.getId());
            profile.setAvatarUrl(url);
            profileRepo.save(profile);
            log.info("Avatar uploaded for user {}: {}", user.getEmail(), url);
            return url;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            log.error("Cloudinary avatar upload error for user {}: {}", user.getEmail(), e.getMessage());
            throw new IOException("Image upload failed: " + e.getMessage(), e);
        }
    }

    @Transactional
    public boolean toggleMentorAvailability(UUID userId) {
        Profile profile = getByUserId(userId);
        boolean newValue = !Boolean.TRUE.equals(profile.getCanMentor());
        profile.setCanMentor(newValue);
        profileRepo.save(profile);
        return newValue;
    }

    @Transactional(readOnly = true)
    public List<Profile> getGlobalLeaderboard(int limit) {
        return profileRepo.findTopProfiles(PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public List<Profile> getFacultyLeaderboard(UUID facultyId, int limit) {
        return profileRepo.findTopByFaculty(facultyId, PageRequest.of(0, limit));
    }
}