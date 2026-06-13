package com.example.campushub.responses;

import java.util.List;

public record CursorPagedResponse<T>(
        List<T> items,
        int pageSize,
        String nextCursor,
        boolean hasNext) {
}
