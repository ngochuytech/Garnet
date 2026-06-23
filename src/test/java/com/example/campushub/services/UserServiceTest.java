package com.example.campushub.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.repositories.neo4j.UserNeo4jRepository;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserNeo4jRepository userNeo4jRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void setupUserProfilePersistsMysqlAndSynchronizesTheWholeGraphProfile() {
        User user = user("user-1", "Old Major");
        Set<String> hobbies = Set.of("Chess", "Music");
        when(userRepository.save(user)).thenReturn(user);

        userService.setupUserProfile(user, "Computer Science", hobbies);

        assertEquals("Computer Science", user.getDepartment());
        verify(userRepository).save(user);
        verify(userNeo4jRepository).updateUserProfileGraph(user.getId(), "Computer Science", hobbies);
    }

    @Test
    void setupUserProfilePropagatesNeo4jFailureWithoutManualMysqlOverwrite() {
        User user = user("user-1", "Old Major");
        Set<String> hobbies = Set.of("Chess");
        when(userRepository.save(user)).thenReturn(user);
        when(userNeo4jRepository.updateUserProfileGraph(user.getId(), "Computer Science", hobbies))
                .thenThrow(new IllegalStateException("Neo4j unavailable"));

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> userService.setupUserProfile(user, "Computer Science", hobbies));

        assertNotNull(exception.getCause());
        verify(userRepository, times(1)).save(user);
    }

    private User user(String id, String department) {
        return User.builder()
                .id(id)
                .fullName("Test User")
                .email("user@example.com")
                .password("encoded-password")
                .department(department)
                .build();
    }
}
