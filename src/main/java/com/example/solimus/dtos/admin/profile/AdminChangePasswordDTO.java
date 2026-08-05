package com.example.solimus.dtos.admin.profile;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

// ===== DTO — Corps de la requête POST /api/admin/profile/change-password =====
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminChangePasswordDTO {

    @NotBlank(message = "Le mot de passe actuel est requis")
    private String currentPassword;

    @NotBlank(message = "Le nouveau mot de passe est requis")
    private String newPassword;

    @NotBlank(message = "La confirmation du mot de passe est requise")
    private String confirmPassword;
}