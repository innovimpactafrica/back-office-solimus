package com.example.solimus.dtos.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstimatedDelayDTO {
    private Long id;
    private String label;
    private Integer days;
}
