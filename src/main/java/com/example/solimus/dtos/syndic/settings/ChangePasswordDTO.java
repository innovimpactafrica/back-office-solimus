package com.example.solimus.dtos.syndic.settings;

import com.example.solimus.utils.PasswordValidationUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordDTO {
    
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
