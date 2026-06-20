package com.example.campushub.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.dtos.auth.LoginDTO;
import com.example.campushub.dtos.auth.RegisterDTO;
import com.example.campushub.dtos.auth.ResetPasswordDTO;
import com.example.campushub.enums.UserActionTokenPurpose;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserActionToken;
import com.example.campushub.repositories.jpa.UserActionTokenRepository;
import com.example.campushub.repositories.jpa.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;
    private final UserActionTokenRepository userActionTokenRepository;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.password-reset.token-ttl-minutes:15}")
    private long passwordResetTokenTtlMinutes;

    public void register(RegisterDTO registerDTO) throws Exception {
        if(userRepository.existsByEmail(registerDTO.getEmail())) {
            throw new Exception("Email already exists");
        }

        User newUser = User.builder()
            .fullName(registerDTO.getFullname())
            .email(registerDTO.getEmail())
            .password(passwordEncoder.encode(registerDTO.getPassword()))
            .build();

        userRepository.save(newUser);
    }

    public String login(LoginDTO loginDTO) throws Exception {
        User user = userRepository.findByEmail(loginDTO.getEmail())
            .orElseThrow(() -> new DataNotFoundException("Invalid email or password"));

        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new DataNotFoundException("Invalid email or password");
        }

        return jwtTokenProvider.generateToken(user);
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            String token = generateSecureToken();
            userActionTokenRepository.deleteByUserAndPurposeAndConsumedAtIsNull(
                    user,
                    UserActionTokenPurpose.PASSWORD_RESET);
            userActionTokenRepository.save(UserActionToken.builder()
                    .tokenHash(hashToken(token))
                    .purpose(UserActionTokenPurpose.PASSWORD_RESET)
                    .expiresAt(LocalDateTime.now().plusMinutes(passwordResetTokenTtlMinutes))
                    .user(user)
                    .build());

            String resetLink = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        });
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) throws Exception {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidParamException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }

        UserActionToken actionToken = userActionTokenRepository
                .findByTokenHashAndPurpose(hashToken(dto.getToken()), UserActionTokenPurpose.PASSWORD_RESET)
                .orElse(null);
        if (actionToken == null || actionToken.getConsumedAt() != null || actionToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidParamException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findById(actionToken.getUser().getId())
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        actionToken.setConsumedAt(LocalDateTime.now());
        userActionTokenRepository.save(actionToken);
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
