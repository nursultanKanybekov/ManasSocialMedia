package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.social.Post;
import com.com.manasuniversityecosystem.domain.enums.PostType;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.social.PostService;
import com.com.manasuniversityecosystem.web.dto.social.CreatePostRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class NewsController {

    private final PostService postService;
    private final UserService userService;

    @GetMapping("/university-news")
    public String universityNews(@RequestParam(defaultValue = "0") int page,
                                  @AuthenticationPrincipal UserDetailsImpl principal,
                                  Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Page<Post> posts = postService.getFeedByType(PostType.UNIVERSITY_NEWS, page, 9);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("posts", posts);
        model.addAttribute("pageTitle", "university_news");
        return "news/university-news";
    }

    @GetMapping("/news")
    public String news(@RequestParam(defaultValue = "0") int page,
                       @AuthenticationPrincipal UserDetailsImpl principal,
                       Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Page<Post> posts = postService.getFeedByType(PostType.NEWS, page, 9);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("posts", posts);
        return "news/news";
    }

    @GetMapping("/articles")
    public String articles(@RequestParam(defaultValue = "0") int page,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Page<Post> posts = postService.getFeedByType(PostType.ARTICLE, page, 9);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("posts", posts);
        return "news/articles";
    }

    @GetMapping("/articles/new")
    public String newArticleForm(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        model.addAttribute("currentUser", currentUser);
        return "news/article-form";
    }

    @PostMapping("/articles/new")
    public String createArticle(@RequestParam String title,
                                 @RequestParam String content,
                                 @RequestParam(defaultValue = "en") String lang,
                                 @AuthenticationPrincipal UserDetailsImpl principal) {
        AppUser author = userService.getById(principal.getId());
        CreatePostRequest req = new CreatePostRequest();
        req.setContent((title.isBlank() ? "" : "**" + title + "**\n\n") + content);
        req.setLang(lang);
        req.setPostType(PostType.ARTICLE);
        postService.createPost(author, req);
        return "redirect:/articles";
    }

    @GetMapping("/articles/{id}")
    public String articleDetail(@PathVariable UUID id,
                                @AuthenticationPrincipal UserDetailsImpl principal,
                                Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        Post post = postService.getById(id);
        boolean liked = postService.isLikedByUser(id, principal.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("post", post);
        model.addAttribute("liked", liked);
        model.addAttribute("comments", postService.getTopLevelComments(id, 0, 20));
        return "news/article-detail";
    }
}
