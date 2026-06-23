package com.example.campushub.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.neo4j.Neo4jContainer;
import org.testcontainers.utility.DockerImageName;

import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.GroupStatus;
import com.example.campushub.enums.NotificationType;
import com.example.campushub.enums.ReactionType;
import com.example.campushub.enums.UserActionTokenPurpose;
import com.example.campushub.enums.UserRole;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.events.NotificationEvent;
import com.example.campushub.models.jpa.Comment;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserActionToken;
import com.example.campushub.repositories.jpa.CommentReactionRepository;
import com.example.campushub.repositories.jpa.CommentRepository;
import com.example.campushub.repositories.jpa.GroupMemberRepository;
import com.example.campushub.repositories.jpa.GroupRepository;
import com.example.campushub.repositories.jpa.NotificationRepository;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.TokenRepository;
import com.example.campushub.repositories.jpa.UserActionTokenRepository;
import com.example.campushub.repositories.neo4j.GroupNeo4jRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;

import tools.jackson.databind.JsonNode;

/**
 * High-value defence scenarios that require the real Spring application and both
 * databases. Each test uses the public HTTP API and asserts persistent state.
 */
@Testcontainers
@ActiveProfiles("e2e")
@AutoConfigureTestRestTemplate
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class CriticalDefenseFlowsE2EIT extends AbstractE2EIT {

    private static final String PASSWORD = "CorrectHorseBatteryStaple1!";

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4.4"))
            .withDatabaseName("campushub_e2e_critical")
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
    private TokenRepository tokenRepository;

    @Autowired
    private UserActionTokenRepository userActionTokenRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    @Qualifier("transactionManager")
    private PlatformTransactionManager transactionManager;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupMemberRepository groupMemberRepository;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private CommentReactionRepository commentReactionRepository;

    @Autowired
    private Neo4jClient neo4jClient;

    @MockitoSpyBean
    private UserNeo4jRepository userNeo4jRepository;

    @MockitoSpyBean
    private GroupNeo4jRepository groupNeo4jRepository;

    @Test
    void logoutRevokesStoredRefreshTokenAndItCannotBeUsedAgain() {
        User user = registerUser("Logout User");
        ResponseEntity<JsonNode> login = login(user.getEmail());
        String refreshToken = refreshTokenFrom(login);

        ResponseEntity<JsonNode> logout = request(
                "/auth/logout", HttpMethod.POST, null, null, Map.of(HttpHeaders.COOKIE, "refreshToken=" + refreshToken));

        assertEquals(HttpStatus.OK, logout.getStatusCode());
        assertTrue(logout.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
        assertNull(tokenRepository.findByToken(refreshToken));

        ResponseEntity<JsonNode> refreshAfterLogout = request(
                "/auth/refresh-token", HttpMethod.POST, null, null,
                Map.of(HttpHeaders.COOKIE, "refreshToken=" + refreshToken));
        assertEquals(HttpStatus.UNAUTHORIZED, refreshAfterLogout.getStatusCode());
    }

    @Test
    void profileSetupSynchronizesMajorAndInterestAcrossMysqlAndNeo4j() {
        User user = registerUser("Profile User");
        String accessToken = accessToken(login(user.getEmail()));
        String interest = "E2E Chess " + UUID.randomUUID();
        String major = "E2E Computer Science";
        seedInterest(interest);

        ResponseEntity<JsonNode> response = request(
                "/users/profiles/setup", HttpMethod.POST, accessToken,
                Map.of("major", major, "hobbies", List.of(interest)), Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(major, userRepository.findById(user.getId()).orElseThrow().getDepartment());
        assertEquals(1L, countMajorRelation(user.getId(), major));
        assertEquals(1L, countInterestRelation(user.getId(), interest));
    }

    @Test
    void profileSetupRollsBackMysqlWhenNeo4jSynchronizationFails() {
        User user = registerUser("Profile Rollback User");
        String originalMajor = "Original Major";
        String newMajor = "Major That Must Roll Back";
        user.setDepartment(originalMajor);
        userRepository.save(user);
        String accessToken = accessToken(login(user.getEmail()));

        doThrow(new IllegalStateException("Forced Neo4j failure"))
                .when(userNeo4jRepository)
                .updateUserProfileGraph(eq(user.getId()), eq(newMajor), any());
        try {
            ResponseEntity<JsonNode> response = request(
                    "/users/profiles/setup", HttpMethod.POST, accessToken,
                    Map.of("major", newMajor, "hobbies", List.of("Chess")), Map.of());

            assertFalse(response.getStatusCode().is2xxSuccessful());
            assertEquals(originalMajor, userRepository.findById(user.getId()).orElseThrow().getDepartment());
        } finally {
            reset(userNeo4jRepository);
        }
    }

    @Test
    void failedNeo4jLeaderLinkLeavesNoOrphanGroup() {
        User leader = registerUser("Group Graph Failure Leader");
        String accessToken = accessToken(login(leader.getEmail()));
        String groupName = "E2E Orphan Check " + UUID.randomUUID();

        doThrow(new IllegalStateException("Forced Neo4j leader link failure"))
                .when(groupNeo4jRepository)
                .addUserToGroup(eq(leader.getId()), any());
        try {
            ResponseEntity<JsonNode> response = request(
                    "/users/groups", HttpMethod.POST, accessToken,
                    Map.of("name", groupName, "description", "Must be compensated when graph sync fails"), Map.of());

            assertFalse(response.getStatusCode().is2xxSuccessful());
            assertTrue(groupRepository.findAll().stream().noneMatch(group -> groupName.equals(group.getName())));
            assertTrue(groupMemberRepository.findAll().stream()
                    .noneMatch(member -> leader.getId().equals(member.getId().getUserId())));
            assertEquals(0L, countGroupNodesByName(groupName));
        } finally {
            reset(groupNeo4jRepository);
        }
    }

    @Test
    void groupLeaderApprovesMemberAndSynchronizesMembershipAcrossBothDatabases() {
        User leader = registerUser("Group Leader");
        User applicant = registerUser("Group Applicant");
        String leaderToken = accessToken(login(leader.getEmail()));
        String applicantToken = accessToken(login(applicant.getEmail()));

        ResponseEntity<JsonNode> createGroup = request(
                "/users/groups", HttpMethod.POST, leaderToken,
                Map.of("name", "E2E Group " + UUID.randomUUID(), "description", "Group approval flow"), Map.of());
        assertEquals(HttpStatus.OK, createGroup.getStatusCode());
        String groupId = createGroup.getBody().at("/data/id").asText();
        assertFalse(groupId.isBlank());

        ResponseEntity<JsonNode> join = request(
                "/users/groups/" + groupId + "/join", HttpMethod.POST, applicantToken, null, Map.of());
        assertEquals(HttpStatus.OK, join.getStatusCode());

        ResponseEntity<JsonNode> approve = request(
                "/users/groups/" + groupId + "/approve/" + applicant.getId(),
                HttpMethod.POST, leaderToken, null, Map.of());
        assertEquals(HttpStatus.OK, approve.getStatusCode());

        GroupMember member = groupMemberRepository
                .findById(new GroupMemberId(groupId, applicant.getId()))
                .orElseThrow();
        Group group = groupRepository.findById(groupId).orElseThrow();
        assertEquals(MemberStatus.APPROVED, member.getStatus());
        assertEquals(2, group.getMemberCount());
        assertEquals(1L, countGroupMembership(applicant.getId(), groupId));
    }

    @Test
    void failedNeo4jApprovalKeepsPendingMemberAndCountUnchanged() {
        User leader = registerUser("Approval Graph Failure Leader");
        User applicant = registerUser("Approval Graph Failure Applicant");
        String leaderToken = accessToken(login(leader.getEmail()));
        String applicantToken = accessToken(login(applicant.getEmail()));

        ResponseEntity<JsonNode> createGroup = request(
                "/users/groups", HttpMethod.POST, leaderToken,
                Map.of("name", "Approval Graph Failure " + UUID.randomUUID(), "description", "Rollback approval"),
                Map.of());
        assertEquals(HttpStatus.OK, createGroup.getStatusCode());
        String groupId = createGroup.getBody().at("/data/id").asText();
        assertEquals(HttpStatus.OK, request(
                "/users/groups/" + groupId + "/join", HttpMethod.POST, applicantToken, null, Map.of())
                .getStatusCode());

        doThrow(new IllegalStateException("Forced Neo4j approval failure"))
                .when(groupNeo4jRepository)
                .addUserToGroup(eq(applicant.getId()), eq(groupId));
        try {
            ResponseEntity<JsonNode> response = request(
                    "/users/groups/" + groupId + "/approve/" + applicant.getId(),
                    HttpMethod.POST, leaderToken, null, Map.of());

            assertFalse(response.getStatusCode().is2xxSuccessful());
            assertEquals(MemberStatus.PENDING, groupMemberRepository
                    .findById(new GroupMemberId(groupId, applicant.getId())).orElseThrow().getStatus());
            assertEquals(1, groupRepository.findById(groupId).orElseThrow().getMemberCount());
            assertEquals(0L, countGroupMembership(applicant.getId(), groupId));
        } finally {
            reset(groupNeo4jRepository);
        }
    }

    @Test
    void groupLeaderCanApproveTwoPendingMembersConcurrentlyWithoutLosingMemberCount() throws Exception {
        User leader = registerUser("Concurrent Group Leader");
        User firstApplicant = registerUser("Concurrent Applicant One");
        User secondApplicant = registerUser("Concurrent Applicant Two");
        String leaderToken = accessToken(login(leader.getEmail()));
        String firstApplicantToken = accessToken(login(firstApplicant.getEmail()));
        String secondApplicantToken = accessToken(login(secondApplicant.getEmail()));

        ResponseEntity<JsonNode> createGroup = request(
                "/users/groups", HttpMethod.POST, leaderToken,
                Map.of("name", "Concurrent E2E Group " + UUID.randomUUID(), "description", "Concurrent approval"),
                Map.of());
        assertEquals(HttpStatus.OK, createGroup.getStatusCode());
        String groupId = createGroup.getBody().at("/data/id").asText();

        assertEquals(HttpStatus.OK, request(
                "/users/groups/" + groupId + "/join", HttpMethod.POST, firstApplicantToken, null, Map.of())
                .getStatusCode());
        assertEquals(HttpStatus.OK, request(
                "/users/groups/" + groupId + "/join", HttpMethod.POST, secondApplicantToken, null, Map.of())
                .getStatusCode());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<JsonNode>> firstApproval = executor.submit(() ->
                    approveWhenReleased(ready, start, groupId, firstApplicant.getId(), leaderToken));
            Future<ResponseEntity<JsonNode>> secondApproval = executor.submit(() ->
                    approveWhenReleased(ready, start, groupId, secondApplicant.getId(), leaderToken));

            assertTrue(ready.await(5, TimeUnit.SECONDS), "Both approval requests should be ready before release");
            start.countDown();

            assertEquals(HttpStatus.OK, firstApproval.get(10, TimeUnit.SECONDS).getStatusCode());
            assertEquals(HttpStatus.OK, secondApproval.get(10, TimeUnit.SECONDS).getStatusCode());
        } finally {
            executor.shutdownNow();
        }

        Group group = groupRepository.findById(groupId).orElseThrow();
        assertEquals(3, group.getMemberCount());
        assertEquals(MemberStatus.APPROVED, groupMemberRepository
                .findById(new GroupMemberId(groupId, firstApplicant.getId())).orElseThrow().getStatus());
        assertEquals(MemberStatus.APPROVED, groupMemberRepository
                .findById(new GroupMemberId(groupId, secondApplicant.getId())).orElseThrow().getStatus());
        assertEquals(1L, countGroupMembership(firstApplicant.getId(), groupId));
        assertEquals(1L, countGroupMembership(secondApplicant.getId(), groupId));
    }

    @Test
    void groupLeaderCanKickTwoMembersConcurrentlyWithoutLosingMemberCount() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Concurrent Kick");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<JsonNode>> firstKick = executor.submit(() -> requestWhenReleased(
                    ready, start,
                    "/users/groups/" + fixture.groupId() + "/members/" + fixture.firstMember().getId(),
                    HttpMethod.DELETE, fixture.leaderToken()));
            Future<ResponseEntity<JsonNode>> secondKick = executor.submit(() -> requestWhenReleased(
                    ready, start,
                    "/users/groups/" + fixture.groupId() + "/members/" + fixture.secondMember().getId(),
                    HttpMethod.DELETE, fixture.leaderToken()));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(HttpStatus.OK, firstKick.get(10, TimeUnit.SECONDS).getStatusCode());
            assertEquals(HttpStatus.OK, secondKick.get(10, TimeUnit.SECONDS).getStatusCode());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, groupRepository.findById(fixture.groupId()).orElseThrow().getMemberCount());
        assertTrue(groupMemberRepository.findById(new GroupMemberId(fixture.groupId(), fixture.firstMember().getId()))
                .isEmpty());
        assertTrue(groupMemberRepository.findById(new GroupMemberId(fixture.groupId(), fixture.secondMember().getId()))
                .isEmpty());
        assertEquals(0L, countGroupMembership(fixture.firstMember().getId(), fixture.groupId()));
        assertEquals(0L, countGroupMembership(fixture.secondMember().getId(), fixture.groupId()));
    }

    @Test
    void failedNeo4jKickKeepsMemberAndCountUnchanged() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Kick Graph Failure");

        doThrow(new IllegalStateException("Forced Neo4j kick failure"))
                .when(groupNeo4jRepository)
                .removeUserFromGroup(eq(fixture.firstMember().getId()), eq(fixture.groupId()));
        try {
            ResponseEntity<JsonNode> response = request(
                    "/users/groups/" + fixture.groupId() + "/members/" + fixture.firstMember().getId(),
                    HttpMethod.DELETE, fixture.leaderToken(), null, Map.of());

            assertFalse(response.getStatusCode().is2xxSuccessful());
            assertEquals(3, groupRepository.findById(fixture.groupId()).orElseThrow().getMemberCount());
            assertEquals(MemberStatus.APPROVED, groupMemberRepository
                    .findById(new GroupMemberId(fixture.groupId(), fixture.firstMember().getId()))
                    .orElseThrow()
                    .getStatus());
            assertEquals(1L, countGroupMembership(fixture.firstMember().getId(), fixture.groupId()));
        } finally {
            reset(groupNeo4jRepository);
        }
    }

    @Test
    void twoMembersCanLeaveConcurrentlyWithoutLosingMemberCount() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Concurrent Leave");

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<JsonNode>> firstLeave = executor.submit(() -> requestWhenReleased(
                    ready, start,
                    "/users/groups/" + fixture.groupId() + "/leave",
                    HttpMethod.DELETE, fixture.firstMemberToken()));
            Future<ResponseEntity<JsonNode>> secondLeave = executor.submit(() -> requestWhenReleased(
                    ready, start,
                    "/users/groups/" + fixture.groupId() + "/leave",
                    HttpMethod.DELETE, fixture.secondMemberToken()));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            assertEquals(HttpStatus.OK, firstLeave.get(10, TimeUnit.SECONDS).getStatusCode());
            assertEquals(HttpStatus.OK, secondLeave.get(10, TimeUnit.SECONDS).getStatusCode());
        } finally {
            executor.shutdownNow();
        }

        assertEquals(1, groupRepository.findById(fixture.groupId()).orElseThrow().getMemberCount());
        assertTrue(groupMemberRepository.findById(new GroupMemberId(fixture.groupId(), fixture.firstMember().getId()))
                .isEmpty());
        assertTrue(groupMemberRepository.findById(new GroupMemberId(fixture.groupId(), fixture.secondMember().getId()))
                .isEmpty());
        assertEquals(0L, countGroupMembership(fixture.firstMember().getId(), fixture.groupId()));
        assertEquals(0L, countGroupMembership(fixture.secondMember().getId(), fixture.groupId()));
    }

    @Test
    void failedNeo4jLeaveKeepsMemberAndCountUnchanged() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Leave Graph Failure");

        doThrow(new IllegalStateException("Forced Neo4j leave failure"))
                .when(groupNeo4jRepository)
                .removeUserFromGroup(eq(fixture.firstMember().getId()), eq(fixture.groupId()));
        try {
            ResponseEntity<JsonNode> response = request(
                    "/users/groups/" + fixture.groupId() + "/leave",
                    HttpMethod.DELETE, fixture.firstMemberToken(), null, Map.of());

            assertFalse(response.getStatusCode().is2xxSuccessful());
            assertEquals(3, groupRepository.findById(fixture.groupId()).orElseThrow().getMemberCount());
            assertEquals(MemberStatus.APPROVED, groupMemberRepository
                    .findById(new GroupMemberId(fixture.groupId(), fixture.firstMember().getId()))
                    .orElseThrow()
                    .getStatus());
            assertEquals(1L, countGroupMembership(fixture.firstMember().getId(), fixture.groupId()));
        } finally {
            reset(groupNeo4jRepository);
        }
    }

    @Test
    void deleteGroupMarksMysqlDeletedAndRemovesTheGraphGroup() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Delete Group Success");

        ResponseEntity<JsonNode> response = request(
                "/users/groups/" + fixture.groupId(), HttpMethod.DELETE, fixture.leaderToken(), null, Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GroupStatus.DELETED, groupRepository.findById(fixture.groupId()).orElseThrow().getStatus());
        assertEquals(0L, countGroupNodesById(fixture.groupId()));
        assertEquals(0L, countGroupMembership(fixture.firstMember().getId(), fixture.groupId()));
        assertEquals(0L, countGroupMembership(fixture.secondMember().getId(), fixture.groupId()));
    }

    @Test
    void failedNeo4jGroupDeletionKeepsMysqlActiveAndGraphMemberships() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Delete Group Failure");

        doThrow(new IllegalStateException("Forced Neo4j group deletion failure"))
                .when(groupNeo4jRepository)
                .deleteGroupById(eq(fixture.groupId()));
        try {
            ResponseEntity<JsonNode> response = request(
                    "/users/groups/" + fixture.groupId(), HttpMethod.DELETE, fixture.leaderToken(), null, Map.of());

            assertFalse(response.getStatusCode().is2xxSuccessful());
            assertEquals(GroupStatus.ACTIVE, groupRepository.findById(fixture.groupId()).orElseThrow().getStatus());
            assertEquals(1L, countGroupNodesById(fixture.groupId()));
            assertEquals(1L, countGroupMembership(fixture.firstMember().getId(), fixture.groupId()));
            assertEquals(1L, countGroupMembership(fixture.secondMember().getId(), fixture.groupId()));
        } finally {
            reset(groupNeo4jRepository);
        }
    }

    @Test
    void updateGroupNameSynchronizesMysqlAndNeo4j() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Rename Group Success");
        String newName = "Renamed E2E Group " + UUID.randomUUID();

        ResponseEntity<JsonNode> response = request(
                "/users/groups/" + fixture.groupId() + "/name", HttpMethod.PUT, fixture.leaderToken(),
                Map.of("name", newName), Map.of());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(newName, groupRepository.findById(fixture.groupId()).orElseThrow().getName());
        assertEquals(newName, groupNodeName(fixture.groupId()));
    }

    @Test
    void failedNeo4jRenameKeepsTheOriginalNameInBothDatabases() throws Exception {
        ApprovedGroupFixture fixture = createGroupWithTwoApprovedMembers("Rename Group Failure");
        String originalName = groupRepository.findById(fixture.groupId()).orElseThrow().getName();
        String newName = "Name That Must Roll Back " + UUID.randomUUID();

        doThrow(new IllegalStateException("Forced Neo4j rename failure"))
                .when(groupNeo4jRepository)
                .updateGroupName(eq(fixture.groupId()), eq(newName));
        try {
            ResponseEntity<JsonNode> response = request(
                    "/users/groups/" + fixture.groupId() + "/name", HttpMethod.PUT, fixture.leaderToken(),
                    Map.of("name", newName), Map.of());

            assertFalse(response.getStatusCode().is2xxSuccessful());
            assertEquals(originalName, groupRepository.findById(fixture.groupId()).orElseThrow().getName());
            assertEquals(originalName, groupNodeName(fixture.groupId()));
        } finally {
            reset(groupNeo4jRepository);
        }
    }

    @Test
    void concurrentCommentLikesKeepStoredCountEqualToReactionCount() throws Exception {
        User author = registerUser("Comment Author");
        Post post = postRepository.save(Post.builder()
                .user(author)
                .content("Post for concurrent comment likes")
                .build());
        Comment comment = commentRepository.save(Comment.builder()
                .post(post)
                .user(author)
                .content("Comment to like concurrently")
                .build());

        int likerCount = 6;
        List<String> likerTokens = new ArrayList<>();
        for (int index = 0; index < likerCount; index++) {
            User liker = registerUser("Concurrent Liker " + index);
            likerTokens.add(accessToken(login(liker.getEmail())));
        }

        CountDownLatch ready = new CountDownLatch(likerCount);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(likerCount);
        try {
            List<Future<ResponseEntity<JsonNode>>> likes = new ArrayList<>();
            for (String likerToken : likerTokens) {
                likes.add(executor.submit(() -> requestWhenReleased(
                        ready,
                        start,
                        "/users/comments/" + comment.getId() + "/like",
                        HttpMethod.POST,
                        likerToken)));
            }

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            for (Future<ResponseEntity<JsonNode>> like : likes) {
                assertEquals(HttpStatus.OK, like.get(10, TimeUnit.SECONDS).getStatusCode());
            }
        } finally {
            executor.shutdownNow();
        }

        Comment updatedComment = commentRepository.findById(comment.getId()).orElseThrow();
        long likeReactionCount = commentReactionRepository.findAll().stream()
                .filter(reaction -> reaction.getId().getCommentId().equals(comment.getId()))
                .filter(reaction -> reaction.getType() == ReactionType.LIKE)
                .count();

        assertEquals(likeReactionCount, updatedComment.getLiked().longValue(),
                "Comment.liked must equal the number of LIKE reaction rows");
        assertEquals(likerCount, likeReactionCount);
    }

    @Test
    void resetPasswordTokenCanBeConsumedByOnlyOneConcurrentRequest() throws Exception {
        User user = registerUser("Concurrent Reset User");
        String rawToken = "e2e-reset-" + UUID.randomUUID();
        userActionTokenRepository.save(UserActionToken.builder()
                .tokenHash(hashToken(rawToken))
                .purpose(UserActionTokenPurpose.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .user(user)
                .build());

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<ResponseEntity<JsonNode>> firstReset = executor.submit(() -> resetPasswordWhenReleased(
                    ready, start, rawToken, "NewPasswordOne1!"));
            Future<ResponseEntity<JsonNode>> secondReset = executor.submit(() -> resetPasswordWhenReleased(
                    ready, start, rawToken, "NewPasswordTwo2!"));

            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            List<ResponseEntity<JsonNode>> responses = List.of(
                    firstReset.get(10, TimeUnit.SECONDS),
                    secondReset.get(10, TimeUnit.SECONDS));
            long successfulRequests = responses.stream()
                    .filter(response -> response.getStatusCode() == HttpStatus.OK)
                    .count();

            assertEquals(1L, successfulRequests,
                    "Only one request may consume a password-reset token");
        } finally {
            executor.shutdownNow();
        }

        UserActionToken storedToken = userActionTokenRepository
                .findByTokenHashAndPurpose(hashToken(rawToken), UserActionTokenPurpose.PASSWORD_RESET)
                .orElseThrow();
        assertNotNull(storedToken.getConsumedAt());
    }

    @Test
    void rolledBackTransactionDoesNotCreateNotification() throws InterruptedException {
        User actor = registerUser("Rollback Notification Actor");
        User recipient = registerUser("Rollback Notification Recipient");
        NotificationEvent event = NotificationEvent.builder()
                .actorId(actor.getId())
                .recipientId(recipient.getId())
                .recipientName(recipient.getUsername())
                .type(NotificationType.NEW_FOLLOWER)
                .targetType("USER")
                .targetId(recipient.getId())
                .message("This notification must not be persisted")
                .build();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        // The old asynchronous listener could have already persisted the notification.
        // AFTER_COMMIT must not invoke it at all after this rollback.
        Thread.sleep(500);
        assertTrue(notificationRepository.findFirstByRecipientIdAndActorIdAndType(
                recipient.getId(), actor.getId(), NotificationType.NEW_FOLLOWER).isEmpty());
    }

    @Test
    void adminEndpointRejectsNormalUserButAllowsAdmin() {
        User normalUser = registerUser("Normal User");
        User admin = registerUser("Admin User");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);

        ResponseEntity<JsonNode> normalResponse = request(
                "/admin/users", HttpMethod.GET, accessToken(login(normalUser.getEmail())), null, Map.of());
        assertEquals(HttpStatus.FORBIDDEN, normalResponse.getStatusCode(), normalResponse.getBody().toString());

        ResponseEntity<JsonNode> adminResponse = request(
                "/admin/users", HttpMethod.GET, accessToken(login(admin.getEmail())), null, Map.of());
        assertEquals(HttpStatus.OK, adminResponse.getStatusCode());
    }

    @Test
    void banningUserInvalidatesExistingAccessTokenAndUnbanRestoresAccess() {
        User targetUser = registerUser("Ban Target");
        User admin = registerUser("Ban Admin");
        admin.setRole(UserRole.ADMIN);
        userRepository.save(admin);
        String targetAccessToken = accessToken(login(targetUser.getEmail()));
        String adminAccessToken = accessToken(login(admin.getEmail()));

        ResponseEntity<JsonNode> ban = request(
                "/admin/users/" + targetUser.getId() + "/ban", HttpMethod.PUT, adminAccessToken, null, Map.of());
        assertEquals(HttpStatus.OK, ban.getStatusCode());
        assertEquals(UserStatus.BANNED, userRepository.findById(targetUser.getId()).orElseThrow().getStatus());

        ResponseEntity<JsonNode> blockedRequest = request(
                "/users/posts/me", HttpMethod.GET, targetAccessToken, null, Map.of());
        assertEquals(HttpStatus.UNAUTHORIZED, blockedRequest.getStatusCode());

        ResponseEntity<JsonNode> unban = request(
                "/admin/users/" + targetUser.getId() + "/unban", HttpMethod.PUT, adminAccessToken, null, Map.of());
        assertEquals(HttpStatus.OK, unban.getStatusCode());
        assertEquals(UserStatus.ACTIVE, userRepository.findById(targetUser.getId()).orElseThrow().getStatus());

        ResponseEntity<JsonNode> restoredRequest = request(
                "/users/posts/me", HttpMethod.GET, targetAccessToken, null, Map.of());
        assertEquals(HttpStatus.OK, restoredRequest.getStatusCode());
    }

    private User registerUser(String fullName) {
        String email = "e2e-critical-" + UUID.randomUUID() + "@example.com";
        ResponseEntity<JsonNode> registration = post("/auth/register", Map.of(
                "fullname", fullName,
                "email", email,
                "password", PASSWORD,
                "confirmPassword", PASSWORD));
        assertEquals(HttpStatus.CREATED, registration.getStatusCode());
        return userRepository.findByEmail(email).orElseThrow();
    }

    private ResponseEntity<JsonNode> login(String email) {
        ResponseEntity<JsonNode> response = post("/auth/login", Map.of("email", email, "password", PASSWORD));
        assertEquals(HttpStatus.OK, response.getStatusCode());
        return response;
    }

    private String accessToken(ResponseEntity<JsonNode> login) {
        String token = login.getBody().at("/data/token").asText();
        assertFalse(token.isBlank());
        return token;
    }

    private String refreshTokenFrom(ResponseEntity<JsonNode> login) {
        String setCookie = login.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
        assertNotNull(setCookie);
        String cookie = setCookie.split(";", 2)[0];
        assertTrue(cookie.startsWith("refreshToken="));
        return cookie.substring("refreshToken=".length());
    }

    private ResponseEntity<JsonNode> request(
            String path, HttpMethod method, String accessToken, Object body, Map<String, String> extraHeaders) {
        HttpHeaders headers = new HttpHeaders();
        if (accessToken != null) {
            headers.setBearerAuth(accessToken);
        }
        extraHeaders.forEach(headers::set);
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), JsonNode.class);
    }

    private ResponseEntity<JsonNode> approveWhenReleased(
            CountDownLatch ready,
            CountDownLatch start,
            String groupId,
            String applicantId,
            String leaderToken) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent group approvals");
        }
        return request(
                "/users/groups/" + groupId + "/approve/" + applicantId,
                HttpMethod.POST,
                leaderToken,
                null,
                Map.of());
    }

    private ResponseEntity<JsonNode> requestWhenReleased(
            CountDownLatch ready,
            CountDownLatch start,
            String path,
            HttpMethod method,
            String accessToken) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent group operations");
        }
        return request(path, method, accessToken, null, Map.of());
    }

    private ResponseEntity<JsonNode> resetPasswordWhenReleased(
            CountDownLatch ready,
            CountDownLatch start,
            String rawToken,
            String newPassword) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting to start concurrent password resets");
        }
        return post("/auth/reset-password", Map.of(
                "token", rawToken,
                "newPassword", newPassword,
                "confirmPassword", newPassword));
    }

    private ApprovedGroupFixture createGroupWithTwoApprovedMembers(String namePrefix) throws Exception {
        User leader = registerUser(namePrefix + " Leader");
        User firstMember = registerUser(namePrefix + " Member One");
        User secondMember = registerUser(namePrefix + " Member Two");
        String leaderToken = accessToken(login(leader.getEmail()));
        String firstMemberToken = accessToken(login(firstMember.getEmail()));
        String secondMemberToken = accessToken(login(secondMember.getEmail()));

        ResponseEntity<JsonNode> createGroup = request(
                "/users/groups", HttpMethod.POST, leaderToken,
                Map.of("name", namePrefix + " Group " + UUID.randomUUID(), "description", "Concurrent member change"),
                Map.of());
        assertEquals(HttpStatus.OK, createGroup.getStatusCode());
        String groupId = createGroup.getBody().at("/data/id").asText();

        assertEquals(HttpStatus.OK, request(
                "/users/groups/" + groupId + "/join", HttpMethod.POST, firstMemberToken, null, Map.of())
                .getStatusCode());
        assertEquals(HttpStatus.OK, request(
                "/users/groups/" + groupId + "/join", HttpMethod.POST, secondMemberToken, null, Map.of())
                .getStatusCode());
        assertEquals(HttpStatus.OK, request(
                "/users/groups/" + groupId + "/approve/" + firstMember.getId(), HttpMethod.POST, leaderToken, null,
                Map.of()).getStatusCode());
        assertEquals(HttpStatus.OK, request(
                "/users/groups/" + groupId + "/approve/" + secondMember.getId(), HttpMethod.POST, leaderToken, null,
                Map.of()).getStatusCode());
        assertEquals(3, groupRepository.findById(groupId).orElseThrow().getMemberCount());

        return new ApprovedGroupFixture(groupId, leaderToken, firstMember, firstMemberToken, secondMember, secondMemberToken);
    }

    private record ApprovedGroupFixture(
            String groupId,
            String leaderToken,
            User firstMember,
            String firstMemberToken,
            User secondMember,
            String secondMemberToken) {
    }

    private void seedInterest(String interest) {
        neo4jClient.query("""
                MERGE (category:Category {name: 'Sở thích'})
                MERGE (interest:Interest {name: $interest})
                MERGE (interest)-[:SPECIFIC_OF]->(category)
                """)
                .bind(interest).to("interest")
                .run();
    }

    private long countMajorRelation(String userId, String major) {
        return count("""
                MATCH (:User {id: $userId})-[:MAJORS_IN]->(:Major {name: $name})
                RETURN count(*) AS total
                """, userId, major);
    }

    private long countInterestRelation(String userId, String interest) {
        return count("""
                MATCH (:User {id: $userId})-[:INTERESTED_IN]->(:Interest {name: $name})
                RETURN count(*) AS total
                """, userId, interest);
    }

    private long countGroupMembership(String userId, String groupId) {
        return count("""
                MATCH (:User {id: $userId})-[:JOINED_GROUP]->(:Group {id: $name})
                RETURN count(*) AS total
                """, userId, groupId);
    }

    private long countGroupNodesByName(String name) {
        Map<String, Object> row = neo4jClient.query("""
                MATCH (:Group {name: $name})
                RETURN count(*) AS total
                """)
                .bind(name).to("name")
                .fetch()
                .one()
                .orElseThrow();
        return ((Number) row.get("total")).longValue();
    }

    private long countGroupNodesById(String groupId) {
        Map<String, Object> row = neo4jClient.query("""
                MATCH (:Group {id: $groupId})
                RETURN count(*) AS total
                """)
                .bind(groupId).to("groupId")
                .fetch()
                .one()
                .orElseThrow();
        return ((Number) row.get("total")).longValue();
    }

    private String groupNodeName(String groupId) {
        Map<String, Object> row = neo4jClient.query("""
                MATCH (group:Group {id: $groupId})
                RETURN group.name AS name
                """)
                .bind(groupId).to("groupId")
                .fetch()
                .one()
                .orElseThrow();
        return (String) row.get("name");
    }

    private String hashToken(String token) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available", exception);
        }
    }

    private long count(String query, String userId, String name) {
        Map<String, Object> row = neo4jClient.query(query)
                .bind(userId).to("userId")
                .bind(name).to("name")
                .fetch()
                .one()
                .orElseThrow();
        return ((Number) row.get("total")).longValue();
    }
}
