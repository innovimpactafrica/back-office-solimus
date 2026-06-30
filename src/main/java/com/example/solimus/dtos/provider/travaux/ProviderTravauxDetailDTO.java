package com.example.solimus.dtos.provider.travaux;


import com.example.solimus.dtos.provider.request.WorkflowStepDTO;
import com.example.solimus.enums.InterventionStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

//DTO représentant les détails d'une demande de travail assigné à un prestataire
@Data
@Builder
public class ProviderTravauxDetailDTO {

    private Long id;

    private String title; // "Fuite d'eau salle de bain"

    private String residenceName; // "Résidence Diana"

    // Statut global de l'intervention
    private InterventionStatus status;

    private String statusLabel; // "Accepté"

    // texte complet du problème
    private String description;

    // date affichée sous la description
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDateTime createdAt;

    // Photos du problème initial
    private List<String> photoUrls;

    // Contact résident (selon managementMode)
    private String contactPhone;

    private String contactEmail;

    // Les 6 étapes du workflow, avec leur état et leur date
    private List<WorkflowStepDTO> workflowSteps;
}
