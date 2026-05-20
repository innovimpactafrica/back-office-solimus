package com.example.solimus.dtos.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerifyForgotPasswordCodeRequestDTO {
    @NotBlank(message = "L'identifiant (e-mail ou téléphone) est obligatoire")
    private String emailOrPhone;

    @NotBlank(message = "Le code OTP est obligatoire")
    private String code;
}
