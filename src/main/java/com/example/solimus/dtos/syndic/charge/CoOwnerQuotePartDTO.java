package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

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
    private String typeBienName;
    private BigDecimal totalTantieme;
    private BigDecimal quotePartAnnuelle;
    private BigDecimal quotePartPeriode;
}
