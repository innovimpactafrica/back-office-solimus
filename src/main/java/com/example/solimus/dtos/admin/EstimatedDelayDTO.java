package com.example.solimus.dtos.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO représentant un délai d'estimation de durée d'intervention (ex: "1-2 jours", "1 semaine")
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EstimatedDelayDTO {
    private Long id;
    private String label;
    private Integer days;
}
