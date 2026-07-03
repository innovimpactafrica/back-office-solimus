package com.example.solimus.dtos.syndic.residence;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO pour une carte du Kanban d'interventions
 * Champs spécifiques à chaque colonne sont null si non pertinents
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterventionKanbanCardDTO {
    // Champs communs à toutes les colonnes
    private Long id;
    private String reference;
    private String title;
    private String urgencyLevel;
    private String status;
    private Integer commentsCount;

    // Champs spécifiques à la colonne "reported" (Signalé)
    private LocalDateTime reportedAt;
    private UserInfoDTO reportedBy;

    // Champs spécifiques à la colonne "inProgress" (En cours)
    private LocalDateTime startedAt;
    private UserInfoDTO provider;

    // Champs spécifiques à la colonne "resolved" (Résolu)
    private LocalDateTime resolvedAt;
    private UserInfoDTO resolvedBy;

    /**
     * DTO imbriqué pour les informations utilisateur (nom + photo)
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class UserInfoDTO {
        private String fullName;
        private String photoUrl;
    }
}
