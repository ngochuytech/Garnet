package com.example.campushub.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.exceptions.UnauthorizedException;
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
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

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
            throw new ResourceNotFoundException("Không tìm thấy RefreshToken");
        }

        if (existingToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(existingToken);
            throw new UnauthorizedException("RefreshToken đã hết hạn");
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
