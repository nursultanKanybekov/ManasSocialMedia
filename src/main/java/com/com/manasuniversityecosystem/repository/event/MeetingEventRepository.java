package com.com.manasuniversityecosystem.repository.event;

import com.com.manasuniversityecosystem.domain.entity.event.MeetingEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface MeetingEventRepository extends JpaRepository<MeetingEvent, UUID> {

    @Query("SELECT e FROM MeetingEvent e WHERE e.eventDate > :now ORDER BY e.eventDate ASC")
    List<MeetingEvent> findUpcoming(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("SELECT e FROM MeetingEvent e WHERE " +
           "(:eventType IS NULL OR e.eventType = :eventType) " +
           "ORDER BY e.eventDate DESC")
    Page<MeetingEvent> search(@Param("eventType") String eventType, Pageable pageable);
}
