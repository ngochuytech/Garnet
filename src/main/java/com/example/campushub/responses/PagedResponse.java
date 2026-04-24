package com.example.campushub.responses;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Slice;

public record PagedResponse<T>(
        List<T> items,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast());
    }

    public static <T> PagedResponse<T> from(Slice<T> slice) {
        return new PagedResponse<>(
                slice.getContent(),
                slice.getNumber(),
                slice.getSize(),
                -1, // Slice không hỗ trợ đếm tổng số phần tử (totalElements)
                -1, // Slice không hỗ trợ đếm tổng số trang (totalPages)
                !slice.hasNext()); // isLast = Không còn trang tiếp theo
    }
}
