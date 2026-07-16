package com.example.solimus.dtos.syndic.finance;

import com.example.solimus.dtos.syndic.dashboard.TreasuryEvolutionPointDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

//DTO principal du dashboard "Finances"
@Data
public class FinanceDashboardDTO {
    private BigDecimal treasuryGlobal; // Solde actuel du wallet syndic (toutes résidences)
    private Double treasuryEvolutionPercent; // Évolution vs le mois dernier
    private BigDecimal chargesCollected; // Charges collectées sur le trimestre calendaire en cours
    private BigDecimal unpaidAmount; // Montant total impayé
    private Double unpaidPercentOfTotal; // % impayés / (charges collectées + impayés)
    private BigDecimal expenses; // Dépenses TRAVAUX du mois en cours
    private List<TreasuryEvolutionPointDTO> treasuryEvolution; // Graphique cumulatif Trésorerie vs Appels de charges
}
