package com.example.campushub.services;

import org.springframework.stereotype.Service;

import com.example.campushub.dtos.users.CreateReportPostDTO;
import com.example.campushub.enums.ReportStatus;
import com.example.campushub.enums.ReportType;
import com.example.campushub.exceptions.DataNotFoundException;
import com.example.campushub.exceptions.InvalidParamException;
import com.example.campushub.models.jpa.Post;
import com.example.campushub.models.jpa.Report;
import com.example.campushub.models.jpa.User;
import com.example.campushub.repositories.jpa.PostRepository;
import com.example.campushub.repositories.jpa.ReportRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ReportRepository reportRepository;
    private final PostRepository postRepository;

    private ReportType parseAndValidateTargetType(String targetType){
        try {
            return ReportType.valueOf(targetType.toUpperCase());
        } catch (InvalidParamException e) {
            throw new InvalidParamException("Tham số target type không hợp lệ" + targetType);
        }
    }

    public void createReportPost(User reporter, CreateReportPostDTO dto) throws Exception{
        ReportType type = parseAndValidateTargetType(dto.getTargetType());
        if(type != ReportType.POST)
            throw new InvalidParamException("Báo cáo không hợp lệ!");
        Post post = postRepository.findById(dto.getTargetId())
            .orElseThrow(() -> new DataNotFoundException("Không tìm thấy bài viết cần báo cáo!"));
        
        Report report = Report.builder()
                .reporter(reporter)
                .targetType(type)
                .targetId(post.getId())
                .reportedUser(post.getUser())
                .reason(dto.getReason())
                .description(dto.getDescription())
                .reportedContentSnapshot(post.getContent())
                .status(ReportStatus.OPEN)
                .build();
        
        reportRepository.save(report);
    }

}
