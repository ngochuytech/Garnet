package com.example.campushub.repositories.jpa;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.campushub.models.jpa.Message;

public interface MessageRepository extends JpaRepository<Message, String> {

    @Query("SELECT m FROM Message m WHERE (m.sender.id = :user1Id AND m.receiver.id = :user2Id) OR (m.sender.id = :user2Id AND m.receiver.id = :user1Id) ORDER BY m.createdAt DESC")
    Page<Message> findChatHistory(@Param("user1Id") String user1Id, @Param("user2Id") String user2Id, Pageable pageable);

    @Query("SELECT m FROM Message m WHERE m.sender.id = :senderId AND m.receiver.id = :receiverId AND m.isRead = false")
    List<Message> findUnreadMessages(@Param("senderId") String senderId, @Param("receiverId") String receiverId);

    @Query("SELECT m FROM Message m WHERE (m.sender.id = :userId OR m.receiver.id = :userId) " +
           "AND m.createdAt = (SELECT MAX(m2.createdAt) FROM Message m2 " +
           "WHERE (m2.sender.id = m.sender.id AND m2.receiver.id = m.receiver.id) " +
           "OR (m2.sender.id = m.receiver.id AND m2.receiver.id = m.sender.id)) " +
           "ORDER BY m.createdAt DESC")
    Page<Message> findConversations(@Param("userId") String userId, Pageable pageable);

}
