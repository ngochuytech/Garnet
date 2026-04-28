package com.example.campushub.repositories.jpa;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.campushub.enums.NotificationType;
import com.example.campushub.models.jpa.Notification;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, String>{
    Optional<Notification> findFirstByRecipientIdAndTargetIdAndTypeOrderByCreatedAtDesc(String recipientId, String targetId, NotificationType type);

    Page<Notification> findByRecipientId(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndType(String recipientId, NotificationType type, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(String recipientId);

    Optional<Notification> findByIdAndRecipientId(String id, String recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.recipientId = :recipientId AND n.isRead = false")
    void markAllAsReadByRecipientId(@Param("recipientId") String recipientId);
}
