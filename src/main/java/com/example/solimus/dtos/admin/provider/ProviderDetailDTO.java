package com.example.solimus.dtos.admin.provider;

import lombok.*;

import java.util.List;

// ===== DTO — Fiche détail complète d'un prestataire (Admin) : Blocs A à E =====
// Le Bloc F (historique d'activité) est servi par un endpoint séparé (liste indépendante, comme
// pour les activités récentes du syndic).
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderDetailDTO {

    private Long userId;
    private ProviderHeaderDTO header;
    private ProviderDetailKpiDTO kpis;
    private ProviderGeneralInfoDTO generalInfo;
    // Bloc D — une seule zone aujourd'hui (champ texte unique rempli par autocomplétion à
    // l'inscription), renvoyée dans une liste pour rester extensible si plusieurs zones sont
    // permises un jour
    private List<String> interventionZones;
    private ProviderSubscriptionInfoDTO subscription;
}
