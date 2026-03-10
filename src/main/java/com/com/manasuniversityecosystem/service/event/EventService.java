package com.com.manasuniversityecosystem.service.event;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.event.EventRegistration;
import com.com.manasuniversityecosystem.domain.entity.event.MeetingEvent;
import com.com.manasuniversityecosystem.repository.event.EventRegistrationRepository;
import com.com.manasuniversityecosystem.repository.event.MeetingEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final MeetingEventRepository eventRepo;
    private final EventRegistrationRepository regRepo;

    public List<MeetingEvent> getUpcoming(int limit) {
        return eventRepo.findUpcoming(LocalDateTime.now(), PageRequest.of(0, limit));
    }

    public Page<MeetingEvent> search(String eventType, int page) {
        return eventRepo.search(eventType, PageRequest.of(page, 9));
    }

    public MeetingEvent getById(UUID id) {
        return eventRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Event not found"));
    }

    @Transactional
    public MeetingEvent create(AppUser creator, String title, String description,
                                String location, String meetingLink,
                                String eventType, String eventDate, Integer maxParticipants) {
        MeetingEvent event = MeetingEvent.builder()
                .createdBy(creator)
                .title(title)
                .description(description)
                .location(location)
                .meetingLink(meetingLink)
                .eventType(eventType != null ? eventType : "OFFLINE")
                .maxParticipants(maxParticipants)
                .build();
        if (eventDate != null && !eventDate.isBlank()) {
            event.setEventDate(LocalDateTime.parse(eventDate));
        }
        return eventRepo.save(event);
    }

    @Transactional
    public boolean toggleRegistration(UUID eventId, AppUser user) {
        if (regRepo.existsByEventIdAndUserId(eventId, user.getId())) {
            regRepo.findByEventIdAndUserId(eventId, user.getId())
                    .ifPresent(regRepo::delete);
            return false;
        } else {
            MeetingEvent event = getById(eventId);
            if (event.isFull()) {
                throw new RuntimeException("Event is full");
            }
            EventRegistration reg = EventRegistration.builder()
                    .event(event)
                    .user(user)
                    .build();
            regRepo.save(reg);
            return true;
        }
    }

    public boolean isRegistered(UUID eventId, UUID userId) {
        return regRepo.existsByEventIdAndUserId(eventId, userId);
    }

    public long getRegistrationCount(UUID eventId) {
        return regRepo.countByEventId(eventId);
    }
}
