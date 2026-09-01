package com.example.solimus.dtos.syndic.charge;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

//DTO d'une ligne de répartition d'un budget entre copropriétaires
@Data
public class BudgetRepartitionItemDTO {
    private String coOwnerName; // Nom complet du copropriétaire
    private String properties; // Ses appartements, séparés par virgules
    private BigDecimal tantieme; // Son tantième total dans cette résidence
    private BigDecimal quotePart; // Sa part du budget total (annuelle), calculée via son tantième

    // Vraie valeur de CHAQUE période (index 0 = période 1/T1, etc.) — dérivée de quotePart (l'annuelle),
    // jamais un montant de période identique répété sur toutes les colonnes. Même logique que
    // CoOwnerQuotePartDTO.quotePartParPeriode (ChargeServiceImpl.splitAnnualAmountForPeriod)
    private List<BigDecimal> quotePartParPeriode;
}
