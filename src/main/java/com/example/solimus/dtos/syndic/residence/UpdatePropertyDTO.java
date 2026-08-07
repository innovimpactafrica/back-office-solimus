package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour modifier les informations de base d'un bien d'une résidence
 * Tous les champs sont optionnels pour une mise à jour partielle
 * Pas de champ "share" (tantième) : recalculé automatiquement côté serveur.
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

    private BigDecimal area;
}