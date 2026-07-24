package com.example.campushub.services;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.dtos.record.profiles.UserProfileUpdatedPayload;
import com.example.campushub.enums.Neo4jEventType;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.models.jpa.Neo4jSyncEvent;
import com.example.campushub.models.jpa.User;
import com.example.campushub.models.jpa.UserInterest;
import com.example.campushub.models.jpa.UserInterestId;
import com.example.campushub.repositories.jpa.Neo4jSyncEventRepository;
import com.example.campushub.repositories.jpa.UserInterestRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.InterestNeo4jRepository;
import com.example.campushub.repositories.neo4j.MajorNeo4jRepository;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import net.datafaker.Faker;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class UserSeeder {

    private final UserRepository userRepository;
    private final UserInterestRepository userInterestRepository;
    private final MajorNeo4jRepository majorNeo4jRepository;
    private final InterestNeo4jRepository interestNeo4jRepository;
    private final Neo4jSyncEventRepository neo4jSyncEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final Faker faker;
    private final ObjectMapper objectMapper;

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert object to JSON", e);
        }
    }

    @Transactional(value = "transactionManager", rollbackFor = Exception.class)
    public int seedUser(int count) {
        List<String> majors = majorNeo4jRepository.findAllMajorNames();
        List<String> tags = interestNeo4jRepository.findLeafTagsToList();
        List<String> mutableList = new ArrayList<>(tags);
        int successCount = 0;
        for (int i = 0; i < count; i++) {
            String fullName = faker.name().fullName();
            String email = faker.internet().emailAddress();
            String password = "password123";
            boolean isGenderMale = faker.bool().bool();
            LocalDate dateOfBirth = faker.date().birthdayLocalDate(19, 26);
            String randomSeed = faker.internet().uuid();
            String avatarUrl = "https://api.dicebear.com/9.x/adventurer/svg?seed=" + randomSeed;

            String randomDept = faker.options().nextElement(majors);
            Collections.shuffle(mutableList);
            List<String> randomPicksTag = mutableList.subList(3, 7);

            User user = new User();
            user.setFullName(fullName);
            user.setEmail(email);
            user.setPassword(passwordEncoder.encode(password));
            user.setGender(isGenderMale);
            user.setDateOfBirth(dateOfBirth);
            user.setAvatarUrl(avatarUrl);
            user.setDepartment(randomDept);
            user.setStatus(UserStatus.ACTIVE);

            user.setCreatedAt(LocalDateTime.now().minusMonths(1));
            userRepository.save(user);
            List<UserInterest> interests = randomPicksTag.stream()
                    .map(name -> UserInterest.builder()
                            .id(new UserInterestId(user.getId(), name))
                            .user(user)
                            .build())
                    .toList();
            userInterestRepository.saveAll(interests);

            UserProfileUpdatedPayload payload = new UserProfileUpdatedPayload(
                    user.getId(),
                    randomDept,
                    Set.copyOf(randomPicksTag));

            neo4jSyncEventRepository.save(Neo4jSyncEvent.pending(
                    Neo4jEventType.USER_PROFILE_UPDATED,
                    user.getId(),
                    toJson(payload)));
            successCount++;
        }
        return successCount;
    }
}
