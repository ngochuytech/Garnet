package com.example.campushub.events;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.enums.NotificationType;
import com.example.campushub.models.jpa.Notification;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.NotificationRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.responses.NotificationResponse;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class NotificationListener {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Async
    @EventListener
    @Transactional("transactionManager")
    public void handleNotificationEvent(NotificationEvent event) {
        try {
            User actor = userRepository.findById(event.getActorId()).orElse(null);
            if (actor == null)
                return;

            // Các loại thông báo có thể gom nhóm được (liên quan đến 1 target cụ thể và có
            // thể lặp lại nhiều lần bởi nhiều User)
            if (event.getType() == NotificationType.LIKE_POST ||
                    event.getType() == NotificationType.LIKE_COMMENT ||
                    event.getType() == NotificationType.COMMENT_POST ||
                    event.getType() == NotificationType.REPLY_COMMENT ||
                    event.getType() == NotificationType.SHARE_POST) {

                Optional<Notification> existingNotif = notificationRepository
                        .findFirstByRecipientIdAndTargetIdAndTypeOrderByCreatedAtDesc(
                                event.getRecipientId(), event.getTargetId(), event.getType());

                if (existingNotif.isPresent()) {
                    // Đã có thông báo, gom nhóm các tương tác lại thành 1 thông báo duy nhất, cập
                    // nhật message
                    Notification notif = existingNotif.get();
                    // Cập nhật người tương tác mới nhất để query được tên nếu cần thiết
                    notif.setActor(actor);

                    long totalCount = 0;
                    String baseMessage = "";

                    switch (event.getType()) {
                        case LIKE_POST:
                            totalCount = postRepository.findById(event.getTargetId())
                                    .map(p -> (long) p.getLiked()).orElse(1L);
                            baseMessage = " đã thích bài viết của bạn";
                            break;
                        case LIKE_COMMENT:
                            totalCount = commentRepository.findById(event.getTargetId())
                                    .map(c -> (long) c.getLiked()).orElse(1L);
                            baseMessage = " đã thích bình luận của bạn";
                            break;
                        case COMMENT_POST:
                            totalCount = postRepository.findById(event.getTargetId())
                                    .map(p -> (long) p.getCommentCount()).orElse(1L);
                            baseMessage = " đã bình luận về bài viết của bạn";
                            break;
                        case REPLY_COMMENT:
                            totalCount = commentRepository.findById(event.getTargetId())
                                    .map(c -> (long) c.getReplyCount()).orElse(1L);
                            baseMessage = " đã trả lời bình luận của bạn";
                            break;
                        case SHARE_POST:
                            totalCount = postRepository.findById(event.getTargetId())
                                    .map(p -> (long) p.getSharedCount()).orElse(1L);
                            baseMessage = " đã chia sẻ bài viết của bạn";
                            break;
                        default:
                            baseMessage = " đã tương tác với bạn";
                            totalCount = 1L;
                    }

                    long othersCount = totalCount > 0 ? totalCount - 1 : 0;

                    if (othersCount > 0) {
                        notif.setMessage("Một người dùng và " + othersCount + " người khác" + baseMessage);
                    } else {
                        notif.setMessage(event.getMessage()); // Fallback message
                    }

                    notif.setRead(false);

                    notificationRepository.save(notif);

                    // Đẩy thông báo mới cập nhật về Frontend qua WebSocket dưới dạng DTO
                    messagingTemplate.convertAndSendToUser(
                            event.getRecipientName(),
                            "/queue/notifications",
                            NotificationResponse.fromEntity(notif));

                    return;
                }
                
                // NẾU THÔNG BÁO CHƯA TỒN TẠI THÌ TẠO MỚI Ở ĐÂY
                Notification notif = Notification.builder()
                        .recipientId(event.getRecipientId())
                        .actor(actor)
                        .type(event.getType())
                        .targetType(event.getTargetType())
                        .targetId(event.getTargetId())
                        .message(event.getMessage())
                        .build();

                notificationRepository.save(notif);

                messagingTemplate.convertAndSendToUser(
                        event.getRecipientName(),
                        "/queue/notifications",
                        NotificationResponse.fromEntity(notif));

            } else if (event.getType() == NotificationType.NEW_FOLLOWER) {
                // Với thông báo theo dõi, nếu đã tồn tại thì không tạo thêm, tránh spam
                Optional<Notification> existingFollowNotif = notificationRepository
                        .findFirstByRecipientIdAndActorIdAndType(
                                event.getRecipientId(), event.getActorId(), event.getType());

                Notification notif;
                if (existingFollowNotif.isPresent()) {
                    notif = existingFollowNotif.get();
                    notif.setRead(false);
                    notif.setCreatedAt(LocalDateTime.now());
                } else {
                    // Chưa có thì tạo mới
                    notif = Notification.builder()
                            .recipientId(event.getRecipientId())
                            .actor(actor)
                            .type(event.getType())
                            .targetType(event.getTargetType())
                            .targetId(event.getTargetId())
                            .message(event.getMessage())
                            .build();
                }
                notificationRepository.save(notif);
                // Đẩy thông báo mới cập nhật về Frontend qua WebSocket dưới dạng DTO
                messagingTemplate.convertAndSendToUser(
                        event.getRecipientName(),
                        "/queue/notifications",
                        NotificationResponse.fromEntity(notif));

                return;
            } else {
                Notification notif = Notification.builder()
                        .recipientId(event.getRecipientId())
                        .actor(actor)
                        .type(event.getType())
                        .targetType(event.getTargetType())
                        .targetId(event.getTargetId())
                        .message(event.getMessage())
                        .build();

                notificationRepository.save(notif);

                // Đẩy thông báo mới cập nhật về Frontend qua WebSocket dưới dạng DTO
                messagingTemplate.convertAndSendToUser(
                        event.getRecipientName(),
                        "/queue/notifications",
                        NotificationResponse.fromEntity(notif));
            }

        } catch (Exception e) {
            System.err.println("Lỗi lưu thông báo, : " + e.getMessage());
        }
    }
}
