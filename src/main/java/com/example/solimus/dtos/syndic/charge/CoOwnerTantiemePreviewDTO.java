package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Aperçu d'un copropriétaire avec ses tantièmes pour le budget.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerTantiemePreviewDTO {

    private Long coOwnerId;
    private String coOwnerName;
    private BigDecimal tantieme;       // Ex: 12.5
    private String propertyReference; // Ex: "A-101"
}
