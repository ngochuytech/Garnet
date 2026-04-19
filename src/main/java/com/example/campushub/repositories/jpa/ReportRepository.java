package com.example.campushub.repositories.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.campushub.enums.ReportType;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;

@Repository
public interface ReportRepository extends JpaRepository<Report, String>{
    boolean existsByReporterAndTargetTypeAndTargetId(User reporter, ReportType targetType, String targetId);
}
