package com.example.campushub.repositories.neo4j.projections;

public record FriendSuggestionCandidate(
        String id,
        String fullName,
        String avatarUrl,
        String majorName) {

    public String getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getMajorName() {
        return majorName;
    }
}
