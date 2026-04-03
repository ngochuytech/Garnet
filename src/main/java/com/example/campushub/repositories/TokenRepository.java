package com.example.campushub.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.campushub.models.Token;
import com.example.campushub.models.User;

public interface TokenRepository extends JpaRepository<Token, String> {
    Token findByToken(String token);
    Token findByJti(String jti);

    List<Token> findByUser(User user);
}
