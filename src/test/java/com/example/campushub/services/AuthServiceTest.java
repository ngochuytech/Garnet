package com.example.campushub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.dtos.auth.LoginDTO;
import com.example.campushub.dtos.auth.RegisterDTO;
import com.example.campushub.dtos.auth.ResetPasswordDTO;
import com.example.campushub.enums.UserActionTokenPurpose;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserActionToken;
import com.example.campushub.repositories.jpa.UserActionTokenRepository;
import com.example.campushub.repositories.jpa.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailService emailService;

    @Mock
    private UserActionTokenRepository userActionTokenRepository;

    @InjectMocks
    private AuthService authService;

    @Test
    void loginReturnsJwtWhenCredentialsMatch() throws Exception {
        User user = user("user-1", "user@example.com", "encoded-password");
        LoginDTO dto = LoginDTO.builder()
                .email("user@example.com")
                .password("correct-password")
                .build();

        when(userRepository.findByEmail(dto.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.getPassword(), user.getPassword())).thenReturn(true);
        when(jwtTokenProvider.generateToken(user)).thenReturn("access-token");

        String token = authService.login(dto);

        assertEquals("access-token", token);
        verify(jwtTokenProvider).generateToken(user);
    }

    @Test
    void loginRejectsWrongPassword() {
        User user = user("user-1", "user@example.com", "encoded-password");
        LoginDTO dto = LoginDTO.builder()
                .email("user@example.com")
                .password("wrong-password")
                .build();

        when(userRepository.findByEmail(dto.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(dto.getPassword(), user.getPassword())).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> authService.login(dto));

        verify(jwtTokenProvider, never()).generateToken(any(User.class));
    }

    @Test
    void registerRejectsDuplicateEmail() {
        RegisterDTO dto = RegisterDTO.builder()
                .fullname("Test User")
                .email("duplicate@example.com")
                .password("password123")
                .confirmPassword("password123")
                .build();
        when(userRepository.existsByEmail(dto.getEmail())).thenReturn(true);

        assertThrows(Exception.class, () -> authService.register(dto));

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void resetPasswordConsumesValidTokenOnce() throws Exception {
        String rawToken = "valid-reset-token";
        User user = user("user-1", "user@example.com", "old-password");
        UserActionToken actionToken = UserActionToken.builder()
                .user(user)
                .purpose(UserActionTokenPurpose.PASSWORD_RESET)
                .tokenHash(sha256(rawToken))
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .build();
        ResetPasswordDTO dto = ResetPasswordDTO.builder()
                .token(rawToken)
                .newPassword("new-password")
                .confirmPassword("new-password")
                .build();

        when(userActionTokenRepository.findByTokenHashAndPurposeForUpdate(
                sha256(rawToken), UserActionTokenPurpose.PASSWORD_RESET))
                .thenReturn(Optional.of(actionToken));
        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("new-password")).thenReturn("encoded-new-password");

        authService.resetPassword(dto);

        assertEquals("encoded-new-password", user.getPassword());
        assertNotNull(actionToken.getConsumedAt());
        verify(userRepository).save(user);
        verify(userActionTokenRepository).save(actionToken);
    }

    private User user(String id, String email, String password) {
        return User.builder()
                .id(id)
                .fullName("Test User")
                .email(email)
                .password(password)
                .build();
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte currentByte : hash) {
                result.append(String.format("%02x", currentByte));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
