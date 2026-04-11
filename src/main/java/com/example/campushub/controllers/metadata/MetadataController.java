package com.example.campushub.controllers.metadata;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.neo4j.MajorNode;
import com.example.campushub.models.neo4j.TagNode;
import com.example.campushub.repositories.neo4j.MajorNeo4jRepository;
import com.example.campushub.repositories.neo4j.TagNeo4jRepository;
import com.example.campushub.responses.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/metadata")
@RequiredArgsConstructor
public class MetadataController {
    
    private final MajorNeo4jRepository majorNeo4jRepository;
    private final TagNeo4jRepository tagNeo4jRepository;

    @GetMapping("/majors")
    public ResponseEntity<?> getAllMajors() {
        List<String> majors = majorNeo4jRepository.findAll().stream()
                .map(MajorNode::getName)
                .collect(Collectors.toList());
        return ResponseEntity.ok().body(ApiResponse.ok(majors));
    }

    @GetMapping("/tags")
    public ResponseEntity<?> getAllTags() {
        List<String> tags = tagNeo4jRepository.findAll().stream()
                .map(TagNode::getName)
                .collect(Collectors.toList());
        return ResponseEntity.ok().body(ApiResponse.ok(tags));
    }
}
