package com.example.campushub.dtos.record.posts;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostTags{
    String postId;
    Set<String> tagNames;
}
