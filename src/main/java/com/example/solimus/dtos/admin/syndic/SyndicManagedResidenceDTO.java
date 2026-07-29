package com.example.solimus.dtos.admin.syndic;

import lombok.*;

// ===== DTO — Bloc D : une résidence gérée par le syndic (liste paginée, fiche détail syndic Admin) =====
// La numérotation "N°1, N°2..." affichée à l'écran est calculée côté front à partir de la position
// dans la page (page x taille + index), ce n'est pas une donnée métier — pas de champ dédié ici.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicManagedResidenceDTO {

    private Long id;
    private String name;
    private String city;
    private long lotsCount;
    private long coOwnersCount;

    // Nombre de catégories d'alerte déclenchées sur cette résidence (AG à venir / paiements en
    // retard / intervention urgente), 0 à 3 — voir SyndicServiceImpl.countResidenceAlerts
    private int alertsCount;
    // true si alertsCount == 0
    private boolean upToDate;
}
