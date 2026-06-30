package com.example.solimus.dtos.provider.profile;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

// DTO pour une ligne de devis (matériel ou main d'œuvre)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteLineDTO {
    private String description;
    private Integer quantity; // Quantité (matériel) ou heures (main d'œuvre)
    private BigDecimal unitPrice; // Prix unitaire
    private BigDecimal subtotal; // Sous-total = quantity × unitPrice
}
