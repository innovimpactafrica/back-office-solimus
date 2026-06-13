package com.example.solimus.dtos.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecialtyDTO {
    private Long id;

    @NotBlank(message = "Le nom de la spécialité est obligatoire")
    private String name;

    private String description;

    /**
     * Nom de l'icône pour l'affichage frontend (ex: "plumbing", "electrical")
     */
    private String icon;
}
