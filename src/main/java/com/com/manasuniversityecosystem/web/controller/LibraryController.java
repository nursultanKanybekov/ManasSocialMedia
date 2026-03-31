package com.com.manasuniversityecosystem.web.controller;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.academic.LibraryBook;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.security.UserDetailsImpl;
import com.com.manasuniversityecosystem.service.academic.LibraryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/library")
@RequiredArgsConstructor
public class LibraryController {

    private final LibraryService    libraryService;
    private final UserRepository    userRepo;
    private final FacultyRepository facultyRepo;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String list(@AuthenticationPrincipal UserDetailsImpl principal,
                       @RequestParam(required = false) String q,
                       @RequestParam(required = false) String category,
                       Model model) {
        AppUser user = userRepo.findById(principal.getId()).orElseThrow();
        UUID facultyId = user.getFaculty() != null ? user.getFaculty().getId() : null;

        List<LibraryBook> books;
        if (q != null && !q.isBlank()) {
            books = libraryService.search(q, facultyId);
        } else if (category != null && !category.isBlank()) {
            books = libraryService.getByCategory(LibraryBook.BookCategory.valueOf(category));
        } else {
            books = libraryService.getBooksVisibleTo(facultyId);
        }

        boolean canUpload = user.getRole() == UserRole.TEACHER
                || user.getRole() == UserRole.ADMIN
                || user.getRole() == UserRole.SUPER_ADMIN
                || user.getRole() == UserRole.SECRETARY;

        model.addAttribute("books", books);
        model.addAttribute("q", q);
        model.addAttribute("selectedCategory", category);
        model.addAttribute("categories", LibraryBook.BookCategory.values());
        model.addAttribute("allFaculties", facultyRepo.findAllByOrderByNameAsc());
        model.addAttribute("canUpload", canUpload);
        model.addAttribute("currentUser", user);
        return "library/list";
    }

    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN','SECRETARY')")
    public String upload(@RequestParam String title,
                         @RequestParam(defaultValue = "") String author,
                         @RequestParam(defaultValue = "") String description,
                         @RequestParam LibraryBook.BookCategory category,
                         @RequestParam(defaultValue = "") String isbn,
                         @RequestParam(defaultValue = "") String publishYear,
                         @RequestParam(defaultValue = "English") String language,
                         @RequestParam(defaultValue = "true") boolean isPublic,
                         @RequestParam(required = false) UUID facultyId,
                         @RequestParam(required = false) MultipartFile bookFile,
                         @RequestParam(required = false) MultipartFile coverFile,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra) {
        try {
            libraryService.upload(title, author, description, category,
                    isbn, publishYear, language, isPublic, facultyId,
                    principal.getId(), bookFile, coverFile);
            ra.addFlashAttribute("success", "✅ Book uploaded: " + title);
        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ Upload failed: " + e.getMessage());
        }
        return "redirect:/library";
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN','SUPER_ADMIN','SECRETARY')")
    public String delete(@PathVariable UUID id,
                         @AuthenticationPrincipal UserDetailsImpl principal,
                         RedirectAttributes ra) {
        try {
            libraryService.delete(id, principal.getId());
            ra.addFlashAttribute("success", "Book removed.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "❌ " + e.getMessage());
        }
        return "redirect:/library";
    }

    @GetMapping("/{id}/download")
    @PreAuthorize("isAuthenticated()")
    public String download(@PathVariable UUID id) {
        LibraryBook book = libraryService.getById(id);
        libraryService.incrementDownload(id);
        // Redirect to Cloudinary URL directly (no proxy needed)
        return "redirect:" + book.getFileUrl();
    }
}