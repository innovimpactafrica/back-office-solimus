package com.example.solimus.dtos.admin;

import com.example.solimus.enums.ERole;
import com.example.solimus.enums.UserStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO de réponse renvoyé après la création d'un compte utilisateur par l'admin.
 * Contient les informations du compte créé (sans le mot de passe).
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateUserResponseDTO {

    private Long id;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private ERole role;
    private UserStatus status;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private java.time.LocalDateTime createdAt;

    /** Message informatif indiquant que l'email d'activation a été envoyé. */
    private String message;
}
