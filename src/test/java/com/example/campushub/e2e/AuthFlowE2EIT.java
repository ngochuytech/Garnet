package com.example.campushub.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
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

import com.example.campushub.models.jpa.User;

import tools.jackson.databind.JsonNode;

/**
 * End-to-end API test: HTTP request -> security filter -> services ->
 * MySQL/Neo4j.
 *
 * Run with {@code mvn -Pe2e verify}. Docker Desktop must be running.
 */
@Testcontainers
@ActiveProfiles("e2e")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class AuthFlowE2EIT extends AbstractE2EIT {

        @Container
        static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.4"))
                        .withDatabaseName("campushub_e2e_auth")
                        .withUsername("e2e")
                        .withPassword("e2e-password");

        @Container
        static final Neo4jContainer NEO4J = new Neo4jContainer(DockerImageName.parse("neo4j:5.26.11"))
                        .withAdminPassword(NEO4J_PASSWORD);

        @DynamicPropertySource
        static void configureDatabase(DynamicPropertyRegistry registry) {
                databaseProperties(registry, MYSQL, NEO4J);
        }

        @Test
        void userCanRegisterLoginAndAccessProtectedPostsEndpoint() {
                // *Reigster
                // GIVEN
                String email = "e2e-" + UUID.randomUUID() + "@example.com";
                String password = "CorrectHorseBatteryStaple1!";

                // WHEN
                ResponseEntity<JsonNode> registration = post(
                                "/auth/register",
                                Map.of(
                                                "fullname", "E2E Test User",
                                                "email", email,
                                                "password", password,
                                                "confirmPassword", password));

                // THEN
                assertEquals(HttpStatus.CREATED, registration.getStatusCode());
                assertTrue(registration.getBody().path("success").asBoolean());

                // *Login
                User savedUser = userRepository.findByEmail(email).orElseThrow();
                assertTrue(passwordEncoder.matches(password, savedUser.getPassword()));

                ResponseEntity<JsonNode> login = post(
                                "/auth/login",
                                Map.of("email", email, "password", password));

                assertEquals(HttpStatus.OK, login.getStatusCode());
                assertTrue(login.getBody().path("success").asBoolean());

                // *Access protected endpoint
                String accessToken = login.getBody().at("/data/token").asText();
                assertFalse(accessToken.isBlank());

                ResponseEntity<JsonNode> anonymousRequest = restTemplate.getForEntity("/users/posts/me",
                                JsonNode.class);
                assertEquals(HttpStatus.UNAUTHORIZED, anonymousRequest.getStatusCode());

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(accessToken);
                ResponseEntity<JsonNode> authenticatedRequest = restTemplate.exchange(
                                "/users/posts/me",
                                HttpMethod.GET,
                                new HttpEntity<>(headers),
                                JsonNode.class);

                assertEquals(HttpStatus.OK, authenticatedRequest.getStatusCode());
                assertNotNull(authenticatedRequest.getBody());
                assertTrue(authenticatedRequest.getBody().path("success").asBoolean());
        }
}
