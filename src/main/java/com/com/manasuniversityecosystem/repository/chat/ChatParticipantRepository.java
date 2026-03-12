package com.com.manasuniversityecosystem.repository.chat;

import com.com.manasuniversityecosystem.domain.entity.AppUser;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatParticipant;
import com.com.manasuniversityecosystem.domain.entity.chat.ChatParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, ChatParticipantId> {

    /**
     * Returns all participants (AppUser) for a room except the sender.
     * Pure JPQL — no UUID casting, works on all DB drivers and roles.
     */
    @Query("SELECT cp.user FROM ChatParticipant cp WHERE cp.room.id = :roomId AND cp.user.id <> :senderId")
    List<AppUser> findParticipantsExcluding(@Param("roomId") UUID roomId, @Param("senderId") UUID senderId);
}