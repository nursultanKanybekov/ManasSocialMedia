package com.com.manasuniversityecosystem.service.admin;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportService {

    private final UserRepository userRepo;
    private final FacultyRepository facultyRepo;
    private final PasswordEncoder passwordEncoder;

    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional
    public ImportResult importMezuns(MultipartFile file) throws IOException {
        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);

            // Skip header row (row 0)
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String firstName   = cell(row, 0);   // A: name
                    String lastName    = cell(row, 1);   // B: surname
                    String email       = cell(row, 2);   // C: login/email
                    String yearStr     = cell(row, 3);   // D: graduation year
                    String facultyName = cell(row, 4);   // E: faculty
                    String avatarUrl   = cell(row, 5);   // F: image URL
                    String studentNum  = cell(row, 6);   // G: studentNumber
                    String password    = cell(row, 7);   // H: password

                    if (email == null || email.isBlank()) {
                        errors.add("Row " + (i+1) + ": email is empty, skipped.");
                        skipped++;
                        continue;
                    }

                    if (userRepo.existsByEmail(email.trim())) {
                        errors.add("Row " + (i+1) + ": " + email + " already exists, skipped.");
                        skipped++;
                        continue;
                    }

                    String fullName = ((firstName != null ? firstName : "") + " " +
                                       (lastName  != null ? lastName  : "")).trim();
                    if (fullName.isBlank()) fullName = email;

                    Faculty faculty = null;
                    if (facultyName != null && !facultyName.isBlank()) {
                        faculty = facultyRepo.findAll().stream()
                                .filter(f -> f.getName().equalsIgnoreCase(facultyName.trim())
                                          || f.getCode().equalsIgnoreCase(facultyName.trim()))
                                .findFirst().orElse(null);
                        if (faculty == null) {
                            errors.add("Row " + (i+1) + ": Faculty '" + facultyName + "' not found, user added without faculty.");
                        }
                    }

                    Integer year = null;
                    if (yearStr != null && !yearStr.isBlank()) {
                        try { year = Integer.parseInt(yearStr.trim()); }
                        catch (NumberFormatException e) {
                            errors.add("Row " + (i+1) + ": Invalid year '" + yearStr + "', set to null.");
                        }
                    }

                    String rawPassword = (password != null && !password.isBlank()) ? password.trim() : "Manas2025!";

                    Profile profile = Profile.builder()
                            .totalPoints(0)
                            .avatarUrl(avatarUrl != null && !avatarUrl.isBlank() ? avatarUrl.trim() : null)
                            .build();

                    AppUser user = AppUser.builder()
                            .fullName(fullName)
                            .email(email.trim())
                            .passwordHash(passwordEncoder.encode(rawPassword))
                            .role(UserRole.MEZUN)
                            .status(UserStatus.ACTIVE)
                            .faculty(faculty)
                            .graduationYear(year)
                            .studentIdNumber(studentNum != null ? studentNum.trim() : null)
                            .build();
                    user.setProfile(profile);
                    userRepo.save(user);
                    imported++;

                } catch (Exception e) {
                    errors.add("Row " + (i+1) + ": Error - " + e.getMessage());
                    skipped++;
                }
            }
        }

        log.info("Excel import: {} imported, {} skipped", imported, skipped);
        return new ImportResult(imported, skipped, errors);
    }

    private String cell(Row row, int col) {
        Cell c = row.getCell(col);
        if (c == null) return null;
        return switch (c.getCellType()) {
            case STRING  -> c.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default      -> null;
        };
    }
}
