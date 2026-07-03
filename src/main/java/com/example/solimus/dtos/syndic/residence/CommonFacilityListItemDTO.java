package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * DTO pour un équipement commun dans la liste de l'onglet Biens communs
 * Contient les informations affichées sur chaque carte d'équipement
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommonFacilityListItemDTO {
    // Identifiant de l'équipement
    private Long id;

    // Nom de l'équipement (vient de FacilityType)
    private String name;

    // Icône de l'équipement (URL MinIO, vient de FacilityType)
    private String icon;

    // Statut composite calculé à la volée (FUNCTIONAL / MAINTENANCE)
    private String status;

    // Date de la dernière maintenance terminée sur cet équipement
    private LocalDate lastMaintenanceDate;
}
