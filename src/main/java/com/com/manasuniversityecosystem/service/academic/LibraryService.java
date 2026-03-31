package com.com.manasuniversityecosystem.service.academic;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.academic.LibraryBook;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.LibraryBookRepository;
import com.com.manasuniversityecosystem.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LibraryService {

    private final LibraryBookRepository libraryRepo;
    private final UserRepository        userRepo;
    private final FacultyRepository     facultyRepo;
    private final CloudinaryService     cloudinary;

    public List<LibraryBook> getBooksVisibleTo(UUID facultyId) {
        if (facultyId == null) return libraryRepo.findByIsPublicTrueOrderByCreatedAtDesc();
        return libraryRepo.findVisibleToFaculty(facultyId);
    }

    public List<LibraryBook> search(String query, UUID facultyId) {
        if (query == null || query.isBlank()) return getBooksVisibleTo(facultyId);
        return libraryRepo.search(query.trim(),
                facultyId != null ? facultyId : UUID.fromString("00000000-0000-0000-0000-000000000000"));
    }

    public List<LibraryBook> getByCategory(LibraryBook.BookCategory category) {
        return libraryRepo.findByCategoryOrderByCreatedAtDesc(category);
    }

    public LibraryBook getById(UUID id) {
        return libraryRepo.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Book not found: " + id));
    }

    @Transactional
    public LibraryBook upload(String title, String author, String description,
                              LibraryBook.BookCategory category,
                              String isbn, String publishYear, String language,
                              boolean isPublic, UUID facultyId, UUID uploaderId,
                              MultipartFile bookFile, MultipartFile coverFile) throws IOException {

        AppUser uploader = userRepo.findById(uploaderId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        Faculty faculty = facultyId != null
                ? facultyRepo.findById(facultyId).orElse(null) : null;

        String fileUrl = null;
        if (bookFile != null && !bookFile.isEmpty()) {
            fileUrl = cloudinary.uploadDocument(bookFile, "manas/library/books");
        }
        String coverUrl = null;
        if (coverFile != null && !coverFile.isEmpty()) {
            coverUrl = cloudinary.uploadImage(coverFile, "manas/library/covers", null);
        }

        return libraryRepo.save(LibraryBook.builder()
                .title(title.trim())
                .author(author)
                .description(description)
                .category(category)
                .isbn(isbn)
                .publishYear(publishYear)
                .language(language)
                .isPublic(isPublic)
                .faculty(faculty)
                .uploadedBy(uploader)
                .fileUrl(fileUrl)
                .coverUrl(coverUrl)
                .build());
    }

    @Transactional
    public void incrementDownload(UUID bookId) {
        libraryRepo.findById(bookId).ifPresent(b -> {
            b.setDownloadCount(b.getDownloadCount() + 1);
            libraryRepo.save(b);
        });
    }

    @Transactional
    public void delete(UUID bookId, UUID requesterId) {
        LibraryBook book = getById(bookId);
        AppUser requester = userRepo.findById(requesterId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        boolean isAdmin = requester.getRole().name().contains("ADMIN")
                || requester.getRole().name().equals("SECRETARY");
        if (!book.getUploadedBy().getId().equals(requesterId) && !isAdmin) {
            throw new SecurityException("Not allowed to delete this book");
        }
        libraryRepo.deleteById(bookId);
    }
}