package com.example.solimus.dtos.auth;

import com.example.solimus.utils.PasswordValidationUtil;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SetPasswordRequestDTO {

    @NotBlank(message = "L'email est obligatoire")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = PasswordValidationUtil.MIN_LENGTH,
            message = "Le mot de passe doit contenir au moins 8 caractères")
    @Pattern(regexp = PasswordValidationUtil.PASSWORD_REGEX,
            message = PasswordValidationUtil.PASSWORD_MESSAGE)
    private String password;

    @NotBlank(message = "La confirmation du mot de passe est obligatoire")
    private String confirmPassword;
}
