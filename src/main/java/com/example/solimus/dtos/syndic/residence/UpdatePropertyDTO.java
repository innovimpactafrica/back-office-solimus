package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour modifier un bien d'une résidence
 * Tous les champs sont optionnels pour une mise à jour partielle
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UpdatePropertyDTO {

    private String reference;

    private String bloc;

    private Integer floor;

    private Long propertyTypeId;

    private BigDecimal superficie;

    private BigDecimal tantieme;

    private Long ownerId;
}
