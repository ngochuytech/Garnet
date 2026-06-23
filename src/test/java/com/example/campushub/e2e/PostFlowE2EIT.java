package com.example.campushub.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.enums.UserRole;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.ReportRepository;
import com.example.campushub.repositories.neo4j.PostNeo4jRepository;

import tools.jackson.databind.JsonNode;

/** Verifies that the public create-post API persists the same post in MySQL and Neo4j. */
@Testcontainers
@ActiveProfiles("e2e")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class PostFlowE2EIT extends AbstractE2EIT {

    private static final String PASSWORD = "CorrectHorseBatteryStaple1!";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.4"))
            .withDatabaseName("campushub_e2e_post")
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
    private PostRepository postRepository;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @MockitoSpyBean
    private PostNeo4jRepository postNeo4jRepository;

    @Test
    void createPostSynchronizesMysqlAndNeo4j() {
        User author = registerUser("Post Author");
        String accessToken = login(author.getEmail());
        String tag = "E2E Post Tag " + UUID.randomUUID();
        String content = "Post persisted in both databases";
        seedGraphDependencies(author.getId(), tag);

        ResponseEntity<JsonNode> response = createPost(accessToken, content, tag);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().path("success").asBoolean());

        Post mysqlPost = postRepository.findByUser(author).stream().findFirst().orElseThrow();
        assertEquals(content, mysqlPost.getContent());
        assertEquals(author.getId(), mysqlPost.getUser().getId());
        assertEquals(ContentStatus.ACTIVE, mysqlPost.getStatus());

        assertEquals(1L, countPostGraph(author.getId(), mysqlPost.getId(), tag));
    }

    @Test
    void failedNeo4jGroupLinkLeavesNoOrphanPostInEitherDatabase() {
        User author = registerUser("Post Rollback Author");
        String accessToken = login(author.getEmail());
        String tag = "E2E Rollback Tag " + UUID.randomUUID();
        Group group = createApprovedGroupMembership(author);
        seedGraphDependencies(author.getId(), tag);

        doThrow(new RuntimeException("Simulated Neo4j group-link failure"))
                .when(postNeo4jRepository)
                .linkPostToGroup(anyString(), eq(group.getId()));

        try {
            ResponseEntity<JsonNode> response = createPost(
                    accessToken, "Post that must roll back", tag, group.getId());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertFalse(response.getBody().path("success").asBoolean());
            assertTrue(postRepository.findByUser(author).isEmpty(),
                    "MySQL must roll back the post when Neo4j synchronization fails");
            assertEquals(0L, countPostsCreatedBy(author.getId()),
                    "Neo4j must not retain a post when the create-post flow fails");
        } finally {
            reset(postNeo4jRepository);
        }
    }

    @Test
    void sharePostSynchronizesMysqlAndNeo4j() {
        User author = registerUser("Original Post Author");
        User sharer = registerUser("Post Sharer");
        String authorToken = login(author.getEmail());
        String sharerToken = login(sharer.getEmail());
        String tag = "E2E Share Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        seedGraphDependencies(sharer.getId(), tag);

        assertEquals(HttpStatus.CREATED, createPost(authorToken, "Original post", tag).getStatusCode());
        Post originalPost = postRepository.findByUser(author).stream().findFirst().orElseThrow();

        ResponseEntity<JsonNode> response = sharePost(sharerToken, originalPost.getId(), "Shared post", tag);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertTrue(response.getBody().path("success").asBoolean());
        Post mysqlSharedPost = postRepository.findByUser(sharer).stream().findFirst().orElseThrow();
        assertEquals("Shared post", mysqlSharedPost.getContent());
        assertEquals(originalPost.getId(), mysqlSharedPost.getSharedPost().getId());
        assertEquals(1L, countSharedPostGraph(sharer.getId(), mysqlSharedPost.getId(), originalPost.getId(), tag));
    }

    @Test
    void failedNeo4jShareLeavesNoOrphanSharedPostInEitherDatabase() {
        User author = registerUser("Original Rollback Author");
        User sharer = registerUser("Rollback Post Sharer");
        String authorToken = login(author.getEmail());
        String sharerToken = login(sharer.getEmail());
        String tag = "E2E Share Rollback Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        seedGraphDependencies(sharer.getId(), tag);

        assertEquals(HttpStatus.CREATED, createPost(authorToken, "Original post", tag).getStatusCode());
        Post originalPost = postRepository.findByUser(author).stream().findFirst().orElseThrow();
        doThrow(new RuntimeException("Simulated Neo4j share failure"))
                .when(postNeo4jRepository)
                .createSharedPost(eq(sharer.getId()), anyString(), eq(originalPost.getId()), any(Set.class), any());

        try {
            ResponseEntity<JsonNode> response = sharePost(sharerToken, originalPost.getId(), "Must roll back", tag);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertFalse(response.getBody().path("success").asBoolean());
            assertTrue(postRepository.findByUser(sharer).isEmpty(),
                    "MySQL must roll back the shared post when Neo4j synchronization fails");
            assertEquals(0L, countPostsCreatedBy(sharer.getId()),
                    "Neo4j must not retain a shared post when the share flow fails");
        } finally {
            reset(postNeo4jRepository);
        }
    }

    @Test
    void deletePostSynchronizesDeletedStatusAcrossMysqlAndNeo4j() {
        User author = registerUser("Delete Post Author");
        String accessToken = login(author.getEmail());
        String tag = "E2E Delete Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        assertEquals(HttpStatus.CREATED, createPost(accessToken, "Post to delete", tag).getStatusCode());
        Post post = postRepository.findByUser(author).stream().findFirst().orElseThrow();

        ResponseEntity<JsonNode> response = deletePost(accessToken, post.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ContentStatus.DELETED, postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertEquals(ContentStatus.DELETED.name(), graphPostStatus(post.getId()));
    }

    @Test
    void adminRestorePostSynchronizesActiveStatusAcrossMysqlAndNeo4j() {
        User author = registerUser("Restore Post Author");
        String authorToken = login(author.getEmail());
        String tag = "E2E Restore Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        assertEquals(HttpStatus.CREATED, createPost(authorToken, "Post to restore", tag).getStatusCode());
        Post post = postRepository.findByUser(author).stream().findFirst().orElseThrow();
        assertEquals(HttpStatus.OK, deletePost(authorToken, post.getId()).getStatusCode());

        author.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(author);
        String adminToken = login(author.getEmail());
        ResponseEntity<JsonNode> response = restorePost(adminToken, post.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ContentStatus.ACTIVE, postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertEquals(ContentStatus.ACTIVE.name(), graphPostStatus(post.getId()));
    }

    @Test
    void failedNeo4jDeleteKeepsPostActiveInBothDatabases() {
        User author = registerUser("Delete Rollback Author");
        String accessToken = login(author.getEmail());
        String tag = "E2E Delete Rollback Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        assertEquals(HttpStatus.CREATED, createPost(accessToken, "Post that must remain active", tag).getStatusCode());
        Post post = postRepository.findByUser(author).stream().findFirst().orElseThrow();
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new RuntimeException("Simulated Neo4j delete failure after graph update");
        })
                .when(postNeo4jRepository)
                .updatePostStatus(post.getId(), ContentStatus.DELETED.name());

        try {
            ResponseEntity<JsonNode> response = deletePost(accessToken, post.getId());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(ContentStatus.ACTIVE, postRepository.findById(post.getId()).orElseThrow().getStatus());
            assertEquals(ContentStatus.ACTIVE.name(), graphPostStatus(post.getId()));
        } finally {
            reset(postNeo4jRepository);
        }
    }

    @Test
    void failedNeo4jRestoreKeepsPostDeletedInBothDatabases() {
        User author = registerUser("Restore Rollback Author");
        String authorToken = login(author.getEmail());
        String tag = "E2E Restore Rollback Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        assertEquals(HttpStatus.CREATED, createPost(authorToken, "Post that must remain deleted", tag).getStatusCode());
        Post post = postRepository.findByUser(author).stream().findFirst().orElseThrow();
        assertEquals(HttpStatus.OK, deletePost(authorToken, post.getId()).getStatusCode());

        author.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(author);
        String adminToken = login(author.getEmail());
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new RuntimeException("Simulated Neo4j restore failure after graph update");
        })
                .when(postNeo4jRepository)
                .updatePostStatus(post.getId(), ContentStatus.ACTIVE.name());

        try {
            ResponseEntity<JsonNode> response = restorePost(adminToken, post.getId());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(ContentStatus.DELETED, postRepository.findById(post.getId()).orElseThrow().getStatus());
            assertEquals(ContentStatus.DELETED.name(), graphPostStatus(post.getId()));
        } finally {
            reset(postNeo4jRepository);
        }
    }

    @Test
    void resolvingUserReportMarksPostReportedAcrossMysqlAndNeo4j() {
        User author = registerUser("Reported Post Author");
        User reporter = registerUser("Post Reporter");
        User admin = registerUser("Report Admin");
        String authorToken = login(author.getEmail());
        String reporterToken = login(reporter.getEmail());
        String tag = "E2E Report Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        assertEquals(HttpStatus.CREATED, createPost(authorToken, "Reported post", tag).getStatusCode());
        Post post = postRepository.findByUser(author).stream().findFirst().orElseThrow();
        assertEquals(HttpStatus.CREATED, createPostReport(reporterToken, post.getId()).getStatusCode());
        Report report = openPostReport(post.getId());

        String adminToken = grantAdminAndLogin(admin);
        ResponseEntity<JsonNode> response = resolveReport(adminToken, report.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ContentStatus.REPORTED, postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertEquals(ContentStatus.REPORTED.name(), graphPostStatus(post.getId()));
        assertEquals(ReportStatus.RESOLVED, reportRepository.findById(report.getId()).orElseThrow().getStatus());
    }

    @Test
    void failedNeo4jReportResolutionRestoresPostAndLeavesReportOpen() {
        User author = registerUser("Report Rollback Author");
        User reporter = registerUser("Report Rollback Reporter");
        User admin = registerUser("Report Rollback Admin");
        String authorToken = login(author.getEmail());
        String reporterToken = login(reporter.getEmail());
        String tag = "E2E Report Rollback Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        assertEquals(HttpStatus.CREATED, createPost(authorToken, "Post must remain active", tag).getStatusCode());
        Post post = postRepository.findByUser(author).stream().findFirst().orElseThrow();
        assertEquals(HttpStatus.CREATED, createPostReport(reporterToken, post.getId()).getStatusCode());
        Report report = openPostReport(post.getId());

        String adminToken = grantAdminAndLogin(admin);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new RuntimeException("Simulated report synchronization failure after graph update");
        })
                .when(postNeo4jRepository)
                .updatePostStatus(post.getId(), ContentStatus.REPORTED.name());

        try {
            ResponseEntity<JsonNode> response = resolveReport(adminToken, report.getId());

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
            assertEquals(ContentStatus.ACTIVE, postRepository.findById(post.getId()).orElseThrow().getStatus());
            assertEquals(ContentStatus.ACTIVE.name(), graphPostStatus(post.getId()));
            assertEquals(ReportStatus.OPEN, reportRepository.findById(report.getId()).orElseThrow().getStatus());
        } finally {
            reset(postNeo4jRepository);
        }
    }

    @Test
    void directAdminReportMarksPostReportedAcrossMysqlAndNeo4j() {
        User author = registerUser("Direct Admin Report Author");
        User admin = registerUser("Direct Report Admin");
        String authorToken = login(author.getEmail());
        String tag = "E2E Direct Report Tag " + UUID.randomUUID();
        seedGraphDependencies(author.getId(), tag);
        assertEquals(HttpStatus.CREATED, createPost(authorToken, "Post removed by admin", tag).getStatusCode());
        Post post = postRepository.findByUser(author).stream().findFirst().orElseThrow();

        ResponseEntity<JsonNode> response = directAdminReport(grantAdminAndLogin(admin), post.getId());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ContentStatus.REPORTED, postRepository.findById(post.getId()).orElseThrow().getStatus());
        assertEquals(ContentStatus.REPORTED.name(), graphPostStatus(post.getId()));
        assertEquals(1, reportRepository.findAllByTargetIdAndTargetType(post.getId(), ReportType.POST).size());
    }

    private User registerUser(String fullName) {
        String email = "e2e-post-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> registration = post("/auth/register", Map.of(
                "fullname", fullName,
                "email", email,
                "password", PASSWORD,
                "confirmPassword", PASSWORD));
        assertEquals(HttpStatus.CREATED, registration.getStatusCode());
        return userRepository.findByEmail(email).orElseThrow();
    }

    private String login(String email) {
        ResponseEntity<JsonNode> response = post("/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        String accessToken = response.getBody().at("/data/token").asText();
        assertFalse(accessToken.isBlank());
        return accessToken;
    }

    private ResponseEntity<JsonNode> createPost(String accessToken, String content, String tag) {
        return createPost(accessToken, content, tag, null);
    }

    private ResponseEntity<JsonNode> createPost(String accessToken, String content, String tag, String groupId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("content", content);
        form.add("tags", tag);
        if (groupId != null) {
            form.add("groupId", groupId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        return restTemplate.exchange(
                "/users/posts", HttpMethod.POST, new HttpEntity<>(form, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> sharePost(String accessToken, String originalPostId, String content, String tag) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/users/posts/{postId}/share",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("content", content, "tags", Set.of(tag)), headers),
                JsonNode.class,
                originalPostId);
    }

    private ResponseEntity<JsonNode> deletePost(String accessToken, String postId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return restTemplate.exchange(
                "/users/posts/{postId}", HttpMethod.DELETE, new HttpEntity<>(headers), JsonNode.class, postId);
    }

    private ResponseEntity<JsonNode> restorePost(String adminToken, String postId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        return restTemplate.exchange(
                "/admin/posts/{postId}/active", HttpMethod.PUT, new HttpEntity<>(headers), JsonNode.class, postId);
    }

    private ResponseEntity<JsonNode> createPostReport(String accessToken, String postId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/users/posts/{postId}/report",
                HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "reason", "Inappropriate content",
                        "description", "E2E report",
                        "targetId", postId,
                        "targetType", "POST"), headers),
                JsonNode.class,
                postId);
    }

    private ResponseEntity<JsonNode> resolveReport(String adminToken, String reportId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/admin/reports/{reportId}/resolve",
                HttpMethod.PUT,
                new HttpEntity<>(Map.of("reason", "Inappropriate content", "adminNotes", "Removed by administrator"), headers),
                JsonNode.class,
                reportId);
    }

    private ResponseEntity<JsonNode> directAdminReport(String adminToken, String postId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(
                "/admin/posts/{postId}/report",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("reason", "Inappropriate content", "adminNotes", "Removed by administrator"), headers),
                JsonNode.class,
                postId);
    }

    private String grantAdminAndLogin(User user) {
        user.setRole(UserRole.ADMIN);
        userRepository.saveAndFlush(user);
        return login(user.getEmail());
    }

    private Report openPostReport(String postId) {
        return reportRepository.findAllByTargetIdAndTargetType(postId, ReportType.POST).stream()
                .filter(report -> report.getStatus() == ReportStatus.OPEN)
                .findFirst()
                .orElseThrow();
    }

    private Group createApprovedGroupMembership(User user) {
        Group group = groupRepository.save(Group.builder()
                .name("E2E Post Rollback Group " + UUID.randomUUID())
                .description("Group for Neo4j rollback test")
                .memberCount(1)
                .status(GroupStatus.ACTIVE)
                .build());
        groupMemberRepository.save(GroupMember.builder()
                .id(new GroupMemberId(group.getId(), user.getId()))
                .group(group)
                .user(user)
                .role(MemberRole.LEADER)
                .status(MemberStatus.APPROVED)
                .build());
        return group;
    }

    private void seedGraphDependencies(String userId, String tag) {
        neo4jClient.query("""
                MERGE (:User {id: $userId})
                MERGE (category:Category {name: 'Sở thích'})
                MERGE (interest:Interest {name: $tag})
                MERGE (interest)-[:SPECIFIC_OF]->(category)
                """)
                .bind(userId).to("userId")
                .bind(tag).to("tag")
                .run();
    }

    private long countPostGraph(String userId, String postId, String tag) {
        Map<String, Object> row = neo4jClient.query("""
                MATCH (:User {id: $userId})-[:POSTED]->(post:Post {id: $postId, status: 'ACTIVE'})
                      -[:HAS_TAG]->(:Interest {name: $tag})
                RETURN count(post) AS total
                """)
                .bind(userId).to("userId")
                .bind(postId).to("postId")
                .bind(tag).to("tag")
                .fetch()
                .one()
                .orElseThrow();
        return ((Number) row.get("total")).longValue();
    }

    private long countPostsCreatedBy(String userId) {
        Map<String, Object> row = neo4jClient.query("""
                MATCH (:User {id: $userId})-[:POSTED]->(post:Post)
                RETURN count(post) AS total
                """)
                .bind(userId).to("userId")
                .fetch()
                .one()
                .orElseThrow();
        return ((Number) row.get("total")).longValue();
    }

    private long countSharedPostGraph(String sharerId, String sharedPostId, String originalPostId, String tag) {
        Map<String, Object> row = neo4jClient.query("""
                MATCH (:User {id: $sharerId})-[:POSTED]->(shared:Post {id: $sharedPostId, status: 'ACTIVE'})
                      -[:QUOTES]->(:Post {id: $originalPostId})
                MATCH (shared)-[:HAS_TAG]->(:Interest {name: $tag})
                RETURN count(shared) AS total
                """)
                .bind(sharerId).to("sharerId")
                .bind(sharedPostId).to("sharedPostId")
                .bind(originalPostId).to("originalPostId")
                .bind(tag).to("tag")
                .fetch()
                .one()
                .orElseThrow();
        return ((Number) row.get("total")).longValue();
    }

    private String graphPostStatus(String postId) {
        Map<String, Object> row = neo4jClient.query("""
                MATCH (post:Post {id: $postId})
                RETURN post.status AS status
                """)
                .bind(postId).to("postId")
                .fetch()
                .one()
                .orElseThrow();
        return (String) row.get("status");
    }
}
