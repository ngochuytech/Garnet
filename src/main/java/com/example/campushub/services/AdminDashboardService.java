package com.example.campushub.services;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.record.TopicDistributionDTO;
import com.example.campushub.repositories.jpa.CommentReactionRepository;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.PostReactionRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;
import com.example.campushub.repositories.neo4j.TopicDistributionProjection;
import com.example.campushub.repositories.neo4j.TagNeo4jRepository;
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
    private final PostNeo4jRepository postNeo4jRepository;
    private final TagNeo4jRepository tagNeo4jRepository;

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
        List<TopicDistributionDTO> rawDistribution = tagNeo4jRepository.findActiveTopicDistribution();
        if (rawDistribution.isEmpty()) {
            return Collections.emptyList();
        }

        long total = rawDistribution.stream()
                .map(TopicDistributionDTO::getValue)
                .filter(value -> value != null)
                .mapToLong(Long::longValue)
                .sum();

        if (total == 0) {
            return Collections.emptyList();
        }

        List<TopicShare> items = new ArrayList<>();
        int limit = Math.min(4, rawDistribution.size());
        for (int index = 0; index < limit; index++) {
            TopicDistributionDTO projection = rawDistribution.get(index);
            items.add(new TopicShare(projection.getLabel(), projection.getValue() == null ? 0L : projection.getValue()));
        }

        if (rawDistribution.size() > 4) {
            long othersCount = rawDistribution.subList(4, rawDistribution.size()).stream()
                    .map(TopicDistributionDTO::getValue)
                    .filter(value -> value != null)
                    .mapToLong(Long::longValue)
                    .sum();
            items.add(new TopicShare("Khác", othersCount));
        }

        return allocatePercentages(items);
    }

    private List<AdminTopicDistributionResponse> allocatePercentages(List<TopicShare> items) {
        long total = items.stream().mapToLong(TopicShare::count).sum();
        if (total == 0) {
            return Collections.emptyList();
        }

        List<AllocatedTopicShare> allocated = items.stream()
                .map(item -> {
                    double exact = item.count() * 100.0 / total;
                    long floor = (long) Math.floor(exact);
                    double fraction = exact - floor;
                    return new AllocatedTopicShare(item.label(), item.count(), floor, fraction);
                })
                .collect(Collectors.toCollection(ArrayList::new));

        long assigned = allocated.stream().mapToLong(AllocatedTopicShare::percent).sum();
        long remaining = 100 - assigned;

        allocated.sort(Comparator.comparingDouble(AllocatedTopicShare::fraction).reversed()
                .thenComparingLong(AllocatedTopicShare::count).reversed());

        for (int index = 0; index < remaining && index < allocated.size(); index++) {
            allocated.get(index).addOne();
        }

        return allocated.stream()
                .map(item -> AdminTopicDistributionResponse.builder()
                        .label(item.label())
                        .value(item.percent())
                        .build())
                .collect(Collectors.toList());
    }

    private record TopicShare(String label, long count) {
    }

    private static final class AllocatedTopicShare {
        private final String label;
        private final long count;
        private long percent;
        private final double fraction;

        private AllocatedTopicShare(String label, long count, long percent, double fraction) {
            this.label = label;
            this.count = count;
            this.percent = percent;
            this.fraction = fraction;
        }

        private String label() {
            return label;
        }

        private long count() {
            return count;
        }

        private long percent() {
            return percent;
        }

        private double fraction() {
            return fraction;
        }

        private void addOne() {
            this.percent++;
        }
    }
}
