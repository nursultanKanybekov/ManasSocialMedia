package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.entity.Profile;
import com.com.manasuniversityecosystem.domain.entity.career.JobListing;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnalyticsExcelService {

    private final AnalyticsService  analyticsService;
    private final UserRepository    userRepo;
    private final FacultyRepository facultyRepo;
    private final PasswordEncoder   passwordEncoder;

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    private static final DateTimeFormatter DY = DateTimeFormatter.ofPattern("yyyy");

    // ══════════════════════════════════════════════════════════════════
    //  ADMIN  —  full platform export
    // ══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] exportAdminAnalytics() throws IOException {
        AnalyticsService.PlatformStats s = analyticsService.getPlatformStats();

        // Fetch full user lists
        List<AppUser> allStudents  = userRepo.findByRole(UserRole.STUDENT);
        List<AppUser> allTeachers  = userRepo.findByRole(UserRole.TEACHER);
        List<AppUser> allMezuns    = userRepo.findByRole(UserRole.MEZUN);
        List<AppUser> allEmployers = userRepo.findByRole(UserRole.EMPLOYER);
        List<AppUser> allFacAdmins = userRepo.findByRole(UserRole.FACULTY_ADMIN);
        List<AppUser> allAdmins    = userRepo.findByRole(UserRole.ADMIN);
        List<AppUser> allSecretaries = userRepo.findByRole(UserRole.SECRETARY);
        List<AppUser> pendingUsers = userRepo.findByStatus(UserStatus.PENDING);
        List<AppUser> suspended    = userRepo.findByStatus(UserStatus.SUSPENDED);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            // ── 1. SUMMARY KPIs ────────────────────────────────────────
            {
                Sheet sh = createSheet(wb, "📊 Summary KPIs");
                int r = titleRow(sh, st, "UNIVERSITY ANALYTICS — FULL SUMMARY", 2);
                r = kpiSection(sh, st, r, "👥 USERS");
                r = kv(sh, st, r, "Total Users",          s.totalUsers);
                r = kv(sh, st, r, "Students",             s.totalStudents);
                r = kv(sh, st, r, "Teachers",             s.totalTeachers);
                r = kv(sh, st, r, "Alumni (Mezun)",       s.totalMezuns);
                r = kv(sh, st, r, "Employers",            s.totalEmployers);
                r = kv(sh, st, r, "Faculty Admins",       s.totalFacultyAdmins);
                r = kv(sh, st, r, "Admins",               s.totalAdmins);
                r = kv(sh, st, r, "Secretaries",          s.totalSecretaries);
                r++;
                r = kpiSection(sh, st, r, "🟢 STATUS");
                r = kv(sh, st, r, "Active Users",         s.activeUsers);
                r = kv(sh, st, r, "Pending Approval",     s.pendingUsers);
                r = kv(sh, st, r, "Suspended",            s.suspendedUsers);
                r++;
                r = kpiSection(sh, st, r, "💼 CAREER");
                r = kv(sh, st, r, "Total Jobs Posted",    s.totalJobs);
                r = kv(sh, st, r, "Active Jobs",          s.activeJobs);
                r = kv(sh, st, r, "Job Applications",     s.totalApplications);
                r++;
                r = kpiSection(sh, st, r, "🎓 ACADEMIC");
                r = kv(sh, st, r, "Courses",              s.totalCourses);
                r = kv(sh, st, r, "Feed Posts",           s.totalPosts);
                r = kv(sh, st, r, "Faculties",            s.totalFaculties);
                r++;
                r = kpiSection(sh, st, r, "⚧ GENDER — STUDENTS");
                for (var e : s.studentGenderBreakdown.entrySet())
                    r = kv(sh, st, r, e.getKey(), e.getValue());
                r++;
                r = kpiSection(sh, st, r, "⚧ GENDER — ALUMNI");
                for (var e : s.mezunGenderBreakdown.entrySet())
                    r = kv(sh, st, r, e.getKey(), e.getValue());
                sh.setColumnWidth(0, 11000); sh.setColumnWidth(1, 6000);
            }

            // ── 2. FACULTY BREAKDOWN ───────────────────────────────────
            {
                Sheet sh = createSheet(wb, "🏛️ Faculty Breakdown");
                String[] cols = {"Faculty Name","Code","Students","Teachers","Alumni","Faculty Admins","Courses","Total Members","% of Total Users"};
                int[] w = {10000,4000,4500,4500,4500,6000,4500,6000,6000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var row : s.facultyStats) {
                    Row dr = sh.createRow(r);
                    long total = row.students + row.teachers + row.mezuns;
                    double pct = s.totalUsers > 0 ? (total * 100.0 / s.totalUsers) : 0;
                    c(dr,0,row.faculty.getName(),st.body(r)); c(dr,1,row.faculty.getCode(),st.body(r));
                    n(dr,2,row.students,st.num(r));   n(dr,3,row.teachers,st.num(r));
                    n(dr,4,row.mezuns,st.num(r));     n(dr,5,row.facultyAdmins,st.num(r));
                    n(dr,6,row.courses,st.num(r));    n(dr,7,total,st.num(r));
                    c(dr,8, String.format("%.1f%%", pct), st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 3. ALL STUDENTS ────────────────────────────────────────
            writeFullUserSheet(wb, st, "🎒 Students", allStudents);

            // ── 4. ALL TEACHERS ───────────────────────────────────────
            writeFullUserSheet(wb, st, "👨‍🏫 Teachers", allTeachers);

            // ── 5. ALL ALUMNI ──────────────────────────────────────────
            writeAlumniSheet(wb, st, "🎓 Alumni", allMezuns);

            // ── 6. ALL EMPLOYERS ──────────────────────────────────────
            writeEmployerSheet(wb, st, "🏢 Employers", allEmployers);

            // ── 7. STAFF (Admins, Faculty Admins, Secretaries) ────────
            {
                Sheet sh = createSheet(wb, "🔑 Staff");
                String[] cols = {"Full Name","Email","Role","Faculty","Status","Created At","University Verified"};
                int[] w = {8000,9000,6000,9000,5000,7000,6000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                List<AppUser> staff = new ArrayList<>();
                staff.addAll(allFacAdmins); staff.addAll(allAdmins); staff.addAll(allSecretaries);
                for (AppUser u : staff) {
                    Row dr = sh.createRow(r);
                    c(dr,0,u.getFullName(),st.body(r));
                    c(dr,1,u.getEmail(),st.body(r));
                    c(dr,2,u.getRole().name(),st.body(r));
                    c(dr,3,u.getFaculty()!=null?u.getFaculty().getName():"—",st.body(r));
                    c(dr,4,u.getStatus().name(),st.body(r));
                    c(dr,5,u.getCreatedAt()!=null?u.getCreatedAt().format(DT):"—",st.body(r));
                    c(dr,6,u.isUniversityVerified()?"Yes":"No",st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 8. PENDING USERS ──────────────────────────────────────
            {
                Sheet sh = createSheet(wb, "⏳ Pending Users");
                String[] cols = {"Full Name","Email","Role","Faculty","Created At"};
                int[] w = {8000,9000,6000,9000,7000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : pendingUsers) {
                    Row dr = sh.createRow(r);
                    c(dr,0,u.getFullName(),st.body(r)); c(dr,1,u.getEmail(),st.body(r));
                    c(dr,2,u.getRole().name(),st.body(r));
                    c(dr,3,u.getFaculty()!=null?u.getFaculty().getName():"—",st.body(r));
                    c(dr,4,u.getCreatedAt()!=null?u.getCreatedAt().format(DT):"—",st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 9. SUSPENDED USERS ────────────────────────────────────
            {
                Sheet sh = createSheet(wb, "🚫 Suspended Users");
                String[] cols = {"Full Name","Email","Role","Faculty","Created At"};
                int[] w = {8000,9000,6000,9000,7000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : suspended) {
                    Row dr = sh.createRow(r);
                    c(dr,0,u.getFullName(),st.body(r)); c(dr,1,u.getEmail(),st.body(r));
                    c(dr,2,u.getRole().name(),st.body(r));
                    c(dr,3,u.getFaculty()!=null?u.getFaculty().getName():"—",st.body(r));
                    c(dr,4,u.getCreatedAt()!=null?u.getCreatedAt().format(DT):"—",st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 10. ALUMNI BY GRADUATION YEAR ─────────────────────────
            {
                Sheet sh = createSheet(wb, "📅 Alumni by Grad Year");
                String[] cols = {"Graduation Year","Count","% of All Alumni"};
                int[] w = {6000,5000,6000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var e : s.mezunsByYear.entrySet()) {
                    Row dr = sh.createRow(r);
                    double pct = s.totalMezuns > 0 ? (e.getValue() * 100.0 / s.totalMezuns) : 0;
                    c(dr,0,String.valueOf(e.getKey()),st.body(r));
                    n(dr,1,e.getValue(),st.num(r));
                    c(dr,2,String.format("%.1f%%",pct),st.body(r));
                    r++;
                }
            }

            // ── 11. ALUMNI BY WORKPLACE ───────────────────────────────
            {
                Sheet sh = createSheet(wb, "💼 Alumni by Workplace");
                String[] cols = {"Workplace / Company","Alumni Count","Alumni Names","Emails","Faculties"};
                int[] w = {9000,5000,12000,10000,9000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var e : s.mezunsByWorkplace.entrySet()) {
                    Row dr = sh.createRow(r);
                    String names = e.getValue().stream().map(AppUser::getFullName).collect(Collectors.joining(", "));
                    String emails = e.getValue().stream().map(AppUser::getEmail).collect(Collectors.joining(", "));
                    String facs = e.getValue().stream()
                            .map(u -> u.getFaculty()!=null?u.getFaculty().getCode():"—")
                            .distinct().collect(Collectors.joining(", "));
                    c(dr,0,e.getKey(),st.body(r));
                    n(dr,1,e.getValue().size(),st.num(r));
                    c(dr,2,names,st.body(r));
                    c(dr,3,emails,st.body(r));
                    c(dr,4,facs,st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 12. ALUMNI WORLD MAP ──────────────────────────────────
            {
                Sheet sh = createSheet(wb, "🌍 Alumni World Map");
                String[] cols = {"Full Name","Email","Faculty","Grad Year","Workplace","City","Country","Latitude","Longitude"};
                int[] w = {8000,9000,9000,5000,8000,6000,6000,5000,5000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : allMezuns) {
                    Profile p = u.getProfile();
                    if (p == null || !Boolean.TRUE.equals(p.getShowOnMap())) continue;
                    Row dr = sh.createRow(r);
                    c(dr,0,u.getFullName(),st.body(r)); c(dr,1,u.getEmail(),st.body(r));
                    c(dr,2,u.getFaculty()!=null?u.getFaculty().getName():"—",st.body(r));
                    c(dr,3,u.getGraduationYear()!=null?String.valueOf(u.getGraduationYear()):"—",st.body(r));
                    c(dr,4,nz(u.getWorkPlace()),st.body(r));
                    c(dr,5,nz(p.getMapCity()),st.body(r));
                    c(dr,6,nz(p.getMapCountry()),st.body(r));
                    c(dr,7,p.getMapLat()!=null?String.valueOf(p.getMapLat()):"—",st.body(r));
                    c(dr,8,p.getMapLng()!=null?String.valueOf(p.getMapLng()):"—",st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 13. ALUMNI BY COUNTRY ─────────────────────────────────
            {
                Sheet sh = createSheet(wb, "🌐 Alumni by Country");
                Map<String, Long> byCountry = allMezuns.stream()
                        .filter(u -> u.getProfile() != null && u.getProfile().getMapCountry() != null)
                        .collect(Collectors.groupingBy(u -> u.getProfile().getMapCountry(), TreeMap::new, Collectors.counting()));
                String[] cols = {"Country","Alumni Count","% of Alumni on Map"};
                int[] w = {7000,5000,7000};
                headerRow(sh, st, 0, cols, w);
                long mapTotal = byCountry.values().stream().mapToLong(Long::longValue).sum();
                int r = 1;
                for (var e : byCountry.entrySet()) {
                    Row dr = sh.createRow(r);
                    double pct = mapTotal > 0 ? (e.getValue() * 100.0 / mapTotal) : 0;
                    c(dr,0,e.getKey(),st.body(r)); n(dr,1,e.getValue(),st.num(r));
                    c(dr,2,String.format("%.1f%%",pct),st.body(r));
                    r++;
                }
            }

            // ── 14. MENTORSHIP-READY ALUMNI ───────────────────────────
            {
                Sheet sh = createSheet(wb, "🤝 Mentors");
                String[] cols = {"Full Name","Email","Faculty","Grad Year","Workplace","Job Title","Mentor Job Title","Skills","Can Mentor"};
                int[] w = {8000,9000,9000,5000,8000,7000,9000,10000,5000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : allMezuns) {
                    Profile p = u.getProfile();
                    if (p == null || !Boolean.TRUE.equals(p.getCanMentor())) continue;
                    Row dr = sh.createRow(r);
                    String skills = p.getSkills()!=null ? String.join(", ", p.getSkills()) : "—";
                    c(dr,0,u.getFullName(),st.body(r)); c(dr,1,u.getEmail(),st.body(r));
                    c(dr,2,u.getFaculty()!=null?u.getFaculty().getName():"—",st.body(r));
                    c(dr,3,u.getGraduationYear()!=null?String.valueOf(u.getGraduationYear()):"—",st.body(r));
                    c(dr,4,nz(u.getWorkPlace()),st.body(r));
                    c(dr,5,nz(p.getCurrentJobTitle()),st.body(r));
                    c(dr,6,nz(p.getMentorJobTitle()),st.body(r));
                    c(dr,7,skills,st.body(r));
                    c(dr,8,"Yes",st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 15. STUDENTS BY STUDY YEAR × FACULTY ─────────────────
            {
                Sheet sh = createSheet(wb, "📚 Students by Study Year");
                List<Faculty> faculties = facultyRepo.findAllByOrderByNameAsc();
                // Rows = study years 1-6, cols = faculties
                String[] yearLabels = {"Study Year","Year 1","Year 2","Year 3","Year 4","Year 5","Year 6","Unknown"};
                int[] w = new int[yearLabels.length]; Arrays.fill(w,5000); w[0]=6000;
                headerRow(sh, st, 0, yearLabels, w);

                Map<String, Map<Integer, Long>> facYearMap = new LinkedHashMap<>();
                for (AppUser u : allStudents) {
                    String facName = u.getFaculty()!=null?u.getFaculty().getName():"Unknown Faculty";
                    Integer yr = u.getProfile()!=null?u.getProfile().getStudyYear():null;
                    facYearMap.computeIfAbsent(facName, k->new HashMap<>())
                            .merge(yr!=null?yr:-1, 1L, Long::sum);
                }
                int r = 1;
                for (var fe : facYearMap.entrySet()) {
                    Row dr = sh.createRow(r);
                    c(dr,0,fe.getKey(),st.body(r));
                    for (int yr=1;yr<=6;yr++) n(dr,yr,fe.getValue().getOrDefault(yr,0L),st.num(r));
                    n(dr,7,fe.getValue().getOrDefault(-1,0L),st.num(r));
                    r++;
                }

                // Totals row
                Row tot = sh.createRow(r);
                c(tot,0,"TOTAL",st.headerStyle);
                Map<Integer, Long> totMap = allStudents.stream()
                        .collect(Collectors.groupingBy(u->{
                            Integer yr = u.getProfile()!=null?u.getProfile().getStudyYear():null;
                            return yr!=null?yr:-1;
                        }, Collectors.counting()));
                for (int yr=1;yr<=6;yr++) n(tot,yr,totMap.getOrDefault(yr,0L),st.numBold);
                n(tot,7,totMap.getOrDefault(-1,0L),st.numBold);
            }

            // ── 16. GENDER BREAKDOWN (full) ───────────────────────────
            {
                Sheet sh = createSheet(wb, "⚧ Gender Breakdown");
                String[] cols = {"Role","Gender","Count","% within Role"};
                int[] w = {6000,5000,5000,7000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                Map<UserRole, List<AppUser>> byRole = new LinkedHashMap<>();
                byRole.put(UserRole.STUDENT,  allStudents);
                byRole.put(UserRole.TEACHER,  allTeachers);
                byRole.put(UserRole.MEZUN,    allMezuns);
                byRole.put(UserRole.EMPLOYER, allEmployers);
                for (var re : byRole.entrySet()) {
                    Map<String, Long> gmap = re.getValue().stream()
                            .filter(u->u.getGender()!=null)
                            .collect(Collectors.groupingBy(AppUser::getGender, Collectors.counting()));
                    long roleTotal = re.getValue().size();
                    for (var ge : gmap.entrySet()) {
                        Row dr = sh.createRow(r);
                        double pct = roleTotal>0?(ge.getValue()*100.0/roleTotal):0;
                        c(dr,0,re.getKey().name(),st.body(r));
                        c(dr,1,ge.getKey(),st.body(r));
                        n(dr,2,ge.getValue(),st.num(r));
                        c(dr,3,String.format("%.1f%%",pct),st.body(r));
                        r++;
                    }
                    // No gender
                    long noGender = re.getValue().stream().filter(u->u.getGender()==null).count();
                    if (noGender > 0) {
                        Row dr = sh.createRow(r);
                        c(dr,0,re.getKey().name(),st.body(r));
                        c(dr,1,"Not specified",st.body(r));
                        n(dr,2,noGender,st.num(r));
                        c(dr,3,String.format("%.1f%%",noGender*100.0/Math.max(1,roleTotal)),st.body(r));
                        r++;
                    }
                    r++;
                }
            }

            // ── 17. EMPLOYERS BY FIELD ────────────────────────────────
            {
                Sheet sh = createSheet(wb, "🏭 Employers by Field");
                String[] cols = {"Field of Operation","Employer Count","% of Total Employers","Employer Names"};
                int[] w = {9000,6000,7000,14000};
                headerRow(sh, st, 0, cols, w);
                Map<String,List<AppUser>> byField = allEmployers.stream()
                        .collect(Collectors.groupingBy(u->nz(u.getCompanyField()),
                                Collectors.toList()));
                int r = 1;
                for (var e : byField.entrySet().stream()
                        .sorted((a,b)->Integer.compare(b.getValue().size(),a.getValue().size()))
                        .collect(Collectors.toList())) {
                    Row dr = sh.createRow(r);
                    double pct = allEmployers.size()>0?(e.getValue().size()*100.0/allEmployers.size()):0;
                    String names = e.getValue().stream()
                            .map(u->u.getCompanyName()!=null?u.getCompanyName():u.getFullName())
                            .collect(Collectors.joining(", "));
                    c(dr,0,e.getKey(),st.body(r));
                    n(dr,1,e.getValue().size(),st.num(r));
                    c(dr,2,String.format("%.1f%%",pct),st.body(r));
                    c(dr,3,names,st.body(r));
                    r++;
                }
            }

            // ── 18. REGISTRATION TIMELINE ─────────────────────────────
            {
                Sheet sh = createSheet(wb, "📈 Registration Timeline");
                List<AppUser> allUsers = userRepo.findAll();
                Map<String, Long> byMonth = allUsers.stream()
                        .filter(u->u.getCreatedAt()!=null)
                        .collect(Collectors.groupingBy(
                                u->u.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM")),
                                TreeMap::new, Collectors.counting()));
                String[] cols = {"Year-Month","New Registrations"};
                int[] w = {6000,7000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var e : byMonth.entrySet()) {
                    Row dr = sh.createRow(r);
                    c(dr,0,e.getKey(),st.body(r)); n(dr,1,e.getValue(),st.num(r));
                    r++;
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  FACULTY  —  full faculty export
    // ══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] exportFacultyAnalytics(UUID facultyId) throws IOException {
        AnalyticsService.FacultyAnalytics fa = analyticsService.getFacultyAnalytics(facultyId);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            // ── 1. SUMMARY ────────────────────────────────────────────
            {
                Sheet sh = createSheet(wb, "📊 Summary");
                int r = titleRow(sh, st, "FACULTY ANALYTICS — " + fa.faculty.getName().toUpperCase(), 2);
                r = kpiSection(sh, st, r, "🏛️ FACULTY");
                r = kv(sh, st, r, "Faculty Name",    fa.faculty.getName());
                r = kv(sh, st, r, "Faculty Code",    fa.faculty.getCode());
                r++;
                r = kpiSection(sh, st, r, "👥 MEMBERS");
                r = kv(sh, st, r, "Total Students",  fa.totalStudents);
                r = kv(sh, st, r, "Total Teachers",  fa.totalTeachers);
                r = kv(sh, st, r, "Total Alumni",    fa.totalMezuns);
                r = kv(sh, st, r, "Verified Alumni", fa.verifiedMezuns);
                r = kv(sh, st, r, "Total Courses",   fa.totalCourses);
                r++;
                r = kpiSection(sh, st, r, "⚧ GENDER — STUDENTS");
                for (var e : fa.studentGenderBreakdown.entrySet()) r = kv(sh, st, r, e.getKey(), e.getValue());
                r++;
                r = kpiSection(sh, st, r, "⚧ GENDER — ALUMNI");
                for (var e : fa.mezunGenderBreakdown.entrySet()) r = kv(sh, st, r, e.getKey(), e.getValue());
                r++;
                r = kpiSection(sh, st, r, "📚 STUDENTS BY STUDY YEAR");
                for (var e : fa.studentsByYear.entrySet()) r = kv(sh, st, r, "Year " + e.getKey(), e.getValue());
                r++;
                r = kpiSection(sh, st, r, "📅 ALUMNI BY GRADUATION YEAR");
                for (var e : fa.mezunsByYear.entrySet()) r = kv(sh, st, r, String.valueOf(e.getKey()), e.getValue());
                sh.setColumnWidth(0,11000); sh.setColumnWidth(1,6000);
            }

            // ── 2. STUDENTS (full) ────────────────────────────────────
            writeFullUserSheet(wb, st, "🎒 Students", fa.students);

            // ── 3. TEACHERS (full) ────────────────────────────────────
            writeFullUserSheet(wb, st, "👨‍🏫 Teachers", fa.teachers);

            // ── 4. ALUMNI (full) ──────────────────────────────────────
            writeAlumniSheet(wb, st, "🎓 Alumni", fa.mezuns);

            // ── 5. ALUMNI BY GRADUATION YEAR ──────────────────────────
            {
                Sheet sh = createSheet(wb, "📅 Alumni by Year");
                String[] cols = {"Graduation Year","Count","% of Faculty Alumni"};
                int[] w = {6000,5000,7000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var e : fa.mezunsByYear.entrySet()) {
                    Row dr = sh.createRow(r);
                    double pct = fa.totalMezuns>0?(e.getValue()*100.0/fa.totalMezuns):0;
                    c(dr,0,String.valueOf(e.getKey()),st.body(r));
                    n(dr,1,e.getValue(),st.num(r));
                    c(dr,2,String.format("%.1f%%",pct),st.body(r));
                    r++;
                }
            }

            // ── 6. ALUMNI BY WORKPLACE ────────────────────────────────
            {
                Sheet sh = createSheet(wb, "💼 Alumni by Workplace");
                String[] cols = {"Workplace","Count","Alumni Names","Emails"};
                int[] w = {9000,4000,12000,10000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var e : fa.mezunsByWorkplace.entrySet()) {
                    Row dr = sh.createRow(r);
                    String names  = e.getValue().stream().map(AppUser::getFullName).collect(Collectors.joining(", "));
                    String emails = e.getValue().stream().map(AppUser::getEmail).collect(Collectors.joining(", "));
                    c(dr,0,e.getKey(),st.body(r));
                    n(dr,1,e.getValue().size(),st.num(r));
                    c(dr,2,names,st.body(r));
                    c(dr,3,emails,st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 7. STUDENTS BY STUDY YEAR ─────────────────────────────
            {
                Sheet sh = createSheet(wb, "📚 Students by Year");
                String[] cols = {"Study Year","Count","% of Students","Student Names"};
                int[] w = {5000,5000,7000,16000};
                headerRow(sh, st, 0, cols, w);
                Map<Integer, List<AppUser>> byYr = fa.students.stream()
                        .collect(Collectors.groupingBy(u->{
                            Integer yr = u.getProfile()!=null?u.getProfile().getStudyYear():null;
                            return yr!=null?yr:-1;
                        }, TreeMap::new, Collectors.toList()));
                int r = 1;
                for (var e : byYr.entrySet()) {
                    Row dr = sh.createRow(r);
                    String label = e.getKey()==-1?"Unknown":"Year "+e.getKey();
                    String names = e.getValue().stream().map(AppUser::getFullName).collect(Collectors.joining(", "));
                    double pct = fa.totalStudents>0?(e.getValue().size()*100.0/fa.totalStudents):0;
                    c(dr,0,label,st.body(r)); n(dr,1,e.getValue().size(),st.num(r));
                    c(dr,2,String.format("%.1f%%",pct),st.body(r)); c(dr,3,names,st.body(r));
                    r++;
                }
            }

            // ── 8. GENDER BREAKDOWN ───────────────────────────────────
            {
                Sheet sh = createSheet(wb, "⚧ Gender Breakdown");
                String[] cols = {"Group","Gender","Count","% within Group"};
                int[] w = {6000,5000,5000,7000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var e : fa.studentGenderBreakdown.entrySet()) {
                    Row dr = sh.createRow(r);
                    double pct = fa.totalStudents>0?(e.getValue()*100.0/fa.totalStudents):0;
                    c(dr,0,"Students",st.body(r)); c(dr,1,e.getKey(),st.body(r));
                    n(dr,2,e.getValue(),st.num(r)); c(dr,3,String.format("%.1f%%",pct),st.body(r));
                    r++;
                }
                for (var e : fa.mezunGenderBreakdown.entrySet()) {
                    Row dr = sh.createRow(r);
                    double pct = fa.totalMezuns>0?(e.getValue()*100.0/fa.totalMezuns):0;
                    c(dr,0,"Alumni",st.body(r)); c(dr,1,e.getKey(),st.body(r));
                    n(dr,2,e.getValue(),st.num(r)); c(dr,3,String.format("%.1f%%",pct),st.body(r));
                    r++;
                }
            }

            // ── 9. ALUMNI WORLD MAP ───────────────────────────────────
            {
                Sheet sh = createSheet(wb, "🌍 Alumni World Map");
                String[] cols = {"Full Name","Email","Grad Year","Workplace","City","Country","Latitude","Longitude"};
                int[] w = {8000,9000,5000,8000,6000,6000,5000,5000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : fa.mezuns) {
                    Profile p = u.getProfile();
                    if (p==null||!Boolean.TRUE.equals(p.getShowOnMap())) continue;
                    Row dr = sh.createRow(r);
                    c(dr,0,u.getFullName(),st.body(r)); c(dr,1,u.getEmail(),st.body(r));
                    c(dr,2,u.getGraduationYear()!=null?String.valueOf(u.getGraduationYear()):"—",st.body(r));
                    c(dr,3,nz(u.getWorkPlace()),st.body(r));
                    c(dr,4,nz(p.getMapCity()),st.body(r)); c(dr,5,nz(p.getMapCountry()),st.body(r));
                    c(dr,6,p.getMapLat()!=null?String.valueOf(p.getMapLat()):"—",st.body(r));
                    c(dr,7,p.getMapLng()!=null?String.valueOf(p.getMapLng()):"—",st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── 10. MENTORSHIP ALUMNI ─────────────────────────────────
            {
                Sheet sh = createSheet(wb, "🤝 Mentors");
                String[] cols = {"Full Name","Email","Grad Year","Workplace","Current Job","Mentor Job Title","Skills"};
                int[] w = {8000,9000,5000,8000,7000,9000,10000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : fa.mezuns) {
                    Profile p = u.getProfile();
                    if (p==null||!Boolean.TRUE.equals(p.getCanMentor())) continue;
                    Row dr = sh.createRow(r);
                    String skills = p.getSkills()!=null?String.join(", ",p.getSkills()):"—";
                    c(dr,0,u.getFullName(),st.body(r)); c(dr,1,u.getEmail(),st.body(r));
                    c(dr,2,u.getGraduationYear()!=null?String.valueOf(u.getGraduationYear()):"—",st.body(r));
                    c(dr,3,nz(u.getWorkPlace()),st.body(r)); c(dr,4,nz(p.getCurrentJobTitle()),st.body(r));
                    c(dr,5,nz(p.getMentorJobTitle()),st.body(r)); c(dr,6,skills,st.body(r));
                    r++;
                }
            }

            // ── 11. ACTIVE JOB POSTINGS ───────────────────────────────
            {
                Sheet sh = createSheet(wb, "💼 Active Job Postings");
                String[] cols = {"Job Title (EN)","Employer","Company","Field","Type","Location","Salary","Deadline"};
                int[] w = {10000,8000,7000,7000,5000,7000,6000,6000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (var e : fa.employerJobs.entrySet()) {
                    for (JobListing job : e.getValue()) {
                        Row dr = sh.createRow(r);
                        String title = job.getTitleI18n()!=null
                                ?job.getTitleI18n().getOrDefault("en",job.getTitleI18n().values().stream().findFirst().orElse("—"))
                                :"—";
                        c(dr,0,title,st.body(r));
                        c(dr,1,e.getKey().getFullName(),st.body(r));
                        c(dr,2,nz(e.getKey().getCompanyName()),st.body(r));
                        c(dr,3,nz(e.getKey().getCompanyField()),st.body(r));
                        c(dr,4,job.getJobType()!=null?job.getJobType().name():"—",st.body(r));
                        c(dr,5,nz(job.getLocation()),st.body(r));
                        c(dr,6,nz(job.getSalaryRange()),st.body(r));
                        c(dr,7,job.getDeadline()!=null?job.getDeadline().toString():"—",st.body(r));
                        r++;
                    }
                }
                autoFilter(sh, cols.length - 1);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Shared sheet writers
    // ══════════════════════════════════════════════════════════════════

    private void writeFullUserSheet(XSSFWorkbook wb, Styles st, String sheetName, List<AppUser> users) {
        Sheet sh = createSheet(wb, sheetName);
        String[] cols = {
                "Full Name","Email","First Name","Last Name","Gender","Role","Status","Faculty",
                "Student ID","Admission Year","Graduation Year","Grad Detected At",
                "Study Year","Total Points","Rank","Verified","OBIS Username",
                "Headline","Bio","Skills","Current Job Title","Current Company",
                "Phone","Location","Nationality","Website","Date of Birth",
                "Can Mentor","Mentor Job Title","On Map","Map City","Map Country",
                "Social Links","Certifications (count)","Work Experience (count)","Created At"
        };
        int[] w = {8000,9000,6000,6000,4500,5000,5000,9000,
                6000,5500,6000,7000,
                5500,5500,4500,5000,7000,
                9000,12000,12000,8000,8000,
                5500,7000,6000,8000,6000,
                5000,8000,4500,5000,6000,
                9000,6000,6000,7000};
        headerRow(sh, st, 0, cols, w);
        int r = 1;
        for (AppUser u : users) {
            Profile p = u.getProfile();
            Row dr = sh.createRow(r);
            c(dr, 0, u.getFullName(), st.body(r));
            c(dr, 1, u.getEmail(), st.body(r));
            c(dr, 2, nz(u.getFirstName()), st.body(r));
            c(dr, 3, nz(u.getLastName()), st.body(r));
            c(dr, 4, nz(u.getGender()), st.body(r));
            c(dr, 5, u.getRole().name(), st.body(r));
            c(dr, 6, u.getStatus().name(), st.body(r));
            c(dr, 7, u.getFaculty()!=null?u.getFaculty().getName():"—", st.body(r));
            c(dr, 8, nz(u.getStudentIdNumber()), st.body(r));
            c(dr, 9, u.getAdmissionYear()!=null?String.valueOf(u.getAdmissionYear()):"—", st.body(r));
            c(dr,10, u.getGraduationYear()!=null?String.valueOf(u.getGraduationYear()):"—", st.body(r));
            c(dr,11, u.getGraduationDetectedAt()!=null?u.getGraduationDetectedAt().format(DT):"—", st.body(r));
            c(dr,12, p!=null&&p.getStudyYear()!=null?String.valueOf(p.getStudyYear()):"—", st.body(r));
            c(dr,13, p!=null?String.valueOf(p.getTotalPoints()):"0", st.body(r));
            c(dr,14, p!=null&&p.getRankPosition()!=null?String.valueOf(p.getRankPosition()):"—", st.body(r));
            c(dr,15, u.isUniversityVerified()?"Yes":"No", st.body(r));
            c(dr,16, nz(u.getObisUsername()), st.body(r));
            c(dr,17, p!=null?nz(p.getHeadline()):"—", st.body(r));
            c(dr,18, p!=null&&p.getBio()!=null?p.getBio().replaceAll("\\s+"," "):"—", st.body(r));
            c(dr,19, p!=null&&p.getSkills()!=null?String.join(", ",p.getSkills()):"—", st.body(r));
            c(dr,20, p!=null?nz(p.getCurrentJobTitle()):"—", st.body(r));
            c(dr,21, p!=null?nz(p.getCurrentCompany()):"—", st.body(r));
            c(dr,22, p!=null?nz(p.getPhone()):"—", st.body(r));
            c(dr,23, p!=null?nz(p.getLocation()):"—", st.body(r));
            c(dr,24, p!=null?nz(p.getNationality()):"—", st.body(r));
            c(dr,25, p!=null?nz(p.getWebsite()):"—", st.body(r));
            c(dr,26, p!=null?nz(p.getDateOfBirth()):"—", st.body(r));
            c(dr,27, p!=null&&Boolean.TRUE.equals(p.getCanMentor())?"Yes":"No", st.body(r));
            c(dr,28, p!=null?nz(p.getMentorJobTitle()):"—", st.body(r));
            c(dr,29, p!=null&&Boolean.TRUE.equals(p.getShowOnMap())?"Yes":"No", st.body(r));
            c(dr,30, p!=null?nz(p.getMapCity()):"—", st.body(r));
            c(dr,31, p!=null?nz(p.getMapCountry()):"—", st.body(r));
            c(dr,32, p!=null&&p.getSocialLinks()!=null&&!p.getSocialLinks().isEmpty()?p.getSocialLinks().toString():"—", st.body(r));
            c(dr,33, p!=null&&p.getCertifications()!=null?String.valueOf(p.getCertifications().size()):"0", st.body(r));
            c(dr,34, p!=null&&p.getWorkExperience()!=null?String.valueOf(p.getWorkExperience().size()):"0", st.body(r));
            c(dr,35, u.getCreatedAt()!=null?u.getCreatedAt().format(DT):"—", st.body(r));
            r++;
        }
        autoFilter(sh, cols.length - 1);
        sh.createFreezePane(0, 1);
    }

    private void writeAlumniSheet(XSSFWorkbook wb, Styles st, String sheetName, List<AppUser> mezuns) {
        Sheet sh = createSheet(wb, sheetName);
        String[] cols = {
                "Full Name","Email","Gender","Faculty","Graduation Year","Graduation Detected At","Admission Year",
                "Place of Work","University Verified","Student ID",
                "Headline","Current Job Title","Current Company","Can Mentor","Mentor Job Title",
                "Skills","Phone","Location","Nationality","Date of Birth","On Map","Map City","Map Country",
                "Total Points","Social Links","Certifications","Work Experience Entries","Created At"
        };
        int[] w = {8000,9000,4500,9000,6000,7000,5500,
                9000,6000,6000,
                9000,8000,8000,5000,9000,
                10000,5500,7000,6000,6000,4500,5500,6000,
                5500,9000,5500,7000,7000};
        headerRow(sh, st, 0, cols, w);
        int r = 1;
        for (AppUser u : mezuns) {
            Profile p = u.getProfile();
            Row dr = sh.createRow(r);
            c(dr, 0, u.getFullName(), st.body(r));
            c(dr, 1, u.getEmail(), st.body(r));
            c(dr, 2, nz(u.getGender()), st.body(r));
            c(dr, 3, u.getFaculty()!=null?u.getFaculty().getName():"—", st.body(r));
            c(dr, 4, u.getGraduationYear()!=null?String.valueOf(u.getGraduationYear()):"—", st.body(r));
            c(dr, 5, u.getGraduationDetectedAt()!=null?u.getGraduationDetectedAt().format(DT):"—", st.body(r));
            c(dr, 6, u.getAdmissionYear()!=null?String.valueOf(u.getAdmissionYear()):"—", st.body(r));
            c(dr, 7, nz(u.getWorkPlace()), st.body(r));
            c(dr, 8, u.isUniversityVerified()?"Yes":"No", st.body(r));
            c(dr, 9, nz(u.getStudentIdNumber()), st.body(r));
            c(dr,10, p!=null?nz(p.getHeadline()):"—", st.body(r));
            c(dr,11, p!=null?nz(p.getCurrentJobTitle()):"—", st.body(r));
            c(dr,12, p!=null?nz(p.getCurrentCompany()):"—", st.body(r));
            c(dr,13, p!=null&&Boolean.TRUE.equals(p.getCanMentor())?"Yes":"No", st.body(r));
            c(dr,14, p!=null?nz(p.getMentorJobTitle()):"—", st.body(r));
            c(dr,15, p!=null&&p.getSkills()!=null?String.join(", ",p.getSkills()):"—", st.body(r));
            c(dr,16, p!=null?nz(p.getPhone()):"—", st.body(r));
            c(dr,17, p!=null?nz(p.getLocation()):"—", st.body(r));
            c(dr,18, p!=null?nz(p.getNationality()):"—", st.body(r));
            c(dr,19, p!=null?nz(p.getDateOfBirth()):"—", st.body(r));
            c(dr,20, p!=null&&Boolean.TRUE.equals(p.getShowOnMap())?"Yes":"No", st.body(r));
            c(dr,21, p!=null?nz(p.getMapCity()):"—", st.body(r));
            c(dr,22, p!=null?nz(p.getMapCountry()):"—", st.body(r));
            c(dr,23, p!=null?String.valueOf(p.getTotalPoints()):"0", st.body(r));
            c(dr,24, p!=null&&p.getSocialLinks()!=null&&!p.getSocialLinks().isEmpty()?p.getSocialLinks().toString():"—", st.body(r));
            c(dr,25, p!=null&&p.getCertifications()!=null?String.valueOf(p.getCertifications().size()):"0", st.body(r));
            c(dr,26, p!=null&&p.getWorkExperience()!=null?String.valueOf(p.getWorkExperience().size()):"0", st.body(r));
            c(dr,27, u.getCreatedAt()!=null?u.getCreatedAt().format(DT):"—", st.body(r));
            r++;
        }
        autoFilter(sh, cols.length - 1);
        sh.createFreezePane(0, 1);
    }

    private void writeEmployerSheet(XSSFWorkbook wb, Styles st, String sheetName, List<AppUser> employers) {
        Sheet sh = createSheet(wb, sheetName);
        String[] cols = {"Full Name","Email","Gender","Company Name","Field of Operation",
                "Status","Has Document","Website","Location","Phone","Headline","Created At"};
        int[] w = {8000,9000,4500,9000,8000,5000,5000,9000,7000,6000,9000,7000};
        headerRow(sh, st, 0, cols, w);
        int r = 1;
        for (AppUser u : employers) {
            Profile p = u.getProfile();
            Row dr = sh.createRow(r);
            c(dr, 0, u.getFullName(), st.body(r));
            c(dr, 1, u.getEmail(), st.body(r));
            c(dr, 2, nz(u.getGender()), st.body(r));
            c(dr, 3, nz(u.getCompanyName()), st.body(r));
            c(dr, 4, nz(u.getCompanyField()), st.body(r));
            c(dr, 5, u.getStatus().name(), st.body(r));
            c(dr, 6, u.getCompanyDocumentUrl()!=null?"Yes":"No", st.body(r));
            c(dr, 7, p!=null?nz(p.getWebsite()):"—", st.body(r));
            c(dr, 8, p!=null?nz(p.getLocation()):"—", st.body(r));
            c(dr, 9, p!=null?nz(p.getPhone()):"—", st.body(r));
            c(dr,10, p!=null?nz(p.getHeadline()):"—", st.body(r));
            c(dr,11, u.getCreatedAt()!=null?u.getCreatedAt().format(DT):"—", st.body(r));
            r++;
        }
        autoFilter(sh, cols.length - 1);
        sh.createFreezePane(0, 1);
    }

    // ══════════════════════════════════════════════════════════════════
    //  MEZUN CATALOG  —  filtered export (respects search filters + sort)
    // ══════════════════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public byte[] exportMezunExcel(UUID facultyId, Integer graduationYear, String name,
                                   String sortBy, String sortDir) throws IOException {
        String namePattern = (name != null && !name.isBlank())
                ? "%" + name.trim().toLowerCase() + "%" : null;

        List<AppUser> mezuns = new java.util.ArrayList<>(
                userRepo.searchMezunAll(facultyId, graduationYear, namePattern));

        // Apply the same sort the user sees on the catalog page
        java.util.Comparator<AppUser> cmp = switch (sortBy != null ? sortBy : "name") {
            case "faculty" -> java.util.Comparator.comparing(
                    u -> u.getFaculty() != null ? u.getFaculty().getName() : "",
                    java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "year"    -> java.util.Comparator.comparing(
                    AppUser::getGraduationYear,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            case "points"  -> java.util.Comparator.comparing(
                    u -> u.getProfile() != null ? u.getProfile().getTotalPoints() : 0,
                    java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()));
            default        -> java.util.Comparator.comparing(
                    AppUser::getFullName,
                    java.util.Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        };
        if ("desc".equalsIgnoreCase(sortDir)) cmp = cmp.reversed();
        mezuns.sort(cmp);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet sh = wb.createSheet("Alumni List");

            // ── Header row ────────────────────────────────────────────
            String[] cols = {"ALUMNI LIST", "YEAR", "FACULTY", "GENDER", "WORK PLACE"};
            int[]    widths = {10000, 5000, 10000, 5500, 10000};
            Row hdr = sh.createRow(0);
            hdr.setHeight((short) 500);
            for (int i = 0; i < cols.length; i++) {
                sh.setColumnWidth(i, widths[i]);
                Cell cell = hdr.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(st.headerStyle);
            }
            sh.createFreezePane(0, 1);

            // ── Data rows ─────────────────────────────────────────────
            int r = 1;
            for (AppUser u : mezuns) {
                Row row = sh.createRow(r);
                CellStyle cs = st.body(r);
                c(row, 0, u.getFullName(),                                                cs);
                c(row, 1, u.getGraduationYear() != null ? String.valueOf(u.getGraduationYear()) : "—", cs);
                c(row, 2, u.getFaculty() != null ? u.getFaculty().getName() : "—",       cs);
                c(row, 3, u.getGender() != null  ? u.getGender()            : "—",       cs);
                c(row, 4, u.getWorkPlace() != null && !u.getWorkPlace().isBlank()
                        ? u.getWorkPlace() : "—",                                         cs);
                r++;
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    private byte[] exportMezunExcelOldMultiSheet(UUID facultyId, Integer graduationYear, String name) throws IOException {
        String namePattern = (name != null && !name.isBlank())
                ? "%" + name.trim().toLowerCase() + "%" : null;

        List<AppUser> mezuns = userRepo.searchMezunAll(facultyId, graduationYear, namePattern);

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);

            // ── Sheet 1: Summary ──────────────────────────────────────
            {
                Sheet sh = createSheet(wb, "Summary");
                int r = titleRow(sh, st, "ALUMNI EXPORT", 2);
                r = kpiSection(sh, st, r, "🔍 APPLIED FILTERS");
                r = kv(sh, st, r, "Name filter",      name != null && !name.isBlank() ? name : "All");
                r = kv(sh, st, r, "Faculty filter",   facultyId != null
                        ? facultyRepo.findById(facultyId).map(f -> f.getName()).orElse(facultyId.toString())
                        : "All Faculties");
                r = kv(sh, st, r, "Graduation Year",  graduationYear != null ? String.valueOf(graduationYear) : "All Years");
                r++;
                r = kpiSection(sh, st, r, "📊 RESULT COUNTS");
                r = kv(sh, st, r, "Total Alumni in Export",       mezuns.size());
                r = kv(sh, st, r, "University Verified",          mezuns.stream().filter(AppUser::isUniversityVerified).count());
                r = kv(sh, st, r, "Have Place of Work",           mezuns.stream().filter(u -> u.getWorkPlace() != null).count());
                r = kv(sh, st, r, "Available as Mentor",         mezuns.stream().filter(u -> u.getProfile() != null && Boolean.TRUE.equals(u.getProfile().getCanMentor())).count());
                r = kv(sh, st, r, "On Alumni Map",               mezuns.stream().filter(u -> u.getProfile() != null && Boolean.TRUE.equals(u.getProfile().getShowOnMap())).count());
                r++;
                // Gender breakdown
                r = kpiSection(sh, st, r, "⚧ GENDER");
                Map<String, Long> gender = mezuns.stream()
                        .filter(u -> u.getGender() != null)
                        .collect(Collectors.groupingBy(AppUser::getGender, Collectors.counting()));
                for (var e : gender.entrySet()) r = kv(sh, st, r, e.getKey(), e.getValue());
                long noGender = mezuns.stream().filter(u -> u.getGender() == null).count();
                if (noGender > 0) r = kv(sh, st, r, "Not specified", noGender);
                r++;
                // By graduation year
                r = kpiSection(sh, st, r, "📅 BY GRADUATION YEAR");
                Map<Integer, Long> byYear = mezuns.stream()
                        .filter(u -> u.getGraduationYear() != null)
                        .collect(Collectors.groupingBy(AppUser::getGraduationYear, TreeMap::new, Collectors.counting()));
                for (var e : byYear.entrySet()) r = kv(sh, st, r, String.valueOf(e.getKey()), e.getValue());
                r++;
                // By faculty
                r = kpiSection(sh, st, r, "🏛️ BY FACULTY");
                Map<String, Long> byFaculty = mezuns.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getFaculty() != null ? u.getFaculty().getName() : "Unknown",
                                TreeMap::new, Collectors.counting()));
                for (var e : byFaculty.entrySet()) r = kv(sh, st, r, e.getKey(), e.getValue());

                sh.setColumnWidth(0, 11000);
                sh.setColumnWidth(1, 7000);
            }

            // ── Sheet 2: Full Alumni List ─────────────────────────────
            writeAlumniSheet(wb, st, "Alumni List", mezuns);

            // ── Sheet 3: Alumni by Graduation Year ───────────────────
            {
                Sheet sh = createSheet(wb, "By Graduation Year");
                String[] cols = {"Graduation Year", "Count", "% of Export", "Names"};
                int[] w = {6000, 4500, 5000, 20000};
                headerRow(sh, st, 0, cols, w);
                Map<Integer, List<AppUser>> byYear = mezuns.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getGraduationYear() != null ? u.getGraduationYear() : 0,
                                TreeMap::new, Collectors.toList()));
                int r = 1;
                for (var e : byYear.entrySet()) {
                    Row dr = sh.createRow(r);
                    String label = e.getKey() == 0 ? "Unknown" : String.valueOf(e.getKey());
                    double pct = mezuns.size() > 0 ? (e.getValue().size() * 100.0 / mezuns.size()) : 0;
                    String names = e.getValue().stream().map(AppUser::getFullName).collect(Collectors.joining(", "));
                    c(dr, 0, label, st.body(r));
                    n(dr, 1, e.getValue().size(), st.num(r));
                    c(dr, 2, String.format("%.1f%%", pct), st.body(r));
                    c(dr, 3, names, st.body(r));
                    r++;
                }
            }

            // ── Sheet 4: Alumni by Workplace ──────────────────────────
            {
                Sheet sh = createSheet(wb, "By Workplace");
                String[] cols = {"Workplace / Company", "Count", "Alumni Names", "Emails", "Graduation Years"};
                int[] w = {9000, 4000, 12000, 10000, 7000};
                headerRow(sh, st, 0, cols, w);
                Map<String, List<AppUser>> byWork = mezuns.stream()
                        .filter(u -> u.getWorkPlace() != null)
                        .collect(Collectors.groupingBy(AppUser::getWorkPlace,
                                LinkedHashMap::new, Collectors.toList()));
                int r = 1;
                for (var e : byWork.entrySet().stream()
                        .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                        .collect(Collectors.toList())) {
                    Row dr = sh.createRow(r);
                    String names  = e.getValue().stream().map(AppUser::getFullName).collect(Collectors.joining(", "));
                    String emails = e.getValue().stream().map(AppUser::getEmail).collect(Collectors.joining(", "));
                    String years  = e.getValue().stream()
                            .filter(u -> u.getGraduationYear() != null)
                            .map(u -> String.valueOf(u.getGraduationYear()))
                            .distinct().sorted().collect(Collectors.joining(", "));
                    c(dr, 0, e.getKey(), st.body(r));
                    n(dr, 1, e.getValue().size(), st.num(r));
                    c(dr, 2, names, st.body(r));
                    c(dr, 3, emails, st.body(r));
                    c(dr, 4, years.isEmpty() ? "—" : years, st.body(r));
                    r++;
                }
                autoFilter(sh, cols.length - 1);
            }

            // ── Sheet 5: Alumni by Faculty ────────────────────────────
            {
                Sheet sh = createSheet(wb, "By Faculty");
                String[] cols = {"Faculty", "Code", "Count", "% of Export", "Alumni Names"};
                int[] w = {9000, 4000, 4000, 5000, 20000};
                headerRow(sh, st, 0, cols, w);
                Map<String, List<AppUser>> byFac = mezuns.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getFaculty() != null ? u.getFaculty().getName() : "Unknown",
                                TreeMap::new, Collectors.toList()));
                int r = 1;
                for (var e : byFac.entrySet()) {
                    Row dr = sh.createRow(r);
                    String code = e.getValue().stream()
                            .filter(u -> u.getFaculty() != null)
                            .map(u -> u.getFaculty().getCode())
                            .findFirst().orElse("—");
                    double pct = mezuns.size() > 0 ? (e.getValue().size() * 100.0 / mezuns.size()) : 0;
                    String names = e.getValue().stream().map(AppUser::getFullName).collect(Collectors.joining(", "));
                    c(dr, 0, e.getKey(), st.body(r));
                    c(dr, 1, code, st.body(r));
                    n(dr, 2, e.getValue().size(), st.num(r));
                    c(dr, 3, String.format("%.1f%%", pct), st.body(r));
                    c(dr, 4, names, st.body(r));
                    r++;
                }
            }

            // ── Sheet 6: Gender Breakdown ─────────────────────────────
            {
                Sheet sh = createSheet(wb, "Gender Breakdown");
                String[] cols = {"Gender", "Count", "% of Export", "Names"};
                int[] w = {5000, 4000, 5000, 20000};
                headerRow(sh, st, 0, cols, w);
                Map<String, List<AppUser>> byGender = mezuns.stream()
                        .collect(Collectors.groupingBy(
                                u -> u.getGender() != null ? u.getGender() : "Not specified",
                                TreeMap::new, Collectors.toList()));
                int r = 1;
                for (var e : byGender.entrySet()) {
                    Row dr = sh.createRow(r);
                    double pct = mezuns.size() > 0 ? (e.getValue().size() * 100.0 / mezuns.size()) : 0;
                    String names = e.getValue().stream().map(AppUser::getFullName).collect(Collectors.joining(", "));
                    c(dr, 0, e.getKey(), st.body(r));
                    n(dr, 1, e.getValue().size(), st.num(r));
                    c(dr, 2, String.format("%.1f%%", pct), st.body(r));
                    c(dr, 3, names, st.body(r));
                    r++;
                }
            }

            // ── Sheet 7: Mentors ──────────────────────────────────────
            {
                Sheet sh = createSheet(wb, "Mentors");
                List<AppUser> mentors = mezuns.stream()
                        .filter(u -> u.getProfile() != null && Boolean.TRUE.equals(u.getProfile().getCanMentor()))
                        .collect(Collectors.toList());
                String[] cols = {"Full Name", "Email", "Faculty", "Grad Year", "Workplace", "Current Job", "Mentor Job Title", "Skills"};
                int[] w = {8000, 9000, 9000, 5000, 8000, 7000, 9000, 10000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : mentors) {
                    Profile p = u.getProfile();
                    Row dr = sh.createRow(r);
                    String skills = p != null && p.getSkills() != null ? String.join(", ", p.getSkills()) : "—";
                    c(dr, 0, u.getFullName(), st.body(r));
                    c(dr, 1, u.getEmail(), st.body(r));
                    c(dr, 2, u.getFaculty() != null ? u.getFaculty().getName() : "—", st.body(r));
                    c(dr, 3, u.getGraduationYear() != null ? String.valueOf(u.getGraduationYear()) : "—", st.body(r));
                    c(dr, 4, nz(u.getWorkPlace()), st.body(r));
                    c(dr, 5, p != null ? nz(p.getCurrentJobTitle()) : "—", st.body(r));
                    c(dr, 6, p != null ? nz(p.getMentorJobTitle()) : "—", st.body(r));
                    c(dr, 7, skills, st.body(r));
                    r++;
                }
                if (mentors.isEmpty()) {
                    Row dr = sh.createRow(1);
                    c(dr, 0, "No mentors found for this filter.", st.body(1));
                }
                sh.createFreezePane(0, 1);
            }

            // ── Sheet 8: World Map Alumni ─────────────────────────────
            {
                Sheet sh = createSheet(wb, "World Map");
                List<AppUser> onMap = mezuns.stream()
                        .filter(u -> u.getProfile() != null && Boolean.TRUE.equals(u.getProfile().getShowOnMap()))
                        .collect(Collectors.toList());
                String[] cols = {"Full Name", "Email", "Faculty", "Grad Year", "Workplace", "City", "Country", "Latitude", "Longitude"};
                int[] w = {8000, 9000, 9000, 5000, 8000, 6000, 6000, 5000, 5000};
                headerRow(sh, st, 0, cols, w);
                int r = 1;
                for (AppUser u : onMap) {
                    Profile p = u.getProfile();
                    Row dr = sh.createRow(r);
                    c(dr, 0, u.getFullName(), st.body(r));
                    c(dr, 1, u.getEmail(), st.body(r));
                    c(dr, 2, u.getFaculty() != null ? u.getFaculty().getName() : "—", st.body(r));
                    c(dr, 3, u.getGraduationYear() != null ? String.valueOf(u.getGraduationYear()) : "—", st.body(r));
                    c(dr, 4, nz(u.getWorkPlace()), st.body(r));
                    c(dr, 5, p != null ? nz(p.getMapCity()) : "—", st.body(r));
                    c(dr, 6, p != null ? nz(p.getMapCountry()) : "—", st.body(r));
                    c(dr, 7, p != null && p.getMapLat() != null ? String.valueOf(p.getMapLat()) : "—", st.body(r));
                    c(dr, 8, p != null && p.getMapLng() != null ? String.valueOf(p.getMapLng()) : "—", st.body(r));
                    r++;
                }
                if (onMap.isEmpty()) {
                    Row dr = sh.createRow(1);
                    c(dr, 0, "No alumni on map for this filter.", st.body(1));
                }
                autoFilter(sh, cols.length - 1);
                sh.createFreezePane(0, 1);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }



    public record ImportResult(int imported, int skipped, List<String> errors) {}

    @Transactional
    public ImportResult importUsers(MultipartFile file, UUID defaultFacultyId) throws IOException {
        int imported = 0, skipped = 0;
        List<String> errors = new ArrayList<>();

        try (org.apache.poi.ss.usermodel.Workbook wb2 = new org.apache.poi.xssf.usermodel.XSSFWorkbook(file.getInputStream())) {
            org.apache.poi.ss.usermodel.Sheet sheet = wb2.getSheetAt(0);
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
                if (row == null) continue;
                try {
                    String firstName   = cellStr(row, 0);
                    String lastName    = cellStr(row, 1);
                    String email       = cellStr(row, 2);
                    String roleStr     = cellStr(row, 3);
                    String facultyName = cellStr(row, 4);
                    String yearStr     = cellStr(row, 5);
                    String studentNum  = cellStr(row, 6);
                    String password    = cellStr(row, 7);

                    if (email == null || email.isBlank()) { errors.add("Row "+(i+1)+": email empty."); skipped++; continue; }
                    if (userRepo.existsByEmail(email.trim())) { errors.add("Row "+(i+1)+": "+email+" exists."); skipped++; continue; }

                    UserRole role = UserRole.STUDENT;
                    if (roleStr != null && !roleStr.isBlank()) {
                        try { role = UserRole.valueOf(roleStr.trim().toUpperCase()); }
                        catch (IllegalArgumentException e) { errors.add("Row "+(i+1)+": unknown role '"+roleStr+"', defaulted STUDENT."); }
                    }

                    Faculty faculty = null;
                    if (defaultFacultyId != null) faculty = facultyRepo.findById(defaultFacultyId).orElse(null);
                    if (facultyName != null && !facultyName.isBlank()) {
                        String fn = facultyName.trim();
                        Faculty found = facultyRepo.findAll().stream()
                                .filter(f->f.getName().equalsIgnoreCase(fn)||f.getCode().equalsIgnoreCase(fn))
                                .findFirst().orElse(null);
                        if (found != null) faculty = found;
                        else errors.add("Row "+(i+1)+": faculty '"+fn+"' not found.");
                    }

                    Integer year = null;
                    if (yearStr != null && !yearStr.isBlank()) {
                        try { year = Integer.parseInt(yearStr.trim()); }
                        catch (NumberFormatException e) { errors.add("Row "+(i+1)+": invalid year."); }
                    }

                    String fullName = ((firstName!=null?firstName:"")+" "+(lastName!=null?lastName:"")).trim();
                    if (fullName.isBlank()) fullName = email.trim();

                    Profile profile = Profile.builder().totalPoints(0).build();
                    AppUser user = AppUser.builder()
                            .fullName(fullName).email(email.trim())
                            .passwordHash(passwordEncoder.encode(password!=null&&!password.isBlank()?password.trim():"Manas2025!"))
                            .role(role).status(UserStatus.ACTIVE)
                            .faculty(faculty).graduationYear(year)
                            .studentIdNumber(studentNum!=null?studentNum.trim():null)
                            .build();
                    user.setProfile(profile);
                    userRepo.save(user);
                    imported++;
                } catch (Exception e) {
                    errors.add("Row "+(i+1)+": "+e.getMessage()); skipped++;
                }
            }
        }
        log.info("Import: {} imported, {} skipped", imported, skipped);
        return new ImportResult(imported, skipped, errors);
    }

    public byte[] generateImportTemplate() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Styles st = new Styles(wb);
            Sheet sh = wb.createSheet("Users");
            String[] cols = {"First Name","Last Name","Email","Role (STUDENT/TEACHER/MEZUN)",
                    "Faculty (name or code)","Graduation Year","Student ID","Password"};
            int[] w = {6000,6000,9000,10000,9000,6000,6000,6000};
            headerRow(sh, st, 0, cols, w);
            Row ex = sh.createRow(1);
            String[] eg = {"Айгерим","Бекова","aigbekov@example.com","STUDENT","Engineering Faculty","2025","2021-00123","Manas2025!"};
            for (int i=0;i<eg.length;i++) ex.createCell(i).setCellValue(eg[i]);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Style helpers
    // ══════════════════════════════════════════════════════════════════

    private static class Styles {
        final XSSFCellStyle headerStyle, sectionStyle, bodyEven, bodyOdd, numEven, numOdd, numBold, kpiLabel, kpiVal;

        Styles(XSSFWorkbook wb) {
            headerStyle = mk(wb, new byte[]{(byte)0x1b,(byte)0x2a,(byte)0x6b}, IndexedColors.WHITE.getIndex(), true, false);
            sectionStyle = mk(wb, new byte[]{(byte)0x2e,(byte)0x3f,(byte)0xa3}, IndexedColors.WHITE.getIndex(), true, false);
            bodyEven    = mk(wb, null, IndexedColors.AUTOMATIC.getIndex(), false, false);
            bodyOdd     = mkBg(wb, new byte[]{(byte)0xf7,(byte)0xf8,(byte)0xfc});
            numEven     = mkNum(wb, null);
            numOdd      = mkNumBg(wb, new byte[]{(byte)0xf7,(byte)0xf8,(byte)0xfc});
            numBold     = mk(wb, new byte[]{(byte)0x1b,(byte)0x2a,(byte)0x6b}, IndexedColors.WHITE.getIndex(), true, false);
            kpiLabel    = mkBold(wb);
            kpiVal      = mkBoldBlue(wb);
        }

        XSSFCellStyle body(int row) { return row % 2 == 0 ? bodyOdd : bodyEven; }
        XSSFCellStyle num(int row)  { return row % 2 == 0 ? numOdd  : numEven;  }

        private XSSFCellStyle mk(XSSFWorkbook wb, byte[] rgb, short fontColor, boolean bold, boolean wrap) {
            XSSFCellStyle s = wb.createCellStyle();
            if (rgb != null) { XSSFColor col = new XSSFColor(rgb, null); s.setFillForegroundColor(col); s.setFillPattern(FillPatternType.SOLID_FOREGROUND); }
            XSSFFont f = wb.createFont(); f.setBold(bold); if (fontColor != IndexedColors.AUTOMATIC.getIndex()) f.setColor(new XSSFColor(new byte[]{(byte)0xff,(byte)0xff,(byte)0xff},null)); f.setFontHeightInPoints((short)10);
            s.setFont(f); s.setWrapText(wrap);
            s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
            s.setBottomBorderColor(new XSSFColor(new byte[]{(byte)0xe2,(byte)0xe5,(byte)0xf0},null));
            s.setTopBorderColor(new XSSFColor(new byte[]{(byte)0xe2,(byte)0xe5,(byte)0xf0},null));
            return s;
        }
        private XSSFCellStyle mkBg(XSSFWorkbook wb, byte[] rgb) {
            XSSFCellStyle s = wb.createCellStyle(); XSSFColor col = new XSSFColor(rgb,null);
            s.setFillForegroundColor(col); s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            XSSFFont f = wb.createFont(); f.setFontHeightInPoints((short)10); s.setFont(f);
            s.setBorderBottom(BorderStyle.THIN); s.setBorderTop(BorderStyle.THIN);
            s.setBottomBorderColor(new XSSFColor(new byte[]{(byte)0xe2,(byte)0xe5,(byte)0xf0},null));
            return s;
        }
        private XSSFCellStyle mkNum(XSSFWorkbook wb, byte[] rgb) {
            XSSFCellStyle s = mkBg(wb, rgb!=null?rgb:new byte[]{(byte)0xff,(byte)0xff,(byte)0xff});
            s.setAlignment(HorizontalAlignment.RIGHT); return s;
        }
        private XSSFCellStyle mkNumBg(XSSFWorkbook wb, byte[] rgb) {
            XSSFCellStyle s = mkBg(wb, rgb); s.setAlignment(HorizontalAlignment.RIGHT); return s;
        }
        private XSSFCellStyle mkBold(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle(); XSSFFont f = wb.createFont(); f.setBold(true); f.setFontHeightInPoints((short)10); s.setFont(f); return s;
        }
        private XSSFCellStyle mkBoldBlue(XSSFWorkbook wb) {
            XSSFCellStyle s = wb.createCellStyle(); XSSFFont f = wb.createFont(); f.setBold(true);
            f.setColor(new XSSFColor(new byte[]{(byte)0x1b,(byte)0x2a,(byte)0x6b},null)); f.setFontHeightInPoints((short)11);
            s.setFont(f); return s;
        }
    }

    // ══════════════════════════════════════════════════════════════════
    //  Cell / row helpers
    // ══════════════════════════════════════════════════════════════════

    private Sheet createSheet(XSSFWorkbook wb, String name) {
        // Excel sheet names max 31 chars, strip emoji for safety
        String safe = name.replaceAll("[^\\x20-\\x7E]","").trim();
        if (safe.isEmpty()) safe = name.substring(0, Math.min(name.length(),28));
        if (safe.length() > 31) safe = safe.substring(0, 31);
        return wb.createSheet(safe);
    }

    private void headerRow(Sheet sh, Styles st, int rowNum, String[] labels, int[] widths) {
        Row row = sh.createRow(rowNum);
        row.setHeight((short) 460);
        for (int i = 0; i < labels.length; i++) {
            if (i < widths.length) sh.setColumnWidth(i, widths[i]);
            Cell c = row.createCell(i);
            c.setCellValue(labels[i]);
            c.setCellStyle(st.headerStyle);
        }
        sh.createFreezePane(0, rowNum + 1);
    }

    private int titleRow(Sheet sh, Styles st, String title, int cols) {
        Row r = sh.createRow(0);
        r.setHeight((short) 600);
        Cell c = r.createCell(0);
        c.setCellValue(title);
        c.setCellStyle(st.sectionStyle);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, cols - 1));
        return 2;
    }

    private int kpiSection(Sheet sh, Styles st, int r, String label) {
        Row row = sh.createRow(r); row.setHeight((short)380);
        Cell c = row.createCell(0); c.setCellValue(label); c.setCellStyle(st.sectionStyle);
        row.createCell(1).setCellStyle(st.sectionStyle);
        return r + 1;
    }

    private int kv(Sheet sh, Styles st, int r, String label, long val) {
        Row row = sh.createRow(r);
        Cell lc = row.createCell(0); lc.setCellValue(label); lc.setCellStyle(st.kpiLabel);
        Cell vc = row.createCell(1); vc.setCellValue(val);   vc.setCellStyle(st.kpiVal);
        return r + 1;
    }

    private int kv(Sheet sh, Styles st, int r, String label, String val) {
        Row row = sh.createRow(r);
        Cell lc = row.createCell(0); lc.setCellValue(label);       lc.setCellStyle(st.kpiLabel);
        Cell vc = row.createCell(1); vc.setCellValue(val!=null?val:"—"); vc.setCellStyle(st.kpiVal);
        return r + 1;
    }

    private void c(Row row, int col, String val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val != null ? val : "—");
        if (style != null) cell.setCellStyle(style);
    }

    private void n(Row row, int col, long val, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(val);
        if (style != null) cell.setCellStyle(style);
    }

    private void autoFilter(Sheet sh, int lastCol) {
        sh.setAutoFilter(new CellRangeAddress(0, 0, 0, lastCol));
    }

    private String nz(String s) { return s != null && !s.isBlank() ? s : "—"; }

    private String cellStr(org.apache.poi.ss.usermodel.Row row, int col) {
        org.apache.poi.ss.usermodel.Cell c = row.getCell(col);
        if (c == null) return null;
        return switch (c.getCellType()) {
            case STRING  -> c.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            case BOOLEAN -> String.valueOf(c.getBooleanCellValue());
            default      -> null;
        };
    }
}