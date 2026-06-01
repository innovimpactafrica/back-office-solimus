package com.example.solimus.dtos.charge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO pour créer une ligne de charge
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateChargeLineDTO {
    private String label;       // "Entretien parties communes"
    private BigDecimal amount;  // 85 000
}
