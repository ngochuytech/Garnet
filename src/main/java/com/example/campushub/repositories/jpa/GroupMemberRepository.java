package com.example.campushub.repositories.jpa;

import com.example.campushub.enums.MemberStatus;
import com.example.campushub.enums.MemberRole;
import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    List<GroupMember> findByGroup(Group group);
    List<GroupMember> findByUser(User user);

    @EntityGraph(attributePaths = "user")
    Page<GroupMember> findByGroup_IdAndStatus(String groupId, MemberStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "user")
    Optional<GroupMember> findFirstByGroup_IdAndRoleAndStatus(String groupId, MemberRole role, MemberStatus status);

    @EntityGraph(attributePaths = "user")
    List<GroupMember> findByGroup_IdInAndRoleAndStatus(Collection<String> groupIds, MemberRole role, MemberStatus status);
}
