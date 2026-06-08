package com.example.campushub.services;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.dtos.auth.LoginDTO;
import com.example.campushub.dtos.auth.RegisterDTO;
import com.example.campushub.dtos.auth.ResetPasswordDTO;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {
    private static final String PASSWORD_RESET_KEY_PREFIX = "campushub:password-reset:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final EmailService emailService;

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

    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) {
            return;
        }

        userRepository.findByEmail(email.trim()).ifPresent(user -> {
            String token = generateSecureToken();
            String cacheKey = buildPasswordResetCacheKey(token);
            redisTemplate.opsForValue().set(
                    cacheKey,
                    user.getId(),
                    Duration.ofMinutes(passwordResetTokenTtlMinutes));

            String resetLink = frontendUrl + "/reset-password?token=" + token;
            emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
        });
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void resetPassword(ResetPasswordDTO dto) throws Exception {
        if (!dto.getNewPassword().equals(dto.getConfirmPassword())) {
            throw new InvalidParamException("Mật khẩu mới và xác nhận mật khẩu không khớp");
        }

        String cacheKey = buildPasswordResetCacheKey(dto.getToken());
        String userId = redisTemplate.opsForValue().get(cacheKey);
        if (userId == null) {
            throw new InvalidParamException("Token đặt lại mật khẩu không hợp lệ hoặc đã hết hạn");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new DataNotFoundException("User not found"));
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
        redisTemplate.delete(cacheKey);
    }

    private String generateSecureToken() {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String buildPasswordResetCacheKey(String token) {
        return PASSWORD_RESET_KEY_PREFIX + token;
    }
}
