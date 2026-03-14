package com.com.manasuniversityecosystem.repository.chat;

import com.com.manasuniversityecosystem.domain.entity.chat.ChatRoom;
import com.com.manasuniversityecosystem.domain.enums.RoomType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, UUID> {

    @Query("SELECT DISTINCT cr FROM ChatRoom cr " +
            "JOIN cr.participants cp " +
            "WHERE cp.user.id = :userId " +
            "ORDER BY cr.createdAt DESC")
    List<ChatRoom> findRoomsByUserId(@Param("userId") UUID userId);

    /** Rooms ordered by the most recent message (for sidebar) */
    @Query(value = """
        SELECT cr.* FROM chat_room cr
        JOIN chat_participant cp ON cp.room_id = cr.id AND cp.user_id = :userId
        LEFT JOIN chat_message cm ON cm.room_id = cr.id
        GROUP BY cr.id
        ORDER BY COALESCE(MAX(cm.created_at), cr.created_at) DESC
        """, nativeQuery = true)
    List<ChatRoom> findRoomsByUserIdOrderedByActivity(@Param("userId") UUID userId);

    @Query(value = """
        SELECT cr.* FROM chat_room cr
        JOIN chat_participant cp1 ON cp1.room_id = cr.id AND cp1.user_id = :userAId
        JOIN chat_participant cp2 ON cp2.room_id = cr.id AND cp2.user_id = :userBId
        WHERE cr.room_type = 'DIRECT'
        LIMIT 1
        """, nativeQuery = true)
    Optional<ChatRoom> findDirectRoom(
            @Param("userAId") UUID userAId,
            @Param("userBId") UUID userBId);

    Optional<ChatRoom> findByFacultyIdAndRoomType(UUID facultyId, RoomType roomType);

    Optional<ChatRoom> findFirstByRoomType(RoomType roomType);

    List<ChatRoom> findByRoomTypeOrderByCreatedAtDesc(RoomType roomType);

    @Query("SELECT cr FROM ChatRoom cr " +
            "JOIN FETCH cr.participants cp " +
            "JOIN FETCH cp.user u " +
            "LEFT JOIN FETCH u.profile " +
            "WHERE cr.id = :roomId")
    Optional<ChatRoom> findByIdWithParticipants(@Param("roomId") UUID roomId);

    /** Channels visible to a user: public ones + private ones they belong to */
    @Query("SELECT cr FROM ChatRoom cr LEFT JOIN cr.participants cp " +
            "WHERE cr.roomType = 'CHANNEL' " +
            "AND (cr.isPrivate = false OR cp.user.id = :userId) " +
            "ORDER BY cr.createdAt DESC")
    List<ChatRoom> findVisibleChannels(@Param("userId") UUID userId);

    /** Returns every participant user ID — native SQL, works for any role, zero lazy-load risk */
    @Query(value = "SELECT user_id FROM chat_participant WHERE room_id = :roomId", nativeQuery = true)
    List<UUID> findParticipantUserIds(@Param("roomId") UUID roomId);
}