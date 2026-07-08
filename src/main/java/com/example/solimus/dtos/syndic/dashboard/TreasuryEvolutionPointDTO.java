package com.example.solimus.dtos.syndic.dashboard;


import lombok.Data;
import java.math.BigDecimal;

//DTO d'un point du graphique "Évolution Financière" (Trésorerie vs Appels de charges)
@Data
public class TreasuryEvolutionPointDTO {
    private String monthLabel; // Ex: "Jan", "Fév"
    private BigDecimal treasury; // Solde cumulé du wallet à la fin de ce mois
    private BigDecimal chargeCallsCumulated; // Cumul des ChargeCall.totalAmount jusqu'à ce mois
}