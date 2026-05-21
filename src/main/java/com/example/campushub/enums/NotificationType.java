package com.example.campushub.enums;

public enum NotificationType {
    LIKE_POST,
    LIKE_COMMENT,
    COMMENT_POST,
    REPLY_COMMENT,
    SHARE_POST,
    NEW_FOLLOWER,
    GROUP_JOIN_REQUEST,
    GROUP_JOIN_APPROVED,
    GROUP_JOIN_REJECTED,
    GROUP_MEMBER_KICKED,
    GROUP_NAME_UPDATED,
    GROUP_LOCKED,
    GROUP_UNLOCKED,
    CONTENT_REPORTED,
    REPORT_RESOLVED, // Khi Admin xử lý xong report, báo lại cho người report
    SYSTEM_ALERT // Thông báo chung từ hệ thống
}