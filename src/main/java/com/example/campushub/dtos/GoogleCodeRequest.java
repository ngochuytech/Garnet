package com.example.campushub.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class GoogleCodeRequest {
    @JsonProperty("code")
    private String code;

    @JsonProperty("redirectUri")
    private String redirectUri;
}
