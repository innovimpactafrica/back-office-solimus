package com.example.solimus.dtos.admin.dashboard;

import lombok.*;

// ===== DTO — Bloc 4 : répartition des utilisateurs (graphique "Utilisateurs") =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBreakdownDTO {

    private long totalUsers;
    private RoleBreakdownDTO syndics;
    private RoleBreakdownDTO providers;
    private RoleBreakdownDTO coOwners;
}
