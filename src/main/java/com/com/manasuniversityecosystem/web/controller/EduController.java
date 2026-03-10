package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.edu.Quiz;
import com.com.manasuniversityecosystem.domain.entity.edu.QuizAttempt;
import com.com.manasuniversityecosystem.domain.entity.edu.Tutorial;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.UserService;
import com.com.manasuniversityecosystem.service.edu.EduService;
import com.com.manasuniversityecosystem.web.dto.edu.QuizSubmitRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/edu")
@RequiredArgsConstructor
@Slf4j
public class EduController {

    private final EduService eduService;
    private final UserService userService;

    // GET /edu  — tutorial list with search
    @GetMapping
    public String tutorialList(@RequestParam(defaultValue = "0")  int    page,
                               @RequestParam(required = false)    String category,
                               @RequestParam(required = false)    String q,
                               @RequestParam(required = false)    String mediaType,
                               @RequestParam(defaultValue = "en") String lang,
                               Model model) {
        Page<Tutorial> tutorials = eduService.searchTutorials(q, mediaType, category, page);

        model.addAttribute("tutorials",  tutorials);
        model.addAttribute("categories", eduService.getAllCategories());
        model.addAttribute("selected",   category);
        model.addAttribute("lang",       lang);
        model.addAttribute("q",          q);
        model.addAttribute("mediaType",  mediaType);
        return "edu/tutorials";
    }

    // GET /edu/new  — add tutorial form (MEZUN + ADMIN)
    @GetMapping("/new")
    @PreAuthorize("hasAnyRole('MEZUN','ADMIN')")
    public String newTutorialForm(@AuthenticationPrincipal UserDetailsImpl principal, Model model) {
        model.addAttribute("categories", eduService.getAllCategories());
        model.addAttribute("mediaTypes", Tutorial.MediaType.values());
        return "edu/tutorial-form";
    }

    // POST /edu/new  — save tutorial
    @PostMapping("/new")
    @PreAuthorize("hasAnyRole('MEZUN','ADMIN')")
    public String createTutorial(@RequestParam String title,
                                  @RequestParam(required = false) String content,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String mediaUrl,
                                  @RequestParam(defaultValue = "TEXT") String mediaType,
                                  @RequestParam(required = false) String description,
                                  @AuthenticationPrincipal UserDetailsImpl principal,
                                  RedirectAttributes ra) {
        AppUser author = userService.getById(principal.getId());
        Tutorial.MediaType mt;
        try { mt = Tutorial.MediaType.valueOf(mediaType.toUpperCase()); }
        catch (Exception e) { mt = Tutorial.MediaType.TEXT; }

        Tutorial saved = eduService.createTutorial(author, title, content, category, mediaUrl, mt, description);
        ra.addFlashAttribute("success", "Tutorial published successfully!");
        return "redirect:/edu/tutorial/" + saved.getId();
    }

    // GET /edu/tutorial/{id}  — view tutorial
    @GetMapping("/tutorial/{id}")
    public String viewTutorial(@PathVariable UUID id,
                               @RequestParam(defaultValue = "en") String lang,
                               @AuthenticationPrincipal UserDetailsImpl principal,
                               Model model) {
        Tutorial tutorial = eduService.viewTutorial(id);
        model.addAttribute("tutorial", tutorial);
        model.addAttribute("lang",     lang);
        return "edu/tutorial-detail";
    }

    // GET /edu/quiz/{id}  — take quiz
    @GetMapping("/quiz/{id}")
    public String quizPage(@PathVariable UUID id,
                           @RequestParam(defaultValue = "en") String lang,
                           @AuthenticationPrincipal UserDetailsImpl principal,
                           Model model) {
        Quiz quiz = eduService.getQuizWithQuestions(id);
        boolean alreadyPassed = eduService.hasPassed(id, principal.getId());

        model.addAttribute("quiz",          quiz);
        model.addAttribute("lang",          lang);
        model.addAttribute("alreadyPassed", alreadyPassed);
        model.addAttribute("bestAttempt",   eduService.getBestAttempt(id, principal.getId()));
        return "edu/quiz";
    }

    // POST /edu/quiz/{id}/submit
    @PostMapping("/quiz/{id}/submit")
    public String submitQuiz(@PathVariable UUID id,
                             @ModelAttribute QuizSubmitRequest req,
                             @AuthenticationPrincipal UserDetailsImpl principal,
                             RedirectAttributes ra) {
        AppUser user = userService.getById(principal.getId());
        QuizAttempt attempt = eduService.submitQuiz(user, id, req);

        ra.addFlashAttribute("score",  attempt.getScore());
        ra.addFlashAttribute("passed", attempt.getPassed());
        return "redirect:/edu/quiz/" + id + "/result";
    }

    // GET /edu/quiz/{id}/result
    @GetMapping("/quiz/{id}/result")
    public String quizResult(@PathVariable UUID id, Model model) {
        model.addAttribute("quizId", id);
        return "edu/quiz-result";
    }
}
