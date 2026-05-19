package com.example.solimus.dtos.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLineDTO {
    private String description;   // "Tuyau PVC Ø32mm"
    private String detail;        // "3 × 2 500 FCFA"
    private BigDecimal montant;   // 7 500
}
