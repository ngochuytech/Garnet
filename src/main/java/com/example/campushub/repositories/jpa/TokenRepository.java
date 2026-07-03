package com.example.campushub.repositories.jpa;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.jpa.Token;
import com.example.campushub.models.jpa.User;

public interface TokenRepository extends JpaRepository<Token, String> {
    Token findByToken(String token);

    Token findByJti(String jti);

    List<Token> findByUser(User user);

    List<Token> findByUserOrderByCreatedAtAsc(User user);

    @Modifying
    @Query("DELETE FROM Token t WHERE t.token = :token")
    int deleteByTokenValue(@Param("token") String token);
}
