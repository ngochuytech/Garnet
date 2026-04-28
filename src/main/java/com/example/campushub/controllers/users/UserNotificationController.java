package com.example.campushub.controllers.users;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.services.NotificationService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/notifications")
@RequiredArgsConstructor
public class UserNotificationController {
        private final NotificationService notificationService;

        @GetMapping("/me")
        public ResponseEntity<?> getMyNotifications(@AuthenticationPrincipal User currentUser,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "25") int size,
                        @RequestParam(defaultValue = "updatedAt") String sortBy,
                        @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
                Sort sort = sortDir.equalsIgnoreCase("desc")
                                ? Sort.by(sortBy).descending()
                                : Sort.by(sortBy).ascending();
                Pageable pageable = PageRequest.of(page, size, sort);
                return ResponseEntity.ok()
                                .body(PagedResponse.from(notificationService.getNotificationByCurrentUser(currentUser,
                                                pageable)));
        }

        @GetMapping("/me/unread-count")
        public ResponseEntity<?> getUnreadNotificationCount(@AuthenticationPrincipal User currentUser) throws Exception {
                long unreadCount = notificationService.getUnreadNotificationCount(currentUser);
                return ResponseEntity.ok(ApiResponse.ok(unreadCount));
        }

        @GetMapping("/me/type/{notificationType}")
        public ResponseEntity<?> getNotificationsByType(@AuthenticationPrincipal User currentUser,
                        @PathVariable String notificationType,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "25") int size,
                        @RequestParam(defaultValue = "updatedAt") String sortBy,
                        @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
                Sort sort = sortDir.equalsIgnoreCase("desc")
                                ? Sort.by(sortBy).descending()
                                : Sort.by(sortBy).ascending();
                Pageable pageable = PageRequest.of(page, size, sort);
                return ResponseEntity.ok()
                                .body(PagedResponse.from(notificationService.getNotificationsByType(currentUser,
                                                notificationType, pageable)));
        }

        @PutMapping("/me/mark-all-read")
        public ResponseEntity<?> markAllAsRead(@AuthenticationPrincipal User currentUser) throws Exception {
                notificationService.markAllAsRead(currentUser);
                return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu tất cả thông báo là đã đọc!"));
        }

        @PutMapping("/me/{notificationId}/mark-read")
        public ResponseEntity<?> markAsRead(@AuthenticationPrincipal User currentUser,
                        @PathVariable String notificationId) throws Exception {
                notificationService.markAsRead(currentUser, notificationId);
                return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu thông báo là đã đọc!"));
        }

}
