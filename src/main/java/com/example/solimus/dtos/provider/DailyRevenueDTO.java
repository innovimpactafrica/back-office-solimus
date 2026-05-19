package com.example.solimus.dtos.provider;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

/**
 * DTO représentant le gain financier cumulé pour un jour donné.
 * Utilisé pour alimenter le graphique de performance hebdomadaire sur le tableau de bord mobile.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRevenueDTO {
    
    // Label du jour abrégé (ex: "Lun", "Mar", etc.)
    private String jour;
    
    // Montant total des revenus encaissés ce jour
    private BigDecimal montant;
}
