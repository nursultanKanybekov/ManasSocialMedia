package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.MezunService;
import com.com.manasuniversityecosystem.service.UserService;
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

    private final MezunService mezunService;
    private final UserService userService;
    private final FacultyRepository facultyRepo;

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
                          @AuthenticationPrincipal UserDetailsImpl principal,
                          Model model) {
        AppUser currentUser = userService.getById(principal.getId());
        AppUser mezun = userService.getById(id);
        model.addAttribute("currentUser", currentUser);
        model.addAttribute("mezun", mezun);
        return "mezun/profile";
    }
}
