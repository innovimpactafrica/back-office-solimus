package com.example.solimus.dtos.admin.settings;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

// ===== DTO — Corps de la requête PUT /api/admin/platform-settings =====
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePlatformSettingsDTO {

    @NotBlank(message = "Le nom de la plateforme est obligatoire")
    private String platformName;

    private String website;

    @Email(message = "L'email de support doit être valide")
    private String supportEmail;

    private String supportPhone;
}