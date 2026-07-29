package com.example.solimus.dtos.admin.provider;

import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Une ligne du Bloc F "Historique d'activité" (Admin, fiche détail prestataire) =====
// Flux assemblé à la volée à partir de plusieurs sources (aucun log d'activité dédié côté
// prestataire, contrairement au syndic) : voir ProviderServiceImpl.getProviderRecentActivities
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderActivityRowDTO {

    private String title;
    private String description;
    private LocalDateTime occurredAt;
    private String relativeTime;
}
