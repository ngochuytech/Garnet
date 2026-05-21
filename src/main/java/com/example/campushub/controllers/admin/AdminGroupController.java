package com.example.campushub.controllers.admin;

import com.example.campushub.dtos.admin.AdminGroupReportDTO;
import com.example.campushub.models.jpa.User;
import com.example.campushub.responses.ApiResponse;
import com.example.campushub.responses.PagedResponse;
import com.example.campushub.services.GroupService;
import com.example.campushub.services.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/groups")
@RequiredArgsConstructor
public class AdminGroupController {
    private final GroupService groupService;
    private final ReportService reportService;

    @GetMapping("")
    public ResponseEntity<?> getGroups(@AuthenticationPrincipal User currentUser,
                                       @RequestParam(required = false) String query,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size,
                                       @RequestParam(defaultValue = "createdAt") String sortBy,
                                       @RequestParam(defaultValue = "desc") String sortDir) {
        Sort sort = sortDir.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ResponseEntity.ok(ApiResponse.ok(PagedResponse.from(groupService.getAdminGroups(query, status, pageable))));
    }

    @PutMapping("/{groupId}/lock")
    public ResponseEntity<?> lockGroup(@AuthenticationPrincipal User currentUser,
                                       @PathVariable String groupId) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.adminLockGroup(currentUser, groupId)));
    }

    @PutMapping("/{groupId}/unlock")
    public ResponseEntity<?> unlockGroup(@AuthenticationPrincipal User currentUser,
                                         @PathVariable String groupId) throws Exception {
        return ResponseEntity.ok(ApiResponse.ok(groupService.adminUnlockGroup(currentUser, groupId)));
    }

    @PostMapping("/{groupId}/report")
    public ResponseEntity<?> reportGroup(@AuthenticationPrincipal User currentUser,
                                         @PathVariable String groupId,
                                         @RequestBody @Valid AdminGroupReportDTO dto) throws Exception {
        reportService.reportGroupByAdmin(currentUser, groupId, dto);
        return ResponseEntity.ok(ApiResponse.ok("Đã ghi nhận xử lý vi phạm nhóm"));
    }
}
