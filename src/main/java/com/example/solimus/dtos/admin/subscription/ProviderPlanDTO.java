package com.example.solimus.dtos.admin.subscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO retourné pour afficher la formule prestataire actuelle.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProviderPlanDTO {

    private Long id;
    private String name;
    private String description;
    private BigDecimal monthlyPrice;
    private BigDecimal yearlyPrice;
    private LocalDateTime updatedAt;
}