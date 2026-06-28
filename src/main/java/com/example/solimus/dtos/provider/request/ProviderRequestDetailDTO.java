package com.example.solimus.dtos.provider.request;

import com.example.solimus.enums.ProviderRequestDisplayStatus;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

//DTO représentant les détails d'une demande de travail pour un prestataire
@Data
@Builder
public class ProviderRequestDetailDTO {

    private Long id;

    private String title; // "Fuite d'eau salle de bain"

    private String residenceName; // "Résidence Diana"

    // Statut affiché, calculé spécifiquement pour CE prestataire
    // (même logique que pour la liste — REJECTED, QUOTE_SENT, PENDING_QUOTE)
    private ProviderRequestDisplayStatus status;

    private String statusLabel; // "En attente devis"

    // texte complet du problème
    private String description;

    // date affichée sous la description
    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDateTime createdAt;

    // Photos du problème initial
    private List<String> photoUrls;

    // Contact résident
    private String contactPhone;

    private String contactEmail;

    // Les 6 étapes du workflow, avec leur état et leur date
    private List<WorkflowStepDTO> workflowSteps;

}
