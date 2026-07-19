package com.example.campushub.models.jpa;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "post_tags",
    indexes = {
        @Index(name = "idx_post_tags_tag_name", columnList = "tag_name"),
        @Index(name = "idx_post_tags_post_id", columnList = "post_id")
    })
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PostTag {
    @EmbeddedId
    private PostTagId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("postId")
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;
}
