package com.example.solimus.dtos.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO pour une ligne de charge
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeLineDTO {
    private String label;
    private BigDecimal amount;
}
