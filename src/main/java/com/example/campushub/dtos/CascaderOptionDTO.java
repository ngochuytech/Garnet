package com.example.campushub.dtos;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CascaderOptionDTO {
    private String value;
    private String label;
    private List<CascaderOptionDTO> children;
}
