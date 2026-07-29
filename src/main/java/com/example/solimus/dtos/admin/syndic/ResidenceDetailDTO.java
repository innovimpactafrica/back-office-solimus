package com.example.solimus.dtos.admin.syndic;

import lombok.*;

// ===== DTO — Fiche détail d'une résidence gérée par un syndic (Bloc D-a, Admin) =====
// Regroupe Informations Générales + KPIs, affichés ensemble en haut de l'écran de détail résidence
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResidenceDetailDTO {

    private ResidenceGeneralInfoDTO generalInfo;
    private ResidenceKpiDTO kpis;
}
