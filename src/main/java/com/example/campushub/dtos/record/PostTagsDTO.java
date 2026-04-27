package com.example.campushub.dtos.record;

import java.util.Set;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PostTagsDTO{
    String postId;
    Set<String> tagNames;
}
