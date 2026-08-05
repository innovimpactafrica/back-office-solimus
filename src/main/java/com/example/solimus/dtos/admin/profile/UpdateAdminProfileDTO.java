package com.example.solimus.dtos.admin.profile;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.*;

// ===== DTO — Corps de la requête PUT /api/admin/profile =====
// Mise à jour partielle : tous les champs sont optionnels, seuls ceux envoyés (non null) sont modifiés
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAdminProfileDTO {

    private String fullName;

    @Email(message = "L'email doit être valide")
    private String email;

    @Pattern(
            regexp = "^[+]?[0-9\\s\\-\\(\\)]{8,25}$",
            message = "Le numéro de téléphone doit être valide"
    )
    private String phone;
}