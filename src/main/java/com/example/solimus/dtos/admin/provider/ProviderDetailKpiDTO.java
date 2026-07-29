package com.example.solimus.dtos.admin.provider;

// ===== DTO — Bloc B "KPIs" de la fiche détail prestataire (Admin) =====
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDetailKpiDTO {

    private long totalMissionsReceived;
    private long quotesSent;
    // Missions FINISHED + FINAL_VALIDATION (même définition que côté résidence)
    private long missionsCompleted;

    // Moyenne en minutes entre le début et la fin des interventions terminées — null si aucune
    // intervention terminée n'a encore de started_at/finished_at renseignés
    private Double averageInterventionMinutes;

    // Note moyenne (0.0 - 5.0) — valeur mise en cache sur ProviderProfile, déjà tenue à jour à
    // chaque nouvel avis reçu
    private Double rating;
    private Long reviewCount;
}
