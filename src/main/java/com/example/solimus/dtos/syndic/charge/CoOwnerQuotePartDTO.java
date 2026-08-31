package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Une ligne de répartition (un copropriétaire).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerQuotePartDTO {

    private Long coOwnerId;
    private String coOwnerName;
    private List<String> typeBienNames;
    private BigDecimal totalTantieme;
    private BigDecimal quotePartAnnuelle;
    private BigDecimal quotePartPeriode; // = quotePartParPeriode.get(0), gardé pour compat ascendante

    // Vraie valeur de CHAQUE période (index 0 = période 1, etc.) — dérivée de quotePartAnnuelle,
    // jamais un quotePartPeriode répété identique sur toutes les colonnes (les périodes peuvent
    // légitimement différer d'1 FCFA entre elles, cf. ChargeAllocationUtil.distributeByLargestRemainder)
    private List<BigDecimal> quotePartParPeriode;
}
