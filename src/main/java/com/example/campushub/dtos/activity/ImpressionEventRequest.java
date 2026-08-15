package com.example.campushub.dtos.activity;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImpressionEventRequest {
    @NotBlank(message = "event_type is required")
    @Pattern(regexp = "POST_(IMPRESSION|OPEN|DWELL)",
            message = "event_type must be POST_IMPRESSION, POST_OPEN, or POST_DWELL")
    @JsonProperty("event_type")
    private String eventType;

    @NotBlank(message = "target_type is required")
    @Pattern(regexp = "POST", message = "target_type must be POST")
    @JsonProperty("target_type")
    private String targetType;

    @NotBlank(message = "target_id is required")
    @JsonProperty("target_id")
    private String targetId;
    
    @JsonProperty("occurred_at")
    private String occurredAt;
}
