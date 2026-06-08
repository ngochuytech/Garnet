package com.example.campushub.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    public void sendPasswordResetEmail(String recipientEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (fromEmail != null && !fromEmail.isBlank()) {
            message.setFrom(fromEmail);
        }
        message.setTo(recipientEmail);
        message.setSubject("CampusHub Gửi yêu cầu đặt lại mật khẩu");
        message.setText("""
                Chúng tôi nhận được yêu cầu đặt lại mật khẩu cho tài khoản của bạn trên CampusHub.

                Vui lòng nhấp vào liên kết sau để đặt lại mật khẩu của bạn:
                %s

                Đường dẫn này sẽ sớm hết hạn. Nếu không có nhu cầu, bạn có thể bỏ qua email này.
                """.formatted(resetLink));
        mailSender.send(message);
    }
}
