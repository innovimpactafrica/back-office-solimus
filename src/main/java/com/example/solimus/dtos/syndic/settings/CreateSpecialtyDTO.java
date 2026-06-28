package com.example.solimus.dtos.syndic.settings;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

//DTO pour créer une spécialité
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateSpecialtyDTO {
    @NotBlank(message = "Le nom de la spécialité est obligatoire")
    private String name;

    private String description;

    /**
     * Nom de l'icône pour l'affichage frontend (ex: "plumbing", "electrical")
     */
    private String icon;
}
