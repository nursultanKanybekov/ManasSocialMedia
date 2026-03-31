package com.com.manasuniversityecosystem.repository.academic;

import com.com.manasuniversityecosystem.domain.entity.academic.LibraryBook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface LibraryBookRepository extends JpaRepository<LibraryBook, UUID> {

    @Query("SELECT b FROM LibraryBook b WHERE b.isPublic = true OR b.faculty.id = :facultyId ORDER BY b.createdAt DESC")
    List<LibraryBook> findVisibleToFaculty(@Param("facultyId") UUID facultyId);

    List<LibraryBook> findByIsPublicTrueOrderByCreatedAtDesc();

    List<LibraryBook> findByCategoryOrderByCreatedAtDesc(LibraryBook.BookCategory category);

    @Query("SELECT b FROM LibraryBook b WHERE " +
            "(b.isPublic = true OR b.faculty.id = :facultyId) AND " +
            "(LOWER(b.title)  LIKE LOWER(CONCAT('%',:q,'%')) OR " +
            " LOWER(b.author) LIKE LOWER(CONCAT('%',:q,'%')) OR " +
            " LOWER(b.isbn)   LIKE LOWER(CONCAT('%',:q,'%'))) " +
            "ORDER BY b.title ASC")
    List<LibraryBook> search(@Param("q") String query, @Param("facultyId") UUID facultyId);

    List<LibraryBook> findByUploadedByIdOrderByCreatedAtDesc(UUID uploaderId);
}