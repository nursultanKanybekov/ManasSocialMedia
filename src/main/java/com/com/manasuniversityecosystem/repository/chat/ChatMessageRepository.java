package com.com.manasuniversityecosystem.repository.chat;

import com.com.manasuniversityecosystem.domain.entity.chat.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {

    /** Paginated history, newest first. Caller reverses for display. */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender s LEFT JOIN FETCH s.profile " +
            "WHERE m.room.id = :roomId AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<ChatMessage> findByRoomIdOrderByCreatedAtDesc(
            @Param("roomId") UUID roomId, Pageable pageable);

    /** Pinned messages in a room */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender s LEFT JOIN FETCH s.profile " +
            "WHERE m.room.id = :roomId AND m.isPinned = true AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    List<ChatMessage> findPinnedByRoomId(@Param("roomId") UUID roomId);

    /** Unread count for a user in a room since their last read timestamp */
    @Query(value = """
        SELECT COUNT(m.id) FROM chat_message m
        JOIN chat_participant cp ON cp.room_id = m.room_id AND cp.user_id = :userId
        WHERE m.room_id = :roomId
          AND m.sender_id != :userId
          AND m.is_deleted = false
          AND (cp.last_read_at IS NULL OR m.created_at > cp.last_read_at)
        """, nativeQuery = true)
    long countUnread(
            @Param("roomId") UUID roomId,
            @Param("userId") UUID userId);

    long countByRoomId(UUID roomId);

    /** Single latest message per room — for sidebar preview */
    @Query("SELECT m FROM ChatMessage m JOIN FETCH m.sender s " +
            "WHERE m.room.id = :roomId AND m.isDeleted = false " +
            "ORDER BY m.createdAt DESC")
    Page<ChatMessage> findLastMessageByRoomId(
            @Param("roomId") UUID roomId, Pageable pageable);
}