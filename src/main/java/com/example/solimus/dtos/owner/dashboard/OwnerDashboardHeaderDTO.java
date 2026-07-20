package com.example.solimus.dtos.owner.dashboard;

import lombok.*;

// ===== DTO EN-TÊTE - DASHBOARD ACCUEIL COPROPRIÉTAIRE =====
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerDashboardHeaderDTO {

    private String firstName;
    private String photoUrl;
    private long unreadNotificationsCount;
}
