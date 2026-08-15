package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.campushub.models.jpa.UserActivityEvents;

public interface UserActivityEventsRepository extends JpaRepository<UserActivityEvents, Long> {
}
