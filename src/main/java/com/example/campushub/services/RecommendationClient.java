package com.example.campushub.services;

import java.time.Duration;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.example.campushub.dtos.recommendation.RecommendationFeedResponse;
import com.example.campushub.exceptions.RecommendationClientException;

@Service
public class RecommendationClient {
    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String internalKey;

    public RecommendationClient(
            @Value("${recommendation.service.base-url}") String baseUrl,
            @Value("${recommendation.service.internal-key}") String internalKey,
            @Value("${recommendation.service.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${recommendation.service.read-timeout-ms:1500}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restTemplate = new RestTemplate(requestFactory);
        this.baseUrl = baseUrl;
        this.internalKey = internalKey;
    }

    public RecommendationFeedResponse getRecommendations(String userId, int size, String cursor) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/internal/recommendations/{userId}")
                .queryParam("k", size);
        if (cursor != null && !cursor.isBlank()) {
            uriBuilder.queryParam("cursor", cursor);
        }
        return fetchRecommendations(uriBuilder.buildAndExpand(userId).encode().toUri());
    }

    public RecommendationFeedResponse getTopicRecommendations(String userId, String topic, int size, String cursor) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/internal/recommendations/{userId}/topics/{topic}")
                .queryParam("k", size);
        if (cursor != null && !cursor.isBlank()) {
            uriBuilder.queryParam("cursor", cursor);
        }
        return fetchRecommendations(uriBuilder.buildAndExpand(userId, topic).encode().toUri());
    }

    public RecommendationFeedResponse getGroupRecommendations(String userId, String groupId, int size, String cursor) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder
                .fromUriString(baseUrl)
                .path("/internal/recommendations/{userId}/groups/{groupId}")
                .queryParam("k", size);
        if (cursor != null && !cursor.isBlank()) {
            uriBuilder.queryParam("cursor", cursor);
        }
        return fetchRecommendations(uriBuilder.buildAndExpand(userId, groupId).encode().toUri());
    }

    private RecommendationFeedResponse fetchRecommendations(URI uri) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("x-internal-key", internalKey);
            ResponseEntity<RecommendationFeedResponse> response = restTemplate.exchange(
                    uri,
                    HttpMethod.GET,
                    new HttpEntity<Void>(headers),
                    RecommendationFeedResponse.class);

            RecommendationFeedResponse body = response.getBody();
            if (body == null || body.items() == null) {
                throw new RecommendationClientException("Recommendation service returned an invalid response");
            }
            return body;
        } catch (RecommendationClientException e) {
            throw e;
        } catch (RestClientException | IllegalArgumentException e) {
            throw new RecommendationClientException("Failed to fetch post recommendations", e);
        }
    }
}
