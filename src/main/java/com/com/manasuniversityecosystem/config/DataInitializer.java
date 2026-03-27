package com.com.manasuniversityecosystem.config;

import com.com.manasuniversityecosystem.domain.entity.*;
import com.com.manasuniversityecosystem.domain.entity.competition.Competition;
import com.com.manasuniversityecosystem.domain.entity.event.MeetingEvent;
import com.com.manasuniversityecosystem.domain.entity.social.Post;
import com.com.manasuniversityecosystem.domain.enums.*;
import com.com.manasuniversityecosystem.repository.*;
import com.com.manasuniversityecosystem.repository.competition.CompetitionRepository;
import com.com.manasuniversityecosystem.repository.event.MeetingEventRepository;
import com.com.manasuniversityecosystem.repository.social.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final FacultyRepository facultyRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompetitionRepository competitionRepository;
    private final MeetingEventRepository eventRepository;
    private final PostRepository postRepository;
    private final ConstraintHelper constraintHelper;

    @Override
    public void run(String... args) {
        constraintHelper.fixRoleConstraint(); // runs in its own independent transaction
        createFaculties();
        createUsers();
        createSampleCompetitions();
        createSampleEvents();
        createSamplePosts();
    }

    private void createFaculties() {
        if (facultyRepository.count() > 0) return;
        List<Faculty> faculties = List.of(
                Faculty.builder().name("Faculty of Engineering & Technology").code("ENG").build(),
                Faculty.builder().name("Faculty of Economics & Management").code("ECO").build(),
                Faculty.builder().name("Faculty of Law").code("LAW").build(),
                Faculty.builder().name("Faculty of Medicine").code("MED").build(),
                Faculty.builder().name("Faculty of International Relations").code("IR").build(),
                Faculty.builder().name("Faculty of Arts & Humanities").code("ART").build(),
                Faculty.builder().name("Administration").code("ADMIN").build()
        );
        facultyRepository.saveAll(faculties);
        log.info("✅ Created {} faculties", faculties.size());
    }

    private void createUsers() {
        Faculty adminFaculty = facultyRepository.findByCode("ADMIN")
                .orElse(facultyRepository.findAll().get(0));
        Faculty engFaculty = facultyRepository.findByCode("ENG")
                .orElse(facultyRepository.findAll().get(0));
        Faculty ecoFaculty = facultyRepository.findByCode("ECO")
                .orElse(facultyRepository.findAll().get(0));

        // Super Admin
        if (!userRepository.existsByEmail("superadmin@manas.edu")) {
            Profile profile = Profile.builder().totalPoints(0).build();
            AppUser superAdmin = AppUser.builder()
                    .fullName("Super Admin")
                    .email("superadmin@manas.edu")
                    .passwordHash(passwordEncoder.encode("superadmin123"))
                    .role(UserRole.SUPER_ADMIN)
                    .status(UserStatus.ACTIVE)
                    .faculty(adminFaculty)
                    .build();
            superAdmin.setProfile(profile);
            userRepository.save(superAdmin);
            log.info("✅ SuperAdmin: superadmin@manas.edu / superadmin123");
        }

        // Admin
        if (!userRepository.existsByEmail("admin@manas.edu")) {
            Profile profile = Profile.builder().totalPoints(0).build();
            AppUser admin = AppUser.builder()
                    .fullName("System Admin")
                    .email("admin@manas.edu")
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .role(UserRole.ADMIN)
                    .status(UserStatus.ACTIVE)
                    .faculty(adminFaculty)
                    .build();
            admin.setProfile(profile);
            userRepository.save(admin);
            log.info("✅ Admin: admin@manas.edu / admin123");
        } else {
            userRepository.findByEmail("admin@manas.edu").ifPresent(u -> {
                if (u.getStatus() != UserStatus.ACTIVE) {
                    u.setStatus(UserStatus.ACTIVE);
                    userRepository.save(u);
                }
            });
        }

        // Secretary
        if (!userRepository.existsByEmail("secretary@manas.edu")) {
            Profile profile = Profile.builder().totalPoints(0).build();
            AppUser secretary = AppUser.builder()
                    .fullName("Aizat Bekova")
                    .email("secretary@manas.edu")
                    .passwordHash(passwordEncoder.encode("secretary123"))
                    .role(UserRole.SECRETARY)
                    .status(UserStatus.ACTIVE)
                    .faculty(adminFaculty)
                    .build();
            secretary.setProfile(profile);
            userRepository.save(secretary);
            log.info("✅ Secretary: secretary@manas.edu / secretary123");
        }

        // Mezun alumni
        if (!userRepository.existsByEmail("mezun1@manas.edu")) {
            Profile profile = Profile.builder().totalPoints(1250).headline("Senior Software Engineer at EPAM").build();
            AppUser mezun = AppUser.builder()
                    .fullName("Nurlan Askarov")
                    .email("mezun1@manas.edu")
                    .passwordHash(passwordEncoder.encode("mezun123"))
                    .role(UserRole.MEZUN)
                    .status(UserStatus.ACTIVE)
                    .faculty(engFaculty)
                    .graduationYear(2018)
                    .build();
            mezun.setProfile(profile);
            userRepository.save(mezun);
            log.info("✅ Mezun: mezun1@manas.edu / mezun123");
        }

        if (!userRepository.existsByEmail("mezun2@manas.edu")) {
            Profile profile = Profile.builder().totalPoints(980).headline("Business Analyst at Eti").build();
            AppUser mezun = AppUser.builder()
                    .fullName("Айгерим Сейтова")
                    .email("mezun2@manas.edu")
                    .passwordHash(passwordEncoder.encode("mezun123"))
                    .role(UserRole.MEZUN)
                    .status(UserStatus.ACTIVE)
                    .faculty(ecoFaculty)
                    .graduationYear(2020)
                    .build();
            mezun.setProfile(profile);
            userRepository.save(mezun);
        }

        // Student
        if (!userRepository.existsByEmail("student@manas.edu")) {
            Profile profile = Profile.builder().totalPoints(120).build();
            AppUser student = AppUser.builder()
                    .fullName("Timur Dzhaksybekov")
                    .email("student@manas.edu")
                    .passwordHash(passwordEncoder.encode("student123"))
                    .role(UserRole.STUDENT)
                    .status(UserStatus.ACTIVE)
                    .faculty(engFaculty)
                    .studentIdNumber("S2021001")
                    .build();
            student.setProfile(profile);
            userRepository.save(student);
            log.info("✅ Student: student@manas.edu / student123");
        }
    }

    private void createSampleCompetitions() {
        if (competitionRepository.count() > 0) return;
        Faculty engFaculty = facultyRepository.findByCode("ENG").orElse(null);
        AppUser admin = userRepository.findByEmail("admin@manas.edu").orElse(null);

        Competition c1 = Competition.builder()
                .createdBy(admin)
                .faculty(engFaculty)
                .title("Manas Hackathon 2026")
                .description("Annual hackathon competition for students and alumni. Build innovative solutions for real-world problems in 48 hours.")
                .prize("50,000 KGS + Internship Offer")
                .organizer("Manas University Tech Club")
                .status("UPCOMING")
                .startDate(LocalDate.now().plusDays(30))
                .endDate(LocalDate.now().plusDays(32))
                .build();

        Competition c2 = Competition.builder()
                .createdBy(admin)
                .title("Business Case Championship")
                .description("Present the best business case for sustainable development in Kyrgyzstan. Open to all faculties.")
                .prize("30,000 KGS + Certificate")
                .organizer("Faculty of Economics")
                .status("ACTIVE")
                .startDate(LocalDate.now().minusDays(5))
                .endDate(LocalDate.now().plusDays(15))
                .build();

        Competition c3 = Competition.builder()
                .createdBy(admin)
                .title("Research Paper Contest")
                .description("Submit your best research paper and get it published in the Manas University Journal.")
                .prize("Publication + 20,000 KGS")
                .organizer("Academic Affairs Office")
                .status("UPCOMING")
                .startDate(LocalDate.now().plusDays(14))
                .endDate(LocalDate.now().plusDays(45))
                .build();

        competitionRepository.saveAll(List.of(c1, c2, c3));
        log.info("✅ Created sample competitions");
    }

    private void createSampleEvents() {
        if (eventRepository.count() > 0) return;
        AppUser admin = userRepository.findByEmail("admin@manas.edu").orElse(null);

        MeetingEvent e1 = MeetingEvent.builder()
                .createdBy(admin)
                .title("Alumni Reunion 2026")
                .description("Annual gathering of Manas University alumni. Network, share experiences and reconnect with former classmates.")
                .location("Manas University Main Hall, Bishkek")
                .eventType("OFFLINE")
                .eventDate(LocalDateTime.now().plusDays(20))
                .maxParticipants(200)
                .build();

        MeetingEvent e2 = MeetingEvent.builder()
                .createdBy(admin)
                .title("Career Tech Webinar")
                .description("Online session featuring alumni working in top tech companies. Topics: resume, interviews, career growth.")
                .meetingLink("https://zoom.us/j/example")
                .eventType("ONLINE")
                .eventDate(LocalDateTime.now().plusDays(7))
                .maxParticipants(500)
                .build();

        MeetingEvent e3 = MeetingEvent.builder()
                .createdBy(admin)
                .title("Entrepreneurship Summit")
                .description("Hybrid event connecting student entrepreneurs with successful alumni founders and investors.")
                .location("Business Faculty Auditorium")
                .meetingLink("https://zoom.us/j/summit")
                .eventType("HYBRID")
                .eventDate(LocalDateTime.now().plusDays(45))
                .maxParticipants(150)
                .build();

        eventRepository.saveAll(List.of(e1, e2, e3));
        log.info("✅ Created sample events");
    }

    private void createSamplePosts() {
        if (postRepository.count() > 0) return;
        AppUser admin = userRepository.findByEmail("admin@manas.edu").orElse(null);
        AppUser mezun = userRepository.findByEmail("mezun1@manas.edu").orElse(admin);

        java.util.Map<String,String> c1 = new java.util.HashMap<>();
        c1.put("en", "Manas University ranks top in Central Asia in 2026 QS Rankings!");
        c1.put("ru", "Университет Манас занял первое место в рейтинге QS по Центральной Азии 2026!");
        c1.put("ky", "Манас Университети 2026-жылдагы QS рейтингинде Борбордук Азияда биринчи орунду ээледи!");
        c1.put("tr", "Manas Üniversitesi 2026 QS Sıralamasında Orta Asya'da birinci oldu!");

        Post p1 = Post.builder()
                .author(admin)
                .postType(PostType.UNIVERSITY_NEWS)
                .contentI18n(c1)
                .isPinned(true)
                .build();

        java.util.Map<String,String> c2 = new java.util.HashMap<>();
        c2.put("en", "Just got promoted to Senior Engineer at EPAM Systems! Thanks to all my Manas professors!");
        c2.put("ru", "Только что получил повышение до Senior Engineer в EPAM Systems! Спасибо всем преподавателям Манаса!");
        c2.put("ky", "EPAM Systemsде Senior Engineer болуп жогорулатылдым! Манас окутуучуларыма чоң рахмат!");
        c2.put("tr", "EPAM Systems'de Senior Engineer'e terfi ettim! Tüm Manas hocalarıma teşekkürler!");

        Post p2 = Post.builder()
                .author(mezun)
                .postType(PostType.NEWS)
                .contentI18n(c2)
                .build();

        java.util.Map<String,String> c3 = new java.util.HashMap<>();
        c3.put("en", "Registration for Spring 2026 semester courses is now open. Visit the student portal.");
        c3.put("ru", "Регистрация на курсы весеннего семестра 2026 открыта. Посетите студенческий портал.");
        c3.put("ky", "2026-жылдын жазгы семестрине жазылуу ачык. Студенттик порталга кириңиз.");
        c3.put("tr", "2026 Bahar Dönemi ders kaydı açılmıştır. Öğrenci portalını ziyaret edin.");

        Post p3 = Post.builder()
                .author(admin)
                .postType(PostType.ANNOUNCEMENT)
                .contentI18n(c3)
                .build();

        postRepository.saveAll(java.util.List.of(p1, p2, p3));
        log.info("✅ Created sample posts");
    }
}