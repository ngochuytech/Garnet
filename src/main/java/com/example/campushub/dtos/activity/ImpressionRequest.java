package com.example.campushub.dtos.activity;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpressionRequest {
    @NotEmpty(message = "events must contain at least one event")
    private List<@Valid ImpressionEventRequest> events;
}
