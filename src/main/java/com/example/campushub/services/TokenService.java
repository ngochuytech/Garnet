package com.example.campushub.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.auth.ExpiredTokenException;
import com.example.campushub.models.jpa.Token;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.TokenRepository;
import com.example.campushub.repositories.jpa.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {
    @Value("${jwt.expiration-refresh}")
    private Long jwtExpirationDate;

    private final JwtTokenProvider jwtTokenProvider;

    private final UserRepository userRepository;

    private final TokenRepository tokenRepository;

    private static final int MAX_TOKENS = 3;

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public void addToken(User user, String refreshToken, String jti) throws Exception {
        User lockedUser = userRepository.findByIdForUpdate(user.getId())
                .orElseThrow(() -> new DataNotFoundException("User not found"));

        List<Token> tokens = tokenRepository.findByUserOrderByCreatedAtAsc(lockedUser);

        while (tokens.size() >= MAX_TOKENS) {
            tokenRepository.delete(tokens.removeFirst());
        }

        tokenRepository.save(Token.builder()
                .token(refreshToken)
                .jti(jti)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtExpirationDate / 1000))
                .user(lockedUser)
                .build());
    }

    public String refreshToken(User user, String refreshToken) throws Exception {
        Token existingToken = tokenRepository.findByToken(refreshToken);
        if (existingToken == null) {
            throw new DataNotFoundException("Không tìm thấy RefreshToken");
        }

        if (existingToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(existingToken);
            throw new ExpiredTokenException("RefreshToken đã hết hạn");
        }
        return jwtTokenProvider.generateToken(user);
    }

    @Transactional("transactionManager")
    public void revokeToken(String refreshToken) {
        tokenRepository.deleteByTokenValue(refreshToken);
    }

    public Token findByToken(String refreshToken) {
        return tokenRepository.findByToken(refreshToken);
    }
}
