package com.example.campushub.components;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AiGraphUpdateScheduler {

    // Chạy 5 phút 1 lần (ms)
    @Scheduled(fixedRate = 300000)
    public void updateAiGraph() {
        try {
            RestTemplate restTemplate = new RestTemplate();
            String aiUrl = "http://localhost:8000/reload?wait=false"; 
            
            log.info("Bắt đầu gọi API cập nhật Graph phía AI: {}", aiUrl);
            String response = restTemplate.postForObject(aiUrl, null, String.class);
            log.info("Phản hồi từ AI service: {}", response);
            
        } catch (Exception e) {
            log.error("Lỗi khi gọi API cập nhật AI Graph: {}", e.getMessage());
        }
    }
}
    