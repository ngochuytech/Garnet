package com.example.campushub.repositories.jpa;

import com.example.campushub.models.jpa.Group;
import com.example.campushub.models.jpa.GroupMember;
import com.example.campushub.models.jpa.GroupMemberId;
import com.example.campushub.models.jpa.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    List<GroupMember> findByGroup(Group group);
    List<GroupMember> findByUser(User user);
}