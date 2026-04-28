package com.example.campushub.enums;

public enum NotificationType {
    LIKE_POST,
    LIKE_COMMENT,
    COMMENT_POST,
    REPLY_COMMENT,
    SHARE_POST,
    NEW_FOLLOWER,
    REPORT_RESOLVED, // Khi Admin xử lý xong report, báo lại cho người report
    SYSTEM_ALERT // Thông báo chung từ hệ thống
}
