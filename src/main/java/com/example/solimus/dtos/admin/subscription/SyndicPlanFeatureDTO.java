package com.example.solimus.dtos.admin.subscription;

import lombok.*;

// ===== DTO LIGNE - UNE FONCTIONNALITÉ D'UNE FORMULE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicPlanFeatureDTO {

    private String value;  // valeur technique (ex: "AG_MANAGEMENT")
    private String label;  // libellé affichable (ex: "Gestion des AG")
}