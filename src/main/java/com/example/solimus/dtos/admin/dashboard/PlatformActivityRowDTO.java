package com.example.solimus.dtos.admin.dashboard;

import lombok.*;

import java.time.LocalDateTime;

// ===== DTO — Bloc 3 : une ligne du flux "Activité récente" du dashboard admin =====
// Flux assemblé à la volée à partir de plusieurs sources (nouveaux syndics, prestataires inscrits,
// renouvellements, paiements, nouvelles résidences, nouveaux copropriétaires) — voir
// AdminDashboardServiceImpl.getRecentActivities. "type" sert au Front à choisir l'icône affichée.
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformActivityRowDTO {

    private String type;
    private String title;
    private String description;
    private LocalDateTime createdAt;
    private String timeAgo;
}
