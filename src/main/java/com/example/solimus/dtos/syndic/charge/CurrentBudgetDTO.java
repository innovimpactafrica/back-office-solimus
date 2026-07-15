package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CurrentBudgetDTO {
    private Long id;
    private Integer annee;
    private String residenceName;
}
