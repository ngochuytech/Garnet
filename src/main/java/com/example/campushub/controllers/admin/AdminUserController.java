package com.example.campushub.controllers.admin;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.responses.admin.AdminUserResponse;
import com.example.campushub.services.CommentService;
import com.example.campushub.services.PostService;
import com.example.campushub.services.ReportService;
import com.example.campushub.services.UserService;

import lombok.RequiredArgsConstructor;

@RequestMapping("/admin/users")
@RestController
@RequiredArgsConstructor
public class AdminUserController {
    private final UserService userService;
    private final PostService postService;
    private final CommentService commentService;
    private final ReportService reportService;

    @GetMapping("")
    public ResponseEntity<?> getUsers(@AuthenticationPrincipal User currentUser,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(PagedResponse.from(userService.getUsers(query, status, pageable)));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<?> getUserDetail(@AuthenticationPrincipal User currentUser, @PathVariable String userId)
            throws Exception {
        User user = userService.getUserById(userId);
        return ResponseEntity.ok(ApiResponse.ok(AdminUserResponse.fromEntity(user)));
    }

    @GetMapping("/{userId}/posts")
    public ResponseEntity<?> getPostsByUserId(@AuthenticationPrincipal User currentUser,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(postService.getPostsByUserId(userId, pageable))));
    }

    @GetMapping("/{userId}/comments")
    public ResponseEntity<?> getCommentByUserId(@AuthenticationPrincipal User currentUser,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(commentService.getCommentsByUserId(userId, pageable))));
    }

    @GetMapping("/{userId}/reports")
    public ResponseEntity<?> getReportsByUserId(@AuthenticationPrincipal User currentUser,
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir) throws Exception {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(reportService.getReportsByUserId(userId, pageable))));
    }

    @PutMapping("/{userId}/ban")
    public ResponseEntity<?> banUser(@AuthenticationPrincipal User currentUser, @PathVariable String userId)
            throws Exception {
        userService.banUser(currentUser, userId);
        return ResponseEntity.ok(ApiResponse.ok("Đã ban người dùng thành công!"));
    }

    @PutMapping("/{userId}/unban")
    public ResponseEntity<?> unbanUser(@AuthenticationPrincipal User currentUser, @PathVariable String userId)
            throws Exception {
        userService.unbanUser(currentUser, userId);
        return ResponseEntity.ok(ApiResponse.ok("Đã bỏ ban người dùng thành công!"));
    }
}
