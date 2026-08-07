package com.example.solimus.dtos.tenant.accueil;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// DTO du dashboard d'accueil du locataire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantDashboardDTO {

    private String firstName;

    private String propertyReference; // "B-204"

    private String residenceName; // "Résidence Les Acacias"

    private boolean bailActif; // true tant que property.tenant = ce locataire

    private int pendingReportsCount;

    private int inProgressReportsCount;

    private int resolvedReportsCount;

    private List<TenantTravauxSummaryDTO> travauxEnCours; // limité, 3 max
}
