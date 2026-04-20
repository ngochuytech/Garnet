package com.example.campushub.responses;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TopicResponse {
    String topicName;
    String imageUrl;
    Long followerCount;
}
