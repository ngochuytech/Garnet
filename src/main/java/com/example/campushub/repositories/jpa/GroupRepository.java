package com.example.campushub.repositories.jpa;

import com.example.campushub.models.jpa.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface GroupRepository extends JpaRepository<Group, String> {
}