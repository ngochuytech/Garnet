package com.example.campushub.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.campushub.enums.UserStatus;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.neo4j.UserNode;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;

import tools.jackson.databind.JsonNode;

/** Verifies the real HTTP -> JWT -> service -> Neo4j follow lifecycle. */
@Testcontainers
@ActiveProfiles("e2e")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class FollowFlowE2EIT extends AbstractE2EIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.4"))
            .withDatabaseName("campushub_e2e_follow")
            .withUsername("e2e")
            .withPassword("e2e-password");

    @Container
    static final Neo4jContainer NEO4J = new Neo4jContainer(DockerImageName.parse("neo4j:5.26.11"))
            .withAdminPassword(NEO4J_PASSWORD);

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        databaseProperties(registry, MYSQL, NEO4J);
    }

    @Autowired
    private UserNeo4jRepository userNeo4jRepository;

    @Test
    void userCanFollowThenUnfollowAnotherUser() {
        String password = "CorrectHorseBatteryStaple1!";
        User follower = registerUser("Follower", password);
        User target = registerUser("Target", password);
        createNeo4jUser(follower);
        createNeo4jUser(target);
        String accessToken = login(follower.getEmail(), password);

        ResponseEntity<JsonNode> followResponse = exchange(
                "/users/" + target.getId() + "/follow", HttpMethod.POST, accessToken);

        assertEquals(HttpStatus.OK, followResponse.getStatusCode());
        assertTrue(followResponse.getBody().path("success").asBoolean());
        assertTrue(userNeo4jRepository.isFollowing(follower.getId(), target.getId()));

        ResponseEntity<JsonNode> unfollowResponse = exchange(
                "/users/" + target.getId() + "/unfollow", HttpMethod.POST, accessToken);

        assertEquals(HttpStatus.OK, unfollowResponse.getStatusCode());
        assertTrue(unfollowResponse.getBody().path("success").asBoolean());
        assertFalse(userNeo4jRepository.isFollowing(follower.getId(), target.getId()));
    }

    @Test
    void userCannotFollowBannedOrInactiveAccountEvenWhenTheirNeo4jNodeExists() {
        String password = "CorrectHorseBatteryStaple1!";
        User follower = registerUser("Follower", password);
        User bannedTarget = registerUser("Banned Target", password);
        User inactiveTarget = registerUser("Inactive Target", password);
        createNeo4jUser(follower);
        createNeo4jUser(bannedTarget);
        createNeo4jUser(inactiveTarget);
        bannedTarget.setStatus(UserStatus.BANNED);
        inactiveTarget.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(bannedTarget);
        userRepository.saveAndFlush(inactiveTarget);
        String accessToken = login(follower.getEmail(), password);

        ResponseEntity<JsonNode> bannedResponse = exchange(
                "/users/" + bannedTarget.getId() + "/follow", HttpMethod.POST, accessToken);
        ResponseEntity<JsonNode> inactiveResponse = exchange(
                "/users/" + inactiveTarget.getId() + "/follow", HttpMethod.POST, accessToken);

        assertEquals(HttpStatus.BAD_REQUEST, bannedResponse.getStatusCode());
        assertEquals(HttpStatus.BAD_REQUEST, inactiveResponse.getStatusCode());
        assertFalse(userNeo4jRepository.isFollowing(follower.getId(), bannedTarget.getId()));
        assertFalse(userNeo4jRepository.isFollowing(follower.getId(), inactiveTarget.getId()));
    }

    private User registerUser(String fullName, String password) {
        String email = "e2e-follow-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> registration = post("/auth/register", Map.of(
                "fullname", fullName,
                "email", email,
                "password", password,
                "confirmPassword", password));
        assertEquals(HttpStatus.CREATED, registration.getStatusCode());
        return userRepository.findByEmail(email).orElseThrow();
    }

    private String login(String email, String password) {
        ResponseEntity<JsonNode> response = post("/auth/login", Map.of(
                "email", email,
                "password", password));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response.getBody().at("/data/token").asText();
    }

    private void createNeo4jUser(User user) {
        UserNode userNode = new UserNode();
        userNode.setId(user.getId());
        userNeo4jRepository.save(userNode);
    }

    private ResponseEntity<JsonNode> exchange(String path, HttpMethod method, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(path, method, new HttpEntity<>(headers), JsonNode.class);
    }
}
