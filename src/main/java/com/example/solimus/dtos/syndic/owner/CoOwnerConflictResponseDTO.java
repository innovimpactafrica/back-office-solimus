package com.example.solimus.dtos.syndic.owner;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// DTO retourné spécifiquement quand un copropriétaire avec cet email ou téléphone existe déjà
// permet au frontend de récupérer l'ID pour proposer le lien directement
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CoOwnerConflictResponseDTO {

    private String message; // "Email déjà utilisé"

    private Long coOwnerId; // ID du copropriétaire existant — pour appeler POST /co-owners/{id}/link

    @JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime timestamp;

    private int status; // 409
}