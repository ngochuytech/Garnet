package com.example.campushub.services;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.enums.NotificationType;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.models.jpa.Notification;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.NotificationRepository;
import com.example.campushub.responses.NotificationResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public Page<NotificationResponse> getNotificationByCurrentUser(User currentUser, Pageable pageable) throws Exception {
        return notificationRepository.findByRecipientId(currentUser.getId(), pageable).map(NotificationResponse::fromEntity);
    }

    public long getUnreadNotificationCount(User currentUser) throws Exception {
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUser.getId());
    }

    public Page<NotificationResponse> getNotificationsByType(User currentUser, String typeStr, Pageable pageable) throws Exception {
        NotificationType type;
        try {
            type = NotificationType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResourceNotFoundException("Loại thông báo không hợp lệ: " + typeStr);
        }
        return notificationRepository.findByRecipientIdAndType(currentUser.getId(), type, pageable)
                .map(NotificationResponse::fromEntity);
    }

    @Transactional("transactionManager")
    public void markAllAsRead(User currentUser) throws Exception {
        notificationRepository.markAllAsReadByRecipientId(currentUser.getId());
    }

    @Transactional("transactionManager")
    public void markAsRead(User currentUser, String notificationId) throws Exception {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Thông báo không tồn tại hoặc không thuộc về bạn"));
        if(notification.isRead()) {
            return;
        }
        notification.setRead(true);
        notificationRepository.save(notification);
    }
}
