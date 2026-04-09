package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.repository.gamification.UserBadgeRepository;
import com.com.manasuniversityecosystem.repository.career.JobApplicationRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.MezunService;
import com.com.manasuniversityecosystem.service.ProfileService;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.career.JobService;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequestMapping("/mezun")
@RequiredArgsConstructor
public class MezunController {

    private final MezunService           mezunService;
    private final UserService            userService;
    private final ProfileService         profileService;
    private final FacultyRepository      facultyRepo;
    private final UserBadgeRepository    userBadgeRepo;
    private final PostService            postService;
    private final JobService             jobService;
    private final JobApplicationRepository jobApplicationRepo;

    @GetMapping
    public String catalog(@RequestParam(required = false) UUID facultyId,
                          @RequestParam(required = false) Integer graduationYear,
                          @RequestParam(required = false) String name,
                          @RequestParam(defaultValue = "0") int page,
                          @RequestParam(defaultValue = "name") String sortBy,
                          @RequestParam(defaultValue = "asc") String sortDir,
                          @AuthenticationPrincipal UserDetailsImpl principal,
                          Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Page<AppUser> mezunPage = mezunService.search(facultyId, graduationYear, name, page, sortBy, sortDir);

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("mezunPage", mezunPage);
        model.addAttribute("faculties", facultyRepo.findAll());
        model.addAttribute("selectedFaculty", facultyId);
        model.addAttribute("selectedYear", graduationYear);
        model.addAttribute("searchName", name);
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", "asc".equals(sortDir) ? "desc" : "asc");
        return "mezun/catalog";
    }

    @GetMapping("/{id}")
    public String profile(@PathVariable UUID id,
                          @RequestParam(defaultValue = "en") String lang,
                          @AuthenticationPrincipal UserDetailsImpl principal,
                          Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        AppUser mezun       = userService.getById(id);
        Profile profile     = profileService.getByUserId(id);

        boolean isOwn = principal.getId().equals(id);

        // Can view CV: self, or employer/admin with a shared job application
        boolean viewerIsEmployer = false;
        try {
            viewerIsEmployer = currentUser.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.EMPLOYER
                    || currentUser.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.ADMIN
                    || currentUser.getRole() == com.com.manasuniversityecosystem.domain.enums.UserRole.SUPER_ADMIN;
        } catch (Exception ignored) {}
        boolean canViewCv = isOwn || (viewerIsEmployer &&
                jobApplicationRepo.existsByApplicantIdAndJobPosterId(id, principal.getId()));

        model.addAttribute("currentUser",  currentUser);
        model.addAttribute("mezun",        mezun);
        model.addAttribute("profile",      profile);
        model.addAttribute("userBadges",   userBadgeRepo.findByUserIdWithBadge(id));
        model.addAttribute("postCount",    postService.countUserPosts(id));
        model.addAttribute("recentPosts",  postService.getUserPosts(id, 0, 6).getContent());
        model.addAttribute("isOwn",        isOwn);
        model.addAttribute("canViewCv",    canViewCv);
        model.addAttribute("lang",         lang);
        return "mezun/profile";
    }
}

