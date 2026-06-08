package com.example.campushub.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.record.TopicDistributionProjection;
import com.example.campushub.repositories.jpa.CommentReactionRepository;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.responses.admin.AdminStatResponse;
import com.example.campushub.responses.admin.AdminTopicDistributionResponse;
import com.example.campushub.responses.admin.AdminUserGrowthResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final PostReactionRepository postReactionRepository;
    private final CommentReactionRepository commentReactionRepository;
    private final CommentRepository commentRepository;
    private final InterestNeo4jRepository tagNeo4jRepository;

    @Transactional(value = "transactionManager", readOnly = true)
    public AdminStatResponse getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay();
        LocalDateTime nextWeekStart = weekStart.plusWeeks(1);
        LocalDateTime previousWeekStart = weekStart.minusWeeks(1);

        long totalUsers = userRepository.count();
        long weeklyUsers = userRepository.countUserBetweenStartAndEnd(weekStart, nextWeekStart);
        long previousWeeklyUsers = userRepository.countUserBetweenStartAndEnd(previousWeekStart, weekStart);

        long weeklyPosts = postRepository.countPostBetweenStartAndEnd(weekStart, nextWeekStart);
        long previousWeeklyPosts = postRepository.countPostBetweenStartAndEnd(previousWeekStart, weekStart);

        long totalReports = reportRepository.count();
        long weeklyReports = reportRepository.countReportsBetweenStartAndEnd(weekStart, nextWeekStart);
        long previousWeeklyReports = reportRepository.countReportsBetweenStartAndEnd(previousWeekStart, weekStart);
        long weeklyInteractions = postReactionRepository.countPostReactionBetweenStartAndEnd(weekStart, nextWeekStart)
                + commentReactionRepository.countCommentReactionBetweenStartAndEnd(weekStart, nextWeekStart)
                + commentRepository.countCommentBetweenStartAndEnd(weekStart, nextWeekStart);

        long previousWeeklyInteractions = postReactionRepository
                .countPostReactionBetweenStartAndEnd(previousWeekStart, weekStart)
                + commentReactionRepository.countCommentReactionBetweenStartAndEnd(previousWeekStart, weekStart)
                + commentRepository.countCommentBetweenStartAndEnd(previousWeekStart, weekStart);

        return AdminStatResponse.builder()
                .totalUsers(totalUsers)
                .weeklyUsers(weeklyUsers)
                .weeklyPosts(weeklyPosts)
                .totalReports(totalReports)
                .weeklyInteractions(weeklyInteractions)
                .userGrowthPercent(calculateGrowthPercent(weeklyUsers, previousWeeklyUsers))
                .postGrowthPercent(calculateGrowthPercent(weeklyPosts, previousWeeklyPosts))
                .interactionGrowthPercent(calculateGrowthPercent(weeklyInteractions, previousWeeklyInteractions))
                .weeklyReports(weeklyReports)
                .reportGrowthPercent(calculateGrowthPercent(weeklyReports, previousWeeklyReports))
                .build();
    }

    private double calculateGrowthPercent(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : 100.0;
        }
        return Math.round(((current - previous) * 10000.0 / previous)) / 100.0;
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public List<AdminUserGrowthResponse> getUserGrowth() {
        LocalDate today = LocalDate.now();
        int currentMonth = today.getMonthValue();
        int currentYear = today.getYear();

        List<AdminUserGrowthResponse> growth = new ArrayList<>();
        for (int month = 1; month <= currentMonth; month++) {
            YearMonth yearMonth = YearMonth.of(currentYear, month);
            LocalDateTime start = yearMonth.atDay(1).atStartOfDay();
            LocalDateTime end = month == currentMonth
                    ? today.plusDays(1).atStartOfDay()
                    : yearMonth.plusMonths(1).atDay(1).atStartOfDay();

            long value = userRepository.countUserBetweenStartAndEnd(start, end);
            growth.add(AdminUserGrowthResponse.builder()
                    .month("T" + month)
                    .value(value)
                    .build());
        }

        return growth;
    }

    @Transactional(value = "transactionManager", readOnly = true)
    public List<AdminTopicDistributionResponse> getTopicDistribution() {
        List<TopicDistributionProjection> rawDistribution = tagNeo4jRepository.findActiveTopicDistribution();
        if (rawDistribution.isEmpty()) {
            return Collections.emptyList();
        }

        List<TopicDistributionProjection> positiveDistribution = rawDistribution.stream()
                .filter(topic -> topic.value() != null && topic.value() > 0)
                .collect(Collectors.toList());

        if (positiveDistribution.isEmpty()) {
            return Collections.emptyList();
        }

        long total = positiveDistribution.stream()
                .mapToLong(TopicDistributionProjection::value)
                .sum();

        List<TopicShare> items = new ArrayList<>();
        int limit = Math.min(5, positiveDistribution.size());
        for (int index = 0; index < limit; index++) {
            TopicDistributionProjection projection = positiveDistribution.get(index);
            items.add(new TopicShare(projection.label(), projection.value()));
        }

        return calculateActualPercentages(items, total);
    }

    private List<AdminTopicDistributionResponse> calculateActualPercentages(List<TopicShare> items, long total) {
        if (total == 0) {
            return Collections.emptyList();
        }

        return items.stream()
                .map(item -> AdminTopicDistributionResponse.builder()
                        .label(item.label())
                        .value(roundToTwoDecimals(item.count() * 100.0 / total))
                        .build())
                .collect(Collectors.toList());
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private record TopicShare(String label, long count) {
    }
}
