package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.SecretaryService;
import com.com.manasuniversityecosystem.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.UUID;

@Controller
@RequestMapping("/secretary")
@PreAuthorize("hasAnyRole('SECRETARY','ADMIN')")
@RequiredArgsConstructor
@Slf4j
public class SecretaryController {

    private final SecretaryService secretaryService;
    private final UserService userService;

    // GET /secretary  — validation queue
    @GetMapping
    public String validationQueue(Model model) {
        model.addAttribute("pending",      secretaryService.getPendingValidations());
        model.addAttribute("pendingCount", secretaryService.countPending());
        return "secretary/queue";
    }

    // POST /secretary/approve/{validationId}
    @PostMapping("/approve/{id}")
    public String approve(@PathVariable("id") UUID validationId,
                          @AuthenticationPrincipal UserDetailsImpl principal,
                          RedirectAttributes ra) {
        AppUser secretary = userService.getById(principal.getId());
        try {
            secretaryService.approve(validationId, secretary);
            ra.addFlashAttribute("successMsg", "secretary.approved");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/secretary";
    }

    // POST /secretary/reject/{validationId}
    @PostMapping("/reject/{id}")
    public String reject(@PathVariable("id") UUID validationId,
                         @RequestParam(required = false) String reason,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra) {
        AppUser secretary = userService.getById(principal.getId());
        try {
            secretaryService.reject(validationId, secretary,
                    reason != null ? reason : "No reason provided.");
            ra.addFlashAttribute("successMsg", "secretary.rejected");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("errorMsg", e.getMessage());
        }
        return "redirect:/secretary";
    }
}