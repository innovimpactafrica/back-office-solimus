package com.example.solimus.dtos.admin.profile;

import com.example.solimus.utils.PasswordValidationUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @Size(min = PasswordValidationUtil.MIN_LENGTH,
            message = "Le nouveau mot de passe doit contenir au moins 8 caractères")
    @Pattern(regexp = PasswordValidationUtil.PASSWORD_REGEX,
            message = PasswordValidationUtil.PASSWORD_MESSAGE)
    private String newPassword;

    @NotBlank(message = "La confirmation du mot de passe est requise")
    private String confirmPassword;
}