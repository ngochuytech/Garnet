package com.example.campushub.scheduled;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.campushub.enums.Neo4jEventStatus;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.services.Neo4jSyncService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class Neo4jSyncScheduler {
    private final Neo4jSyncEventRepository eventRepository;
    private final Neo4jSyncService neo4jSyncService;

    @Scheduled(fixedRate = 5000)
    public void syncPendingEvents(){
        List<Neo4jSyncEvent> events = eventRepository.findTop50ByStatusOrderByCreatedAtAsc(Neo4jEventStatus.PENDING);

        for(Neo4jSyncEvent event : events){
            if(neo4jSyncService.claim(event.getId())){
                neo4jSyncService.process(event);
            }
        }
    }
}
