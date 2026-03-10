package com.com.manasuniversityecosystem.service.competition;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.competition.Competition;
import com.com.manasuniversityecosystem.domain.entity.competition.CompetitionRegistration;
import com.com.manasuniversityecosystem.repository.FacultyRepository;
import com.com.manasuniversityecosystem.repository.competition.CompetitionRegistrationRepository;
import com.com.manasuniversityecosystem.repository.competition.CompetitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompetitionService {

    private final CompetitionRepository competitionRepo;
    private final CompetitionRegistrationRepository regRepo;
    private final FacultyRepository facultyRepo;

    public Page<Competition> search(String status, UUID facultyId, int page) {
        return competitionRepo.search(status, facultyId, PageRequest.of(page, 9));
    }

    public List<Competition> getLatest6() {
        return competitionRepo.findTop6ByOrderByCreatedAtDesc();
    }

    public Competition getById(UUID id) {
        return competitionRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Competition not found"));
    }

    @Transactional
    public Competition create(AppUser creator, String title, String description,
                              String prize, String organizer, UUID facultyId,
                              String startDate, String endDate) {
        Competition c = Competition.builder()
                .createdBy(creator)
                .title(title)
                .description(description)
                .prize(prize)
                .organizer(organizer)
                .status("UPCOMING")
                .build();
        if (facultyId != null) {
            facultyRepo.findById(facultyId).ifPresent(c::setFaculty);
        }
        if (startDate != null && !startDate.isBlank()) {
            c.setStartDate(java.time.LocalDate.parse(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            c.setEndDate(java.time.LocalDate.parse(endDate));
        }
        return competitionRepo.save(c);
    }

    @Transactional
    public boolean toggleRegistration(UUID competitionId, AppUser user) {
        if (regRepo.existsByCompetitionIdAndUserId(competitionId, user.getId())) {
            regRepo.findByCompetitionIdAndUserId(competitionId, user.getId())
                    .ifPresent(regRepo::delete);
            return false;
        } else {
            Competition c = getById(competitionId);
            CompetitionRegistration reg = CompetitionRegistration.builder()
                    .competition(c)
                    .user(user)
                    .build();
            regRepo.save(reg);
            return true;
        }
    }

    public boolean isRegistered(UUID competitionId, UUID userId) {
        return regRepo.existsByCompetitionIdAndUserId(competitionId, userId);
    }

    public long getRegistrationCount(UUID competitionId) {
        return regRepo.countByCompetitionId(competitionId);
    }

    @Transactional
    public void updateStatus(UUID id, String status) {
        Competition c = getById(id);
        c.setStatus(status);
        competitionRepo.save(c);
    }
}
