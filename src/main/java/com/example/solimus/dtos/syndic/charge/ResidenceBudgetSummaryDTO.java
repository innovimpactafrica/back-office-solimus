package com.example.solimus.dtos.syndic.charge;

import com.example.solimus.enums.ChargeFrequency;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidenceBudgetSummaryDTO {
    private Long id;
    private String residenceName;
    private String referenceBudget;
    private Integer anneeBudget;
    private List<Integer> availablePeriods;
    private ChargeFrequency frequency;
}
