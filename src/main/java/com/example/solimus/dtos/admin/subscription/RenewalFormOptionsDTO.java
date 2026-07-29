package com.example.solimus.dtos.admin.subscription;

import lombok.*;

import java.util.List;

// ===== DTO — options du formulaire "Renouveler l'abonnement" (menus déroulants Formule et Durée) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RenewalFormOptionsDTO {

    private List<PlanOption> plans;
    private List<DurationOption> durations;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanOption {
        private Long id;
        private String label;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DurationOption {
        // Nom brut de l'enum (ex: "MONTHLY") 
        private String value;
        // Libellé humain (ex: "12 mois")
        private String label;
    }
}