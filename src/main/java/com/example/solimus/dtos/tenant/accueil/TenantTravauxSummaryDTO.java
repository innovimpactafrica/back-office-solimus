package com.example.solimus.dtos.tenant.accueil;

import com.example.solimus.enums.InterventionStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO d'une demande de travaux résumée pour le dashboard d'accueil du locataire
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantTravauxSummaryDTO {

    private Long id;

    private String title;

    private String specialtyName; // "Plomberie"

    private String specialtyIcon;

    private String statusLabel; // "En cours" ou "Planifié"

    private InterventionStatus status;
}
