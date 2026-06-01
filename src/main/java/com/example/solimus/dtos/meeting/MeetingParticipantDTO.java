package com.example.solimus.dtos.meeting;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO affiché dans la liste des participants d'une réunion.
 *
 * 3 cas possibles :
 * 1. isOrganisateur = true  → le syndic, badge "Organisateur" affiché
 * 2. grouped = false        → externe ou copro avec rôle spécial, subtitle affiché
 * 3. grouped = true         → tous les copros ordinaires regroupés
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MeetingParticipantDTO {

    private Long id;         // null si externe ou grouped

    // "M. Diop", "Mme Fall", "42 copropriétaires invités"
    private String fullName;

    // "Syndic SOLIMUS", "Présidente du conseil", "Copropriétaires"
    private String subtitle;

    // true → affiche le badge "Organisateur" (syndic uniquement)
    private boolean isOrganisateur;

    // true → entrée groupée (copropriétaires ordinaires)
    private boolean grouped;

    // Nombre dans le groupe si grouped = true (ex: 42)
    private int groupCount;
}
