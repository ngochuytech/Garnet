package com.example.campushub.services;

import java.security.InvalidParameterException;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.neo4j.TagNode;
import com.example.campushub.repositories.neo4j.TagNeo4jRepository;
import com.example.campushub.responses.TopicResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TopicService {
    private final TagNeo4jRepository tagNeo4jRepository;
    private final FileUploadService fileUploadService;

    public List<TopicResponse> getTopicCounts(User user) {
        return tagNeo4jRepository.getTopicUserCounts(user.getId());
    }

    public TopicResponse getTopicDetails(String topicName) throws Exception {
        TopicResponse topic = tagNeo4jRepository.getTopicDetails(topicName);
        if (topic == null) {
            throw new DataNotFoundException("Chủ đề không tồn tại: " + topicName);
        }
        return topic;
    }

    public void updateTopicImage(User user, String topicName, MultipartFile image) throws Exception {
        if (image == null || image.isEmpty()) {
            throw new IllegalArgumentException("File ảnh không được để trống");
        }

        String contentType = image.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidParameterException("File phải là định dạng hình ảnh");
        }
        if (image.getSize() > 5 * 1024 * 1024) { // 5MB limit
            throw new InvalidParameterException("Kích thước ảnh tối đa 5MB");
        }
        TagNode tagNode = tagNeo4jRepository.findById(topicName).orElseThrow(() -> 
            new DataNotFoundException("Chủ đề không tồn tại: " + topicName));

        String imageUrl = fileUploadService.uploadFile(image, "topics");
        tagNeo4jRepository.updateTopicImage(topicName, imageUrl);
    }
}
