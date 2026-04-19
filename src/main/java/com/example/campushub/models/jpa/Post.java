package com.example.campushub.models.jpa;

import com.example.campushub.enums.ContentStatus;
import com.example.campushub.models.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "posts")
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Post extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "group_id")
    private String groupId;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "liked")
    @Builder.Default
    private Integer liked = 0;

    @Column(name = "disliked")
    @Builder.Default
    private Integer disliked = 0;

    @Column(name = "comment_count")
    @Builder.Default
    private Integer commentCount = 0;

    @Column(name = "shared_count")
    @Builder.Default
    private Integer sharedCount = 0;

    @ManyToOne
    @JoinColumn(name = "shared_post_id")
    private Post sharedPost;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ContentStatus status = ContentStatus.ACTIVE;
}
