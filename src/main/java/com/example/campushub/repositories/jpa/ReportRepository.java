package com.example.campushub.repositories.jpa;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;

@Repository
public interface ReportRepository extends JpaRepository<Report, String> {
    boolean existsByReporterAndTargetTypeAndTargetId(User reporter, ReportType targetType, String targetId);

    @Query("SELECT r FROM Report r " +
            "WHERE r.status = :status " +
            "AND (:type IS NULL OR r.targetType = :type)")
    Page<Report> findByStatusAndOptionalType(@Param("status") ReportStatus status,
            @Param("type") ReportType type,
            Pageable pageable);

    @Query("SELECT r FROM Report r " +
        "WHERE (:type IS NULL OR r.targetType = :type)")
    Page<Report> findByOptionalType(@Param("type") ReportType type, Pageable pageable);

    @Query("SELECT r FROM Report r " +
            "WHERE r.id LIKE CONCAT('%', :query, '%') " +
            "OR r.reportedUser.id LIKE CONCAT('%', :query, '%') " +
            "OR LOWER(r.reportedUser.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(r.reportedUser.email) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR r.reporter.id LIKE CONCAT('%', :query, '%') " +
            "OR LOWER(r.reporter.fullName) LIKE LOWER(CONCAT('%', :query, '%')) " +
            "OR LOWER(r.reporter.email) LIKE LOWER(CONCAT('%', :query, '%')) ")
    Page<Report> searchReports(@Param("query") String query, Pageable pageable);
}
