package com.example.campushub.services;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.campushub.exceptions.ResourceNotFoundException;
import com.example.campushub.models.jpa.Message;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.MessageRepository;
import com.example.campushub.repositories.jpa.UserRepository;
import com.example.campushub.responses.ConversationResponse;
import com.example.campushub.responses.MessageResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public void sendMessaging(String senderId, String receiverId, String content) throws Exception {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người gửi"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người nhận"));

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content)
                .isRead(false)
                .build();
        messageRepository.save(message);

        simpMessagingTemplate.convertAndSendToUser(
                receiver.getUsername(),
                "/queue/message",
                MessageResponse.fromEntity(message));
    }

    public Page<MessageResponse> getChatHistory(User currentUser, String otherUserId, Pageable pageable) {
        Page<Message> messages = messageRepository.findChatHistory(currentUser.getId(), otherUserId, pageable);
        if (messages.isEmpty()) {
            return Page.empty();
        }
        return messages.map(MessageResponse::fromEntity);
    }

    @Transactional("transactionManager")
    public void markAsRead(User currentUser, String otherUserId) throws Exception {
        User otherUser = userRepository.findById(otherUserId)
            .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng"));
        List<Message> unreadMessages = messageRepository.findUnreadMessages(otherUserId, currentUser.getId());
        if(unreadMessages.isEmpty())
            return;
        for (Message message : unreadMessages) {
            message.setIsRead(true);
        }
        messageRepository.saveAll(unreadMessages);

        simpMessagingTemplate.convertAndSendToUser(
            otherUser.getUsername(), "/queue/chat.read", currentUser.getId());
    }

    public Page<ConversationResponse> getConversations(User currentUser, Pageable pageable){
        Page<Message> conversations = messageRepository.findConversations(currentUser.getId(), pageable);
        if (conversations.isEmpty()) {
            return Page.empty();
        }
        return conversations.map(msg -> ConversationResponse.fromMessage(msg, currentUser.getId()));
    }
}
