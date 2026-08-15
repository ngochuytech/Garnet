package com.example.campushub.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.activity.ImpressionEventRequest;
import com.example.campushub.models.jpa.UserActivityEvents;
import com.example.campushub.repositories.jpa.UserActivityEventsRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivityService {
    private final UserActivityEventsRepository userActivityEventsRepository;

    @Transactional("transactionManager")
    public int recordPostImpressions(String actorUserId, List<ImpressionEventRequest> events) {
        List<UserActivityEvents> activityEvents = events.stream()
                .map(event -> UserActivityEvents.builder()
                        .actorUserId(actorUserId)
                        .eventType(event.getEventType())
                        .targetType(event.getTargetType())
                        .targetId(event.getTargetId())
                        .build())
                .toList();

        userActivityEventsRepository.saveAll(activityEvents);
        return activityEvents.size();
    }
}
