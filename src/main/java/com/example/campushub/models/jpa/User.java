package com.example.campushub.models.jpa;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.example.campushub.enums.UserRole;
import com.example.campushub.enums.UserStatus;
import com.example.campushub.models.BaseEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class User extends BaseEntity implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "full_name", length = 255, nullable = false)
    private String fullName;

    @Column(name = "avatar_url", length = 255)
    private String avatarUrl;

    @Column(name = "email", length = 255, nullable = false)
    private String email;

    @Column(name = "password", length = 255, nullable = false)
    private String password;

    @Column(name = "phone", length = 255)
    private String phone;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "department", length = 255)
    private String department;

    @Column(name = "bio")
    private String bio;

    @Column(name = "google_id", length = 255)
    private String googleId;

    @Column(name = "gender")
    private Boolean gender;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
        name = "user_interests",
        joinColumns = @JoinColumn(name = "user_id")
    )
    @Column(name = "interest_name")
    private Set<String> interests;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private UserStatus status = UserStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    @Builder.Default
    private UserRole role = UserRole.USER;


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getUsername() {
        return email;
    }
}
