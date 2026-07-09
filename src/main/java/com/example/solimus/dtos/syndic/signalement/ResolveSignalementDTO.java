package com.example.solimus.dtos.syndic.signalement;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour résoudre un signalement sans travaux
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResolveSignalementDTO {

    @NotBlank(message = "La note de clôture est obligatoire")
    private String closingNote;
}
