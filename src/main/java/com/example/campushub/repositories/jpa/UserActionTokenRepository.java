package com.example.campushub.repositories.jpa;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.enums.UserActionTokenPurpose;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserActionToken;

public interface UserActionTokenRepository extends JpaRepository<UserActionToken, String> {
    Optional<UserActionToken> findByTokenHashAndPurpose(String tokenHash, UserActionTokenPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM UserActionToken token "
            + "WHERE token.tokenHash = :tokenHash AND token.purpose = :purpose")
    Optional<UserActionToken> findByTokenHashAndPurposeForUpdate(
            @Param("tokenHash") String tokenHash,
            @Param("purpose") UserActionTokenPurpose purpose);

    void deleteByUserAndPurposeAndConsumedAtIsNull(User user, UserActionTokenPurpose purpose);
}
