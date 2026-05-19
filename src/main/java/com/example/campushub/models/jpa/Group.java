package com.example.campushub.models.jpa;

import com.example.campushub.enums.GroupStatus;
import com.example.campushub.models.BaseEntity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "groups")
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Group extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(columnDefinition = "VARCHAR(255)")
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "avatar_url")
    private String avatarUrl;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "member_count", columnDefinition = "INT DEFAULT 1")
    @Builder.Default
    private Integer memberCount = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private GroupStatus status = GroupStatus.ACTIVE;
}