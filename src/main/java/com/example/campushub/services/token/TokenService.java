package com.example.campushub.services.token;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.example.campushub.components.JwtTokenProvider;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.auth.ExpiredTokenException;
import com.example.campushub.models.Token;
import com.example.campushub.models.User;
import com.example.campushub.repositories.TokenRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenService {
    @Value("${jwt.expiration-refresh}")
    private Long jwtExpirationDate;

    private final JwtTokenProvider jwtTokenProvider;

    private final TokenRepository tokenRepository;

    private static final int MAX_TOKENS = 3;

    public void addToken(User user, String refreshToken, String jti) {
        List<Token> userTokens = tokenRepository.findByUser(user);
        int tokenCount = userTokens.size();
        if (tokenCount >= MAX_TOKENS) {
            tokenRepository.delete(userTokens.getFirst());
        }

        LocalDateTime expireAt = LocalDateTime.now().plusSeconds(jwtExpirationDate / 1000);
        Token newToken = Token.builder()
                .token(refreshToken)
                .jti(jti)
                .expiresAt(expireAt)
                .user(user)
                .build();
        tokenRepository.save(newToken);
    }

    public String refreshToken(User user, String refreshToken) throws Exception{
        Token existingToken = tokenRepository.findByToken(refreshToken);
        if(existingToken == null){
            throw new DataNotFoundException("Không tìm thấy RefreshToken");
        }

        if(existingToken.getExpiresAt().isBefore(LocalDateTime.now())){
            tokenRepository.delete(existingToken);
            throw new ExpiredTokenException("RefreshToken đã hết hạn");
        }
        return jwtTokenProvider.generateToken(user);
    }
    
    public void revokeToken(String refreshToken) {
        Token token = tokenRepository.findByToken(refreshToken);
        if (token != null) {
            tokenRepository.delete(token);
        }
    }

    public Token findByToken(String refreshToken) {
        return tokenRepository.findByToken(refreshToken);
    }
}
