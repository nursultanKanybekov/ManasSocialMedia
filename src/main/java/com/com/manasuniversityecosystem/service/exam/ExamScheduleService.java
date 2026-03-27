package com.com.manasuniversityecosystem.service.exam;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.exam.ExamSchedule;
import com.com.manasuniversityecosystem.domain.entity.notification.Notification;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.exam.ExamScheduleRepository;
import com.com.manasuniversityecosystem.repository.notification.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExamScheduleService {

    private final ExamScheduleRepository examRepo;
    private final FacultyRepository facultyRepo;
    private final UserRepository userRepo;
    private final NotificationRepository notifRepo;
    private final SimpMessagingTemplate messagingTemplate;

    // ── Read ──────────────────────────────────────────────────────────────────

    public List<ExamSchedule> getByFaculty(UUID facultyId) {
        return examRepo.findByFacultyIdOrderByExamDateAsc(facultyId);
    }

    public List<ExamSchedule> getTodayByFaculty(UUID facultyId) {
        return examRepo.findByFacultyIdAndExamDateOrderByExamTimeAsc(facultyId, LocalDate.now());
    }

    public List<ExamSchedule> getAll() {
        return examRepo.findAllByOrderByExamDateAscExamTimeAsc();
    }

    public List<ExamSchedule> search(String query) {
        if (query == null || query.isBlank()) return getAll();
        return examRepo.search(query.trim());
    }

    public List<ExamSchedule> getTodayAll() {
        return examRepo.findByExamDateOrderByFacultyIdAscExamTimeAsc(LocalDate.now());
    }

    // ── Import ────────────────────────────────────────────────────────────────

    /**
     * Parse an .xlsx exam schedule and persist it.
     * Replaces existing records for the given faculty if facultyId is provided.
     * If facultyId is null, all parsed rows are saved without clearing.
     */
    @Transactional
    public int importFromExcel(MultipartFile file, UUID facultyId, String semester) throws IOException {
        Faculty faculty = facultyId != null
                ? facultyRepo.findById(facultyId).orElse(null)
                : null;

        if (facultyId != null) {
            examRepo.deleteByFacultyId(facultyId);
        }

        List<ExamSchedule> exams = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(file.getInputStream())) {
            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                String currentProgram = null;
                boolean inDataSection = false;

                for (Row row : sheet) {
                    if (row == null) continue;
                    String col0 = cellStr(row.getCell(0));
                    if (col0.isBlank()) continue;

                    // Detect program header rows (bold/merged, no date in col2)
                    Cell dateCell = row.getCell(2);
                    boolean hasDate = dateCell != null &&
                            (dateCell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(dateCell));

                    // Check if this is a column header row
                    if (col0.toLowerCase().contains("dersin adı") || col0.toLowerCase().contains("ders kodu")) {
                        inDataSection = true;
                        continue;
                    }

                    // Detect program name lines (no date, not a header)
                    if (!hasDate && inDataSection) {
                        // Could be a new program section
                        if (!col0.toLowerCase().contains("dersin") && col0.length() > 10) {
                            currentProgram = col0.trim();
                            inDataSection = false; // reset — next header re-enables
                        }
                        continue;
                    }

                    if (!hasDate) {
                        // Non-data row — could be program name before header
                        if (col0.length() > 10 && !col0.toLowerCase().startsWith("ders")) {
                            currentProgram = col0.trim();
                        }
                        continue;
                    }

                    // Data row
                    String courseFull = col0.trim();
                    String teacherName = cellStr(row.getCell(1));
                    LocalDate examDate = dateCell.getLocalDateTimeCellValue().toLocalDate();
                    String examTime    = cellStr(row.getCell(3));
                    String proctor     = cellStr(row.getCell(4));
                    String examRoom    = cellStr(row.getCell(5));

                    // Split courseCode from courseName e.g. "BLG-102 Grafik ve Animasyon"
                    String courseCode = "";
                    String courseName = courseFull;
                    int spaceIdx = courseFull.indexOf(' ');
                    if (spaceIdx > 0 && spaceIdx < 15) {
                        courseCode = courseFull.substring(0, spaceIdx).trim();
                        courseName = courseFull.substring(spaceIdx).trim();
                    }

                    exams.add(ExamSchedule.builder()
                            .courseCode(courseCode)
                            .courseName(courseName)
                            .teacherName(teacherName)
                            .proctorName(proctor)
                            .examDate(examDate)
                            .examTime(examTime)
                            .examRoom(examRoom)
                            .programName(currentProgram)
                            .faculty(faculty)
                            .semester(semester)
                            .build());
                }
            }
        }

        examRepo.saveAll(exams);
        log.info("Imported {} exam records (faculty={})", exams.size(), facultyId);
        return exams.size();
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void delete(UUID id) {
        examRepo.deleteById(id);
    }

    @Transactional
    public void deleteByFaculty(UUID facultyId) {
        examRepo.deleteByFacultyId(facultyId);
    }

    // ── Notifications ─────────────────────────────────────────────────────────

    /**
     * Runs every day at 08:00 AM (server time).
     * Sends an in-app notification to every STUDENT and TEACHER
     * whose faculty has an exam today.
     */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional
    public void sendTodayExamNotifications() {
        LocalDate today = LocalDate.now();
        List<ExamSchedule> todayExams = getTodayAll();
        if (todayExams.isEmpty()) return;

        // Group exams by faculty
        Map<UUID, List<ExamSchedule>> byFaculty = new LinkedHashMap<>();
        for (ExamSchedule e : todayExams) {
            UUID fid = e.getFaculty() != null ? e.getFaculty().getId() : null;
            byFaculty.computeIfAbsent(fid, k -> new ArrayList<>()).add(e);
        }

        // Notify students and teachers per faculty
        for (Map.Entry<UUID, List<ExamSchedule>> entry : byFaculty.entrySet()) {
            UUID facultyId = entry.getKey();
            List<ExamSchedule> exams = entry.getValue();

            List<AppUser> recipients = facultyId != null
                    ? userRepo.findByFacultyIdAndRoleIn(facultyId,
                    List.of(UserRole.STUDENT, UserRole.TEACHER))
                    : Collections.emptyList();

            for (AppUser user : recipients) {
                // Build summary message
                String summary = exams.size() == 1
                        ? "📅 Exam today: " + exams.get(0).getCourseCode() + " " + exams.get(0).getCourseName()
                        + " at " + exams.get(0).getExamTime() + " — " + exams.get(0).getExamRoom()
                        : "📅 You have " + exams.size() + " exam(s) today. Tap to see the schedule.";

                Notification notif = Notification.builder()
                        .recipient(user)
                        .type(Notification.NotifType.EXAM_TODAY)
                        .message(summary)
                        .link("/exams")
                        .icon("📅")
                        .build();
                notifRepo.save(notif);

                // Push via WebSocket
                pushWs(user.getId(), notif);
            }

            // Also notify teachers whose name appears in this exam's teacherName
            for (ExamSchedule exam : exams) {
                if (exam.getTeacherName() == null || exam.getTeacherName().isBlank()) continue;
                notifyTeacherByName(exam);
            }
        }

        log.info("Sent today-exam notifications for {} faculty groups", byFaculty.size());
    }

    /** Called manually (e.g. after import) to immediately notify if import day == today */
    @Async
    @Transactional
    public void sendTodayExamNotificationsAsync() {
        sendTodayExamNotifications();
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void notifyTeacherByName(ExamSchedule exam) {
        List<ExamSchedule> todayTeacherExams =
                examRepo.findTodayExamsByTeacherName(LocalDate.now(), exam.getTeacherName());
        if (todayTeacherExams.isEmpty()) return;

        // Find matching teacher account by full name (best-effort)
        userRepo.findAll().stream()
                .filter(u -> u.getRole() == UserRole.TEACHER
                        && u.getFullName() != null
                        && nameMatch(u.getFullName(), exam.getTeacherName()))
                .forEach(teacher -> {
                    boolean alreadySent = notifRepo
                            .existsByRecipientIdAndTypeAndCreatedAtAfter(
                                    teacher.getId(),
                                    Notification.NotifType.EXAM_TODAY,
                                    LocalDate.now().atStartOfDay());
                    if (alreadySent) return;

                    String msg = "📅 You are invigilating/teaching: "
                            + exam.getCourseCode() + " " + exam.getCourseName()
                            + " at " + exam.getExamTime() + " — " + exam.getExamRoom();
                    Notification notif = Notification.builder()
                            .recipient(teacher)
                            .type(Notification.NotifType.EXAM_TODAY)
                            .message(msg)
                            .link("/exams")
                            .icon("📅")
                            .build();
                    notifRepo.save(notif);
                    pushWs(teacher.getId(), notif);
                });
    }

    private boolean nameMatch(String fullName, String teacherName) {
        if (fullName == null || teacherName == null) return false;
        String a = fullName.toLowerCase().trim();
        String b = teacherName.toLowerCase().trim();
        return a.equals(b) || a.contains(b) || b.contains(a);
    }

    private void pushWs(UUID userId, Notification notif) {
        try {
            messagingTemplate.convertAndSendToUser(
                    userId.toString(),
                    "/queue/notifications",
                    Map.of(
                            "id",      notif.getId() != null ? notif.getId().toString() : "",
                            "message", notif.getMessage(),
                            "link",    notif.getLink() != null ? notif.getLink() : "/exams",
                            "icon",    notif.getIcon()
                    ));
        } catch (Exception ex) {
            log.warn("WS push failed for user {}: {}", userId, ex.getMessage());
        }
    }

    private String cellStr(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING  -> cell.getStringCellValue().trim();
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
                    : String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> {
                try { yield cell.getStringCellValue().trim(); }
                catch (Exception e) { yield String.valueOf(cell.getNumericCellValue()); }
            }
            default -> "";
        };
    }
}