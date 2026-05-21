package com.example.campushub.repositories.jpa;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

        Page<Report> findByReportedUser(User reportedUser, Pageable pageable);

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

        Optional<Report> findByTargetIdAndTargetType(String targetId, ReportType type);

        @EntityGraph(attributePaths = {"reporter", "reportedUser", "resolvedBy"})
        List<Report> findAllByTargetIdAndTargetType(String targetId, ReportType type);

        @EntityGraph(attributePaths = {"reporter", "reportedUser", "resolvedBy"})
        List<Report> findAllByTargetIdAndTargetTypeOrderByCreatedAtDesc(String targetId, ReportType type);

        @Modifying
        @Query("UPDATE Report r SET r.status = :status, r.resolvedBy = :admin, " +
                        "r.adminNote = COALESCE(r.adminNote, :adminNote) " + // COALESCE: Nếu adminNote cũ là null thì
                                                                             // lấy cái mới,
                                                                             // không thì giữ nguyên
                        "WHERE r.targetId = :targetId AND r.targetType = :targetType")
        void updateExistingReportsStatus(
                        @Param("targetId") String targetId,
                        @Param("targetType") ReportType targetType,
                        @Param("status") ReportStatus status,
                        @Param("admin") User admin,
                        @Param("adminNote") String adminNote);

        @Query("SELECT COUNT(r) FROM Report r WHERE r.createdAt >= :start AND r.createdAt < :end")
        long countReportsBetweenStartAndEnd(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

        Page<Report> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end, Pageable pageable);
}
