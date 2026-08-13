package com.example.solimus.dtos.owner.meeting;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

// Corps de la requête POST /api/owner/meetings/{meetingId}/procuration
@Data
public class GiveProcurationDTO {

    @NotBlank(message = "Le nom du mandataire est obligatoire")
    private String mandataireName; // Nom et prénom du mandataire
}
