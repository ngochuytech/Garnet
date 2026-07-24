package com.example.campushub.components;

import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import com.example.campushub.enums.UserStatus;
import com.example.campushub.exceptions.UnauthorizedException;
import com.example.campushub.models.jpa.User;

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

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();

        Date currentDate = new Date();

        Date expireDate = new Date(currentDate.getTime() + jwtExpirationDate);

        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(expireDate)
                .claim("type", "access")
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
                .claim("type", "access")
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

    public boolean validateAccessToken(String token, User user) {
        try {
            Claims claims = extractAllClaims(token);
            String type = claims.get("type", String.class);

            if ("refresh".equals(type)) {
                throw new UnauthorizedException("Refresh token cannot be used as access token");
            }

            if (type != null && !"access".equals(type)){
                throw new UnauthorizedException("Invalid token type");
            }

            String subject = claims.getSubject();
            if(user.getStatus().equals(UserStatus.INACTIVE)){
                throw new UnauthorizedException("This account is disabled");
            } else if(user.getStatus().equals(UserStatus.BANNED)){
                throw new UnauthorizedException("This account is banned");
            }

            Date expiration = claims.getExpiration();
            if (expiration == null || expiration.before(new Date())) {
                throw new UnauthorizedException("Token is expired! Try login again!");
            }

            return subject.equals(user.getUsername());
        } catch (ExpiredJwtException e) {
            throw new UnauthorizedException("Token is expired! Try login again");
        } catch (JwtException e){
            throw new UnauthorizedException("Invalid Token");
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
            throw new UnauthorizedException("Không thể lấy jti từ token");
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
}
