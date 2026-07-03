package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * DTO pour un lot dans la liste paginée de l'onglet Appartements
 * Contient les informations affichées sur chaque ligne de lot
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PropertyListItemDTO {
    // Identifiant du lot
    private Long id;

    // Référence du lot (ex: "A01")
    private String reference;

    // Type de bien (ex: "T2")
    private String propertyType;

    // Étage (0 = RDC, conversion d'affichage côté front)
    private Integer floor;

    // Propriétaire du lot (null si vacant)
    private OwnerInfo owner;

    // Statut composite calculé à la volée (MAINTENANCE, OVERDUE, OCCUPE, VACANT)
    private String status;

    // Charge calculée pour ce lot (répartition théorique par lot)
    private BigDecimal charge;

    /**
     * DTO imbriqué pour les informations du propriétaire
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OwnerInfo {
        private String fullName;
        private String photoUrl;
    }
}
