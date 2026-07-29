package com.example.solimus.dtos.admin.dashboard;

import lombok.*;

// ===== DTO — Nombre + pourcentage d'un rôle, pour le graphique "Utilisateurs" (Bloc 4) =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleBreakdownDTO {

    private long count;
    private double percentage;
}
