package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.QuoteItemType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuoteItemDTO {
    private String description;
    private Integer quantity; // Heures ou Unités
    private BigDecimal unitPrice; // Taux horaire ou Prix unitaire
    private QuoteItemType type; // MATERIAL ou LABOR
}
