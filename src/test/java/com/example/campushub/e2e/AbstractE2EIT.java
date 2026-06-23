package com.example.campushub.e2e;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;

import com.example.campushub.repositories.jpa.UserRepository;

import tools.jackson.databind.JsonNode;

/**
 * Shared HTTP client and dynamic database-property helper for Failsafe E2E tests.
 * Each concrete test owns its containers so Spring never reuses a stopped database URL.
 * Run with {@code mvn -Pe2e verify} while Docker Desktop is running.
 */
abstract class AbstractE2EIT {

    protected static final String NEO4J_PASSWORD = "neo4j123";

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    protected static void databaseProperties(
            DynamicPropertyRegistry registry,
            MySQLContainer mysql,
            Neo4jContainer neo4j) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");

        registry.add("spring.neo4j.uri", neo4j::getBoltUrl);
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> NEO4J_PASSWORD);
    }

    protected ResponseEntity<JsonNode> post(String path, Map<String, String> body) {
        return restTemplate.postForEntity(path, body, JsonNode.class);
    }
}
