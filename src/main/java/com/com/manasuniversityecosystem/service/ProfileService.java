package com.com.manasuniversityecosystem.service;


import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.repository.ProfileRepository;
import com.com.manasuniversityecosystem.web.dto.profile.ProfileUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileService {

    private final ProfileRepository profileRepo;
    private final CloudinaryService cloudinaryService;

    @Transactional(readOnly = true)
    public Profile getByUserId(UUID userId) {
        return profileRepo.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for user: " + userId));
    }

    @Transactional
    public Profile update(AppUser user, ProfileUpdateRequest req) {
        Profile profile = getByUserId(user.getId());
        if (req.getBio() != null)       profile.setBio(req.getBio());
        if (req.getHeadline() != null)  profile.setHeadline(req.getHeadline());
        if (req.getSkills() != null)    profile.setSkills(req.getSkills());
        if (req.getSocialLinks() != null) profile.setSocialLinks(req.getSocialLinks());
        if (req.getCanMentor() != null)      profile.setCanMentor(req.getCanMentor());
        if (req.getMentorJobTitle() != null)  profile.setMentorJobTitle(req.getMentorJobTitle());
        if (req.getCurrentJobTitle() != null) profile.setCurrentJobTitle(req.getCurrentJobTitle());
        if (req.getCurrentCompany() != null)  profile.setCurrentCompany(req.getCurrentCompany());

        // Also update user full name if provided
        if (req.getFullName() != null && !req.getFullName().isBlank()) {
            user.setFullName(req.getFullName());
        }
        return profileRepo.save(profile);
    }

    @Transactional
    public String uploadAvatar(AppUser user, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("File is empty.");

        long maxBytes = 5L * 1024 * 1024; // 5 MB
        if (file.getSize() > maxBytes) throw new IllegalArgumentException("File exceeds 5MB limit.");

        try {
            // Use user ID as public_id so re-uploading replaces the same Cloudinary asset
            String publicId = "avatar_" + user.getId();
            String url = cloudinaryService.uploadImage(file, "manas/avatars", publicId);

            Profile profile = getByUserId(user.getId());
            profile.setAvatarUrl(url);
            profileRepo.save(profile);

            log.info("Avatar uploaded to Cloudinary for user {}: {}", user.getEmail(), url);
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