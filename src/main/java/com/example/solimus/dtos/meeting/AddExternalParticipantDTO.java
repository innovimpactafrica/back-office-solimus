package com.example.solimus.dtos.meeting;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour ajouter un participant externe à une réunion.
 * Pas un user du système — juste un nom + un rôle affiché.
 * Ex: "Mme Fall" - "Présidente du conseil"
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class AddExternalParticipantDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String fullName; // "Mme Fall"

    @NotBlank(message = "Le rôle est obligatoire")
    private String roleLabel; // "Présidente du conseil"
}
