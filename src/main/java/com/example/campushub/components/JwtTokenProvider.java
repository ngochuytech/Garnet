package com.example.campushub.components;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.example.campushub.enums.UserStatus;
import com.example.campushub.exceptions.auth.ExpiredTokenException;
import com.example.campushub.exceptions.auth.InvalidTokenException;
import com.example.campushub.exceptions.auth.JwtAuthenticationException;
import com.example.campushub.exceptions.auth.RevokedTokenException;
import com.example.campushub.exceptions.auth.UnauthorizedException;
import com.example.campushub.models.Token;
import com.example.campushub.models.User;
import com.example.campushub.repositories.TokenRepository;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {
    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private Long jwtExpirationDate;

    @Value("${jwt.expiration-refresh}")
    private Long jwtRefreshExpirationDate;

    private final TokenRepository tokenRepository;

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();

        Date currentDate = new Date();

        Date expireDate = new Date(currentDate.getTime() + jwtExpirationDate);

        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(expireDate)
                .signWith(key())
                .compact();
    }

    public String generateToken(User user) {
        String username = user.getEmail();

        Date currentDate = new Date();

        Date expireDate = new Date(currentDate.getTime() + jwtExpirationDate);

        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(expireDate)
                .signWith(key())
                .compact();
    }

    // Generate refresh token with a unique jti claim
    public String generateRefreshToken(User user) {
        String jti = UUID.randomUUID().toString();

        Date currentDate = new Date();

        Date expireDate = new Date(currentDate.getTime() + jwtRefreshExpirationDate);

        return Jwts.builder()
                .id(jti)
                .subject(user.getEmail())
                .claim("type", "refresh")
                .issuedAt(currentDate)
                .expiration(expireDate)
                .signWith(key())
                .compact();
    }

    private SecretKey key() {
        byte[] bytes = Decoders.BASE64.decode(jwtSecret);
        return Keys.hmacShaKeyFor(bytes);
    }

    public String getUsername(String token) {
        return Jwts.parser()
                .verifyWith((SecretKey) key())
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }

    public boolean validateToken(String token, User user) {
        try {
            Claims claims = this.extractAllClaims(token);
            String type = claims.get("type", String.class);

            // If it's a refresh token, validate against DB using jti
            if ("refresh".equals(type)) {
                return validateRefreshToken(token, user);
            }

            // Otherwise treat as stateless access token: check signature, expiration and subject
            String subject = claims.getSubject();
            if (user.getStatus().equals(UserStatus.INACTIVE)) {
                throw new UnauthorizedException("Tài khoản đã bị vô hiệu hóa");
            }
            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) {
                throw new ExpiredTokenException("Token đã hết hạn. Vui lòng đăng nhập lại");
            }
            return subject.equals(user.getUsername());
        } catch (ExpiredJwtException e) {
            throw new ExpiredTokenException("Token đã hết hạn. Vui lòng đăng nhập lại");
        } catch (JwtException e) {
            throw new InvalidTokenException("Token không hợp lệ");
        }
    }

    // Save refresh token into DB (store token string, jti, expiry and owner)
    public Token saveRefreshToken(String refreshToken, User user) {
        Claims claims = extractAllClaims(refreshToken);
        String jti = claims.getId();
        Date exp = claims.getExpiration();
        if (jti == null) {
            throw new InvalidTokenException("Refresh token missing jti");
        }
        Token tokenEntity = Token.builder()
                .token(refreshToken)
                .jti(jti)
                .expiresAt(LocalDateTime.ofInstant(exp.toInstant(), ZoneId.systemDefault()))
                .isRevoked(false)
                .user(user)
                .build();
        return tokenRepository.save(tokenEntity);
    }

    public boolean validateRefreshToken(String refreshToken, User user) {
        try {
            Claims claims = extractAllClaims(refreshToken);
            String jti = claims.getId();
            if (jti == null) {
                throw new InvalidTokenException("Refresh token thiếu jti");
            }

            Token existingToken = tokenRepository.findByJti(jti);
            if (existingToken == null) {
                throw new InvalidTokenException("Refresh token không tồn tại trong hệ thống");
            }
            if (existingToken.isRevoked()) {
                throw new RevokedTokenException("Refresh token đã bị thu hồi. Vui lòng đăng nhập lại");
            }
            if (!existingToken.getUser().getId().equals(user.getId())) {
                throw new UnauthorizedException("Refresh token không thuộc về user này");
            }
            if (existingToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                throw new ExpiredTokenException("Refresh token đã hết hạn. Vui lòng đăng nhập lại");
            }
            return true;
        } catch (ExpiredJwtException e) {
            throw new ExpiredTokenException("Refresh token đã hết hạn. Vui lòng đăng nhập lại");
        } catch (JwtException e) {
            throw new InvalidTokenException("Refresh token không hợp lệ");
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();

    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = this.extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String getJtiFromToken(String token) {
        try {
            return extractClaim(token, Claims::getId);
        } catch (JwtException e) {
            throw new InvalidTokenException("Không thể lấy jti từ token");
        }
    }

    public boolean isTokenExpired(String token) {
        try {
            Date expirationDate = this.extractClaim(token, Claims::getExpiration);
            return expirationDate.before(new Date());
        } catch (ExpiredJwtException e) {
            return true; // Token đã hết hạn
        }
    }

    public User getUserFromToken(String token) {
        User user = tokenRepository.findByToken(token).getUser();
        return user;
    }

    public String getEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthorizedException("Không tìm thấy thông tin người dùng. Vui lòng đăng nhập");
        }

        Object principal = authentication.getPrincipal();

        // Kiểm tra nếu là anonymous user
        if (principal instanceof String && "anonymousUser".equals(principal)) {
            throw new UnauthorizedException("Bạn chưa đăng nhập. Vui lòng đăng nhập để tiếp tục");
        }

        if (principal instanceof User) {
            return ((User) principal).getEmail();
        } else if (principal instanceof String) {
            return (String) principal;
        } else {
            return authentication.getName();
        }
    }

    public User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new UnauthorizedException("Không tìm thấy thông tin người dùng. Vui lòng đăng nhập");
        }

        Object principal = authentication.getPrincipal();

        // Kiểm tra nếu là anonymous user
        if (principal instanceof String && "anonymousUser".equals(principal)) {
            throw new UnauthorizedException("Bạn chưa đăng nhập. Vui lòng đăng nhập để tiếp tục");
        }

        if (principal instanceof User) {
            return (User) principal;
        } else {
            throw new JwtAuthenticationException("Principal không phải là User object. Principal type: " +
                    principal.getClass().getName() + ", value: " + principal);
        }
    }

    public String getCurrentUserId() {
        return getCurrentUser().getId();
    }
}
