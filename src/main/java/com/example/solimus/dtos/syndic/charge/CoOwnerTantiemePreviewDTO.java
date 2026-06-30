package com.example.solimus.dtos.syndic.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

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
    private BigDecimal tantieme;
    private List<String> propertyReferences; // Ex: ["A-101", "B-205"]
}
