package com.com.manasuniversityecosystem.domain.entity.event;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "meeting_event")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MeetingEvent {

    @Id @UuidGenerator
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private AppUser createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 300)
    private String location;

    @Column(length = 500)
    private String meetingLink;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String eventType = "OFFLINE";

    private LocalDateTime eventDate;

    private Integer maxParticipants;

    @Column(nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<EventRegistration> registrations = new ArrayList<>();

    public int getRegistrationCount() {
        return registrations == null ? 0 : registrations.size();
    }

    public boolean isFull() {
        return maxParticipants != null && getRegistrationCount() >= maxParticipants;
    }
}
