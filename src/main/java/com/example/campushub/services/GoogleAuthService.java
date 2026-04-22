package com.example.campushub.services;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.example.campushub.dtos.GoogleCodeRequest;
import com.example.campushub.enums.UserRole;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class GoogleAuthService {
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String redirectUri;

    private String scope = "openid email profile";

    private final UserRepository userRepository;

    public String generateAuthUrl(){
        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=" + clientId +
                "&redirect_uri=" + URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) +
                "&response_type=code" +
                "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);
    }

    public User loginWithGoogle(GoogleCodeRequest request) throws Exception{
        RestTemplate restTemplate = new RestTemplate();

        // Gửi code sang Google để đổi lấy token
        String tokenEndpoint = "https://oauth2.googleapis.com/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("code", request.getCode());
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("redirect_uri", request.getRedirectUri());
        body.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> requestEntity = new HttpEntity<>(body, headers);

        JsonNode tokenResponse = restTemplate.postForObject(tokenEndpoint, requestEntity, JsonNode.class);

        if (tokenResponse == null || !tokenResponse.has("id_token")) {
            throw new IllegalArgumentException("Invalid token response from Google");
        }

        String idToken = tokenResponse.get("id_token").asText();

        // 2. Gọi Google API để lấy thông tin user từ access_token
        String userInfoEndpoint = "https://www.googleapis.com/oauth2/v3/userinfo";
        String accessToken = tokenResponse.get("access_token").asText();

        JsonNode userInfo = restTemplate.getForObject(userInfoEndpoint + "?access_token=" + accessToken, JsonNode.class);

        if (userInfo == null || !userInfo.has("email")) {
            throw new IllegalArgumentException("Cannot fetch user info from Google");
        }

        String googleId = userInfo.get("sub").asText();
        String email = userInfo.get("email").asText();
        String name = userInfo.get("name").asText();
        String picture = userInfo.get("picture").asText();

        // 3. Lưu hoặc cập nhật vào DB
        User user = userRepository.findByEmail(email).orElse(
                User.builder()
                        .googleId(googleId)
                        .email(email)
                        .fullName(name)
                        .avatarUrl(picture)
                        .role(UserRole.USER) // User
                        .build()
        );

        // Nếu user đã tồn tại thì update info
        user.setGoogleId(googleId);
        if(user.getAvatarUrl() == null)
            user.setAvatarUrl(picture);
        return userRepository.save(user);
    }
}
