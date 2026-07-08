package com.example.solimus.dtos.syndic.residence;

import com.example.solimus.enums.FacilityCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO pour le détail d'un bien commun
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommonFacilityDetailDTO {
    // ID du bien
    private Long id;

    // Nom du bien (vient de FacilityType)
    private String name;

    // Icône du bien (vient de FacilityType)
    private String icon;

    // Catégorie du bien (vient de FacilityType)
    private FacilityCategory category;

    // Description du bien (vient de FacilityType)
    private String description;

    // Nom de la résidence
    private String residenceName;

    // Ville de la résidence
    private String city;

    // Statut du bien (MAINTENANCE ou FONCTIONNEL)
    private String status;

    // Date de la dernière maintenance
    private LocalDate lastMaintenanceDate;

    // Dans CommonFacilityDetailDTO
    private BigDecimal budgetAmount; // Montant du poste budgétaire lié à cet équipement (le plus récent, null si aucun)

    // Historique des interventions (4 plus récentes)
    private List<InterventionHistoryItemDTO> interventionHistory;
}
