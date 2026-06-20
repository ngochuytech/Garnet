package com.example.campushub.repositories.jpa;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.campushub.enums.UserActionTokenPurpose;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserActionToken;

public interface UserActionTokenRepository extends JpaRepository<UserActionToken, String> {
    Optional<UserActionToken> findByTokenHashAndPurpose(String tokenHash, UserActionTokenPurpose purpose);

    void deleteByUserAndPurposeAndConsumedAtIsNull(User user, UserActionTokenPurpose purpose);
}
