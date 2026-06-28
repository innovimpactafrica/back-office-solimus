package com.example.solimus.dtos.provider.request;

import com.example.solimus.enums.QuoteItemType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO représentant une ligne de devis (matériel ou main d'œuvre)
@Data
@AllArgsConstructor
@NoArgsConstructor
public class QuoteItemDTO {
    private String description;
    private Integer quantity;
    private BigDecimal unitPrice; // Prix unitaire
    private QuoteItemType type; // MATERIAL ou LABOR
}
