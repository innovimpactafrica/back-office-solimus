package com.example.solimus.dtos.auth;

import com.example.solimus.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour la réponse après une connexion réussie.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponseDTO {

    private String accessToken;
    private String email;
    private String role;
    private Long id;
    private String firstName;
    private String lastName;
    private UserStatus status;
    
    // Pour la gestion OTP administrateur (si applicable)
    private boolean otpRequired;

    /**
     * Constructeur simplifié pour le flux OTP
     */
    public LoginResponseDTO(String email, boolean otpRequired) {
        this.email = email;
        this.otpRequired = otpRequired;
    }
}
