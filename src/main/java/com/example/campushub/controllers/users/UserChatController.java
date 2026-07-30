package com.example.campushub.controllers.users;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.ConversationResponse;
import com.example.campushub.responses.MessageResponse;
import com.example.campushub.services.ChatService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/users/chat")
@RequiredArgsConstructor
public class UserChatController {
    private final ChatService chatService;

    @GetMapping("/conversations")
    public ResponseEntity<?> getConversations(
        @AuthenticationPrincipal User user,
        Pageable pageable){
        Page<ConversationResponse> conversations = chatService.getConversations(user, pageable);
        return ResponseEntity.ok(ApiResponse.ok(conversations));
    }

    @GetMapping("/history/{otherUserId}")
    public ResponseEntity<?> getChatHistory(
            @AuthenticationPrincipal User user, 
            @PathVariable String otherUserId, 
            Pageable pageable) {
        Page<MessageResponse> history = chatService.getChatHistory(user, otherUserId, pageable);
        return ResponseEntity.ok(ApiResponse.ok(history)); 
    }

    @PutMapping("/read/{otherUserId}")
    public ResponseEntity<?> markAsRead(
            @AuthenticationPrincipal User user, 
            @PathVariable String otherUserId) throws Exception {
        chatService.markAsRead(user, otherUserId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đánh dấu xem tin nhắn"));
    }

    @PostMapping("/send/{otherUserId}")
    public ResponseEntity<?> sendMessage(
            @AuthenticationPrincipal User user, 
            @PathVariable String otherUserId, 
            @RequestBody String content) throws Exception {
        chatService.sendMessaging(user.getId(), otherUserId, content);
        return ResponseEntity.ok(ApiResponse.ok("Gửi tin nhắn thành công"));
    }
}
