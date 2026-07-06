package com.example.solimus.dtos.syndic.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

//DTO pour un item de répartition des charges (donut)
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChargeBreakdownItemDTO {

    private String label;
    private Double percentage;
    private BigDecimal amount;
}
