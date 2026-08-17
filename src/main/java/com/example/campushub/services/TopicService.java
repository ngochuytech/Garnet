package com.example.campushub.services;

import java.util.List;

import org.springframework.stereotype.Service;

import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.neo4j.InterestNode;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.responses.TopicResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TopicService {
    private final InterestNeo4jRepository tagNeo4jRepository;
    public List<TopicResponse> getTopicCounts(User user) {
        return tagNeo4jRepository.getTopicUserCounts(user.getId());
    }

    public List<TopicResponse> getAllTopics(User user) {
        return tagNeo4jRepository.findLeafTags();
    }

    public TopicResponse getTopicDetails(String topicName) throws Exception {
        TopicResponse topic = tagNeo4jRepository.getTopicDetails(topicName);
        if (topic == null) {
            throw new ResourceNotFoundException("Chủ đề không tồn tại: " + topicName);
        }
        return topic;
    }

    public void updateTopicImage(User user, String topicName, String imageUrl) throws Exception {
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new IllegalArgumentException("Đường dẫn ảnh không được để trống");
        }

        InterestNode tagNode = tagNeo4jRepository.findById(topicName).orElseThrow(() -> 
            new ResourceNotFoundException("Chủ đề không tồn tại: " + topicName));

        tagNeo4jRepository.updateTopicImage(topicName, imageUrl);
    }
}
