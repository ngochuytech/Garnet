package com.example.campushub.controllers.users;

import com.example.campushub.dtos.users.CreateGroupDTO;
import com.example.campushub.dtos.users.CreateReportGroupDTO;
import com.example.campushub.dtos.users.UpdateGroupDescriptionDTO;
import com.example.campushub.dtos.users.UpdateGroupNameDTO;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.services.GroupService;
import com.example.campushub.services.PostService;
import com.example.campushub.services.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/users/groups")
@RequiredArgsConstructor
public class UserGroupController {
    
    private final GroupService groupService;
    private final PostService postService;
    private final ReportService reportService;

    @GetMapping("")
    public ResponseEntity<?> getAllGroups(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getAllGroups(user)));
    }

    @GetMapping("/{groupId}")
    public ResponseEntity<?> getGroupById(@AuthenticationPrincipal User user,
                                          @PathVariable String groupId) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroupById(user, groupId)));
    }

    @GetMapping("/{groupId}/status")
    public ResponseEntity<?> getGroupStatus(@PathVariable String groupId) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroupStatus(groupId)));
    }

    @GetMapping("/{groupId}/posts")
    public ResponseEntity<?> getPostsByGroup(@AuthenticationPrincipal User user,
                                             @PathVariable String groupId,
                                             @RequestParam(defaultValue = "20") int size,
                                             @RequestParam(required = false) String cursor) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(postService.getPostsByGroupId(groupId, size, cursor, user)));
    }

    @GetMapping("/{groupId}/members")
    public ResponseEntity<?> getGroupMembers(@PathVariable String groupId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size) throws Exception {
        Pageable pageable = PageRequest.of(page, size, Sort.by("joinedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(groupService.getGroupMembers(groupId, pageable))));
    }

    @GetMapping("/{groupId}/pending-members")
    public ResponseEntity<?> getPendingGroupMembers(@AuthenticationPrincipal User currentUser,
                                                    @PathVariable String groupId,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) throws Exception {
        Pageable pageable = PageRequest.of(page, size, Sort.by("joinedAt").descending());
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(groupService.getPendingGroupMembers(currentUser, groupId, pageable))));
    }

    @PostMapping("")
    public ResponseEntity<?> createGroup(@AuthenticationPrincipal User user,
                                         @RequestBody @Valid CreateGroupDTO dto) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.createGroup(user, dto)));
    }

    @PostMapping("/{groupId}/join")
    public ResponseEntity<?> joinGroup(@AuthenticationPrincipal User user,
                                       @PathVariable String groupId) throws Exception {
        groupService.joinGroup(user, groupId);
        return ResponseEntity.ok(ApiResponse.ok("Yêu cầu tham gia nhóm thành công, vui lòng chờ duyệt."));
    }

    @PostMapping("/{groupId}/report")
    public ResponseEntity<?> reportGroup(@AuthenticationPrincipal User user,
                                         @PathVariable String groupId,
                                         @RequestBody @Valid CreateReportGroupDTO dto) throws Exception {
        reportService.createReportGroup(user, groupId, dto);
        return ResponseEntity.ok(ApiResponse.ok("Đã gửi báo cáo nhóm đến quản trị viên."));
    }

    @PutMapping("/{groupId}/name")
    public ResponseEntity<?> updateGroupName(@AuthenticationPrincipal User currentUser,
                                             @PathVariable String groupId,
                                             @RequestBody @Valid UpdateGroupNameDTO dto) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.updateGroupName(currentUser, groupId, dto.getName())));
    }

    @PutMapping("/{groupId}/description")
    public ResponseEntity<?> updateGroupDescription(@AuthenticationPrincipal User currentUser,
                                                   @PathVariable String groupId,
                                                   @RequestBody UpdateGroupDescriptionDTO dto) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.updateGroupDescription(currentUser, groupId, dto.getDescription())));
    }

    @PostMapping("/{groupId}/avatar")
    public ResponseEntity<?> updateGroupAvatar(@AuthenticationPrincipal User currentUser,
                                               @PathVariable String groupId,
                                               @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.updateGroupAvatar(currentUser, groupId, file)));
    }

    @PostMapping("/{groupId}/cover")
    public ResponseEntity<?> updateGroupCover(@AuthenticationPrincipal User currentUser,
                                              @PathVariable String groupId,
                                              @RequestParam("file") MultipartFile file) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.updateGroupCover(currentUser, groupId, file)));
    }

    @PostMapping("/{groupId}/approve/{targetUserId}")
    public ResponseEntity<?> approveJoinRequest(@AuthenticationPrincipal User currentUser,
                                                @PathVariable String groupId,
                                                @PathVariable String targetUserId) throws Exception {
        groupService.approveJoinRequest(currentUser, groupId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok("Duyệt yêu cầu tham gia nhóm thành công."));
    }

    @PostMapping("/{groupId}/reject/{targetUserId}")
    public ResponseEntity<?> rejectJoinRequest(@AuthenticationPrincipal User currentUser,
                                               @PathVariable String groupId,
                                               @PathVariable String targetUserId) throws Exception {
        groupService.rejectJoinRequest(currentUser, groupId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok("Đã từ chối yêu cầu tham gia nhóm."));
    }

    @DeleteMapping("/{groupId}/members/{targetUserId}")
    public ResponseEntity<?> kickMember(@AuthenticationPrincipal User currentUser,
                                        @PathVariable String groupId,
                                        @PathVariable String targetUserId) throws Exception {
        groupService.kickMember(currentUser, groupId, targetUserId);
        return ResponseEntity.ok(ApiResponse.ok("Đã đuổi thành viên khỏi nhóm."));
    }

    @DeleteMapping("/{groupId}/leave")
    public ResponseEntity<?> leaveGroup(@AuthenticationPrincipal User currentUser,
                                        @PathVariable String groupId) throws Exception {
        groupService.leaveGroup(currentUser, groupId);
        return ResponseEntity.ok(ApiResponse.ok("Đã rời khỏi nhóm thành công."));
    }

    @DeleteMapping("/{groupId}")
    public ResponseEntity<?> deleteGroup(@AuthenticationPrincipal User currentUser,
                                         @PathVariable String groupId) throws Exception {
        groupService.deleteGroup(currentUser, groupId);
        return ResponseEntity.ok(ApiResponse.ok("Đã xóa nhóm thành công."));
    }
}
