package com.example.solimus.dtos.admin.finance;

import com.example.solimus.enums.SubscriberType;
import lombok.*;

import java.math.BigDecimal;

// ===== DTO — Bloc 4 : revenu cumulé total (depuis toujours) d'une formule précise =====
// Liste plate mêlant formules Syndic (SyndicPlan) et Prestataire (ProviderPlan) — planType permet
// au Front de distinguer/filtrer sans que le Back ait à séparer la réponse en deux structures.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanRevenueDTO {

    private String planName;
    private SubscriberType planType;
    // Cumul total (jamais restreint à une période) des paiements validés sur cette formule
    private BigDecimal amount;
    // Abonnés actuellement actifs sur cette formule (statut ACTIVE, non expirés)
    private long subscriberCount;
}
