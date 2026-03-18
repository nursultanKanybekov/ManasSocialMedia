package com.com.manasuniversityecosystem.repository.notification;

import com.com.manasuniversityecosystem.domain.entity.notification.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.actor " +
            "WHERE n.recipient.id = :userId ORDER BY n.createdAt DESC")
    Page<Notification> findByRecipientId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.actor " +
            "WHERE n.recipient.id = :userId AND n.isRead = false ORDER BY n.createdAt DESC")
    List<Notification> findUnreadByRecipientId(@Param("userId") UUID userId);

    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipient.id = :userId AND n.isRead = false")
    void markAllReadForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.id = :id AND n.recipient.id = :userId")
    void markOneRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("DELETE FROM Notification n WHERE n.recipient.id = :userId AND n.isRead = true")
    @Modifying
    void deleteReadForUser(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.recipient.id = :userId")
    void deleteByRecipientId(@Param("userId") UUID userId);

    @Modifying
    @Query("DELETE FROM Notification n WHERE n.actor.id = :userId")
    void deleteByActorId(@Param("userId") UUID userId);

    /** Used by SuperAdmin dashboard to fetch unread faculty detection alerts */
    List<Notification> findByTypeAndIsReadFalse(Notification.NotifType type);
}