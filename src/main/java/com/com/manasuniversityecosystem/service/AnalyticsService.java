package com.com.manasuniversityecosystem.service;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.Faculty;
import com.com.manasuniversityecosystem.domain.enums.UserRole;
import com.com.manasuniversityecosystem.domain.enums.UserStatus;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.UserRepository;
import com.com.manasuniversityecosystem.repository.academic.CourseRepository;
import com.com.manasuniversityecosystem.repository.career.JobApplicationRepository;
import com.com.manasuniversityecosystem.repository.career.JobRepository;
import com.com.manasuniversityecosystem.repository.social.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private final UserRepository       userRepo;
    private final FacultyRepository    facultyRepo;
    private final JobRepository        jobRepo;
    private final JobApplicationRepository jobAppRepo;
    private final CourseRepository     courseRepo;
    private final PostRepository       postRepo;

    // ─────────────────────────────────────────────────────────────────
    //  PLATFORM-WIDE STATS (Admin view)
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public PlatformStats getPlatformStats() {
        PlatformStats s = new PlatformStats();

        s.totalUsers      = userRepo.count();
        s.totalStudents   = userRepo.countByRole(UserRole.STUDENT);
        s.totalTeachers   = userRepo.countByRole(UserRole.TEACHER);
        s.totalMezuns     = userRepo.countByRole(UserRole.MEZUN);
        s.totalEmployers  = userRepo.countByRole(UserRole.EMPLOYER);
        s.totalFacultyAdmins = userRepo.countByRole(UserRole.FACULTY_ADMIN);
        s.totalAdmins     = userRepo.countByRole(UserRole.ADMIN);
        s.totalSecretaries = userRepo.countByRole(UserRole.SECRETARY);

        s.activeUsers     = userRepo.countByStatus(UserStatus.ACTIVE);
        s.pendingUsers    = userRepo.countByStatus(UserStatus.PENDING);
        s.suspendedUsers  = userRepo.countByStatus(UserStatus.SUSPENDED);

        s.totalJobs       = jobRepo.count();
        s.activeJobs      = jobRepo.countByIsActiveTrue();
        s.totalApplications = jobAppRepo.count();
        s.totalCourses    = courseRepo.count();
        s.totalPosts      = postRepo.count();
        s.totalFaculties  = facultyRepo.count();

        // Per-faculty breakdown
        s.facultyStats = buildFacultyStats(null);

        // Employer breakdown by company field
        s.employers = userRepo.findAllEmployers();
        s.employersByField = s.employers.stream()
                .filter(e -> e.getCompanyField() != null)
                .collect(Collectors.groupingBy(AppUser::getCompanyField, Collectors.counting()));

        // Mezun workplaces (grouped by company)
        List<AppUser> mezunsWithWork = userRepo.findMezunsWithWorkPlace();
        s.mezunsByWorkplace = mezunsWithWork.stream()
                .collect(Collectors.groupingBy(AppUser::getWorkPlace,
                        LinkedHashMap::new, Collectors.toList()));

        // Mezun gender distribution
        List<AppUser> allMezuns = userRepo.findByRole(UserRole.MEZUN);
        s.mezunGenderBreakdown = allMezuns.stream()
                .filter(u -> u.getGender() != null)
                .collect(Collectors.groupingBy(AppUser::getGender, Collectors.counting()));

        // Student gender distribution
        List<AppUser> allStudents = userRepo.findByRole(UserRole.STUDENT);
        s.studentGenderBreakdown = allStudents.stream()
                .filter(u -> u.getGender() != null)
                .collect(Collectors.groupingBy(AppUser::getGender, Collectors.counting()));

        // Mezuns by graduation year
        s.mezunsByYear = allMezuns.stream()
                .filter(u -> u.getGraduationYear() != null)
                .collect(Collectors.groupingBy(AppUser::getGraduationYear, TreeMap::new, Collectors.counting()));

        return s;
    }

    // ─────────────────────────────────────────────────────────────────
    //  FACULTY-SPECIFIC STATS (Faculty Admin view)
    // ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public FacultyAnalytics getFacultyAnalytics(UUID facultyId) {
        Faculty faculty = facultyRepo.findById(facultyId)
                .orElseThrow(() -> new IllegalArgumentException("Faculty not found"));

        FacultyAnalytics fa = new FacultyAnalytics();
        fa.faculty = faculty;

        fa.students  = userRepo.findByFacultyAndRole(facultyId, UserRole.STUDENT);
        fa.teachers  = userRepo.findByFacultyAndRole(facultyId, UserRole.TEACHER);
        fa.mezuns    = userRepo.findMezunsByFaculty(facultyId);
        fa.admins    = userRepo.findByFacultyAndRole(facultyId, UserRole.FACULTY_ADMIN);

        fa.totalStudents  = fa.students.size();
        fa.totalTeachers  = fa.teachers.size();
        fa.totalMezuns    = fa.mezuns.size();
        fa.totalCourses   = courseRepo.findByFacultyIdAndIsActiveTrueOrderByCodeAsc(facultyId).size();

        // Mezuns with workplaces grouped by company
        List<AppUser> mezunsWithWork = userRepo.findMezunsWithWorkPlaceByFaculty(facultyId);
        fa.mezunsByWorkplace = mezunsWithWork.stream()
                .collect(Collectors.groupingBy(AppUser::getWorkPlace,
                        LinkedHashMap::new, Collectors.toList()));

        // Mezuns by graduation year
        fa.mezunsByYear = fa.mezuns.stream()
                .filter(u -> u.getGraduationYear() != null)
                .collect(Collectors.groupingBy(AppUser::getGraduationYear, TreeMap::new, Collectors.counting()));

        // Gender breakdown for students
        fa.studentGenderBreakdown = fa.students.stream()
                .filter(u -> u.getGender() != null)
                .collect(Collectors.groupingBy(AppUser::getGender, Collectors.counting()));

        // Gender breakdown for mezuns
        fa.mezunGenderBreakdown = fa.mezuns.stream()
                .filter(u -> u.getGender() != null)
                .collect(Collectors.groupingBy(AppUser::getGender, Collectors.counting()));

        // Study year breakdown
        fa.studentsByYear = fa.students.stream()
                .filter(u -> u.getProfile() != null && u.getProfile().getStudyYear() != null)
                .collect(Collectors.groupingBy(
                        u -> u.getProfile().getStudyYear(), TreeMap::new, Collectors.counting()));

        // University verified mezuns
        fa.verifiedMezuns = fa.mezuns.stream().filter(AppUser::isUniversityVerified).count();

        // Employers with active job postings
        List<com.com.manasuniversityecosystem.domain.entity.career.JobListing> activeJobs =
                jobRepo.findAllActiveJobsWithEmployers();
        fa.employerJobs = activeJobs.stream()
                .collect(Collectors.groupingBy(
                        com.com.manasuniversityecosystem.domain.entity.career.JobListing::getPostedBy,
                        LinkedHashMap::new,
                        Collectors.toList()));
        fa.employers = new ArrayList<>(fa.employerJobs.keySet());

        return fa;
    }

    // ─────────────────────────────────────────────────────────────────
    //  Shared: build per-faculty rows for the global table
    // ─────────────────────────────────────────────────────────────────

    public List<FacultyRow> buildFacultyStats(UUID filterFacultyId) {
        List<Faculty> faculties = filterFacultyId != null
                ? facultyRepo.findById(filterFacultyId).map(List::of).orElse(List.of())
                : facultyRepo.findAllByOrderByNameAsc();

        return faculties.stream().map(f -> {
            FacultyRow row = new FacultyRow();
            row.faculty       = f;
            row.students      = userRepo.countActiveFacultyByRole(f.getId(), UserRole.STUDENT);
            row.teachers      = userRepo.countActiveFacultyByRole(f.getId(), UserRole.TEACHER);
            row.mezuns        = userRepo.countActiveFacultyByRole(f.getId(), UserRole.MEZUN);
            row.facultyAdmins = userRepo.countActiveFacultyByRole(f.getId(), UserRole.FACULTY_ADMIN);
            row.courses       = courseRepo.findByFacultyIdAndIsActiveTrueOrderByCodeAsc(f.getId()).size();
            return row;
        }).collect(Collectors.toList());
    }

    // ─────────────────────────────────────────────────────────────────
    //  DTOs
    // ─────────────────────────────────────────────────────────────────

    public static class PlatformStats {
        public long totalUsers, totalStudents, totalTeachers, totalMezuns;
        public long totalEmployers, totalFacultyAdmins, totalAdmins, totalSecretaries;
        public long activeUsers, pendingUsers, suspendedUsers;
        public long totalJobs, activeJobs, totalApplications, totalCourses, totalPosts, totalFaculties;
        public List<FacultyRow> facultyStats;
        public List<AppUser> employers;
        public Map<String, Long> employersByField;
        public Map<String, List<AppUser>> mezunsByWorkplace;
        public Map<String, Long> mezunGenderBreakdown;
        public Map<String, Long> studentGenderBreakdown;
        public Map<Integer, Long> mezunsByYear;
    }

    public static class FacultyAnalytics {
        public Faculty faculty;
        public List<AppUser> students, teachers, mezuns, admins;
        public int totalStudents, totalTeachers, totalMezuns, totalCourses;
        public long verifiedMezuns;
        public Map<String, List<AppUser>> mezunsByWorkplace;
        public Map<Integer, Long> mezunsByYear;
        public Map<String, Long> studentGenderBreakdown;
        public Map<String, Long> mezunGenderBreakdown;
        public Map<Integer, Long> studentsByYear;
        /** Employers who have at least one active job posting */
        public List<AppUser> employers;
        /** Active job listings grouped by the employer (AppUser) who posted them */
        public Map<AppUser, List<com.com.manasuniversityecosystem.domain.entity.career.JobListing>> employerJobs;
    }

    public static class FacultyRow {
        public Faculty faculty;
        public long students, teachers, mezuns, facultyAdmins, courses;
    }
}