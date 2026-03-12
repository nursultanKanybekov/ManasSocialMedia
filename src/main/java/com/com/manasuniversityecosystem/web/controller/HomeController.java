package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.competition.CompetitionService;
import com.com.manasuniversityecosystem.service.event.EventService;
import com.com.manasuniversityecosystem.service.gamification.GamificationService;
import com.com.manasuniversityecosystem.service.social.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final UserService userService;
    private final GamificationService gamificationService;
    private final PostService postService;
    private final CompetitionService competitionService;
    private final EventService eventService;

    @GetMapping("/")
    public String root() {
        return "redirect:/main";
    }

    @GetMapping("/main")
    public String mainPage(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("topUsers", gamificationService.getGlobalTop(5));
        model.addAttribute("latestNews", postService.getFeedByType(PostType.NEWS, 0, 4).getContent());
        model.addAttribute("universityNews", postService.getFeedByType(PostType.UNIVERSITY_NEWS, 0, 3).getContent());
        model.addAttribute("latestCompetitions", competitionService.getLatest6());
        model.addAttribute("upcomingEvents", eventService.getUpcoming(4));
        return "main/mainpage";
    }

    @GetMapping("/feed")
    public String feed(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("topUsers", gamificationService.getGlobalTop(5));

        // Load first page of posts server-side so they render on initial page load.
        // This avoids the HTMX /feed/posts round-trip that was silently failing.
        org.springframework.data.domain.Page<com.com.manasuniversityecosystem.domain.entity.social.Post> posts =
                postService.getFeed(0, 10);
        model.addAttribute("initialPosts",    posts.getContent());
        model.addAttribute("initialNextPage", posts.hasNext() ? 1 : -1);
        model.addAttribute("initialLikedIds",
                posts.getContent().stream()
                        .filter(p -> postService.isLikedByUser(p.getId(), principal.getId()))
                        .map(p -> p.getId().toString())
                        .toList());
        model.addAttribute("currentUserId", principal.getId());
        model.addAttribute("isAdmin", principal.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_SUPER_ADMIN")));
        return "feed/feed";
    }

    @GetMapping("/leaderboard")
    public String leaderboard(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("globalTop", gamificationService.getGlobalTop(50));
        if (currentUser.getFaculty() != null) {
            model.addAttribute("facultyTop",
                    gamificationService.getFacultyTop(currentUser.getFaculty().getId(), 20));
        }
        return "leaderboard";
    }
}