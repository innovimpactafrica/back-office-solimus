package com.example.solimus.dtos.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO renvoyé à l'utilisateur lors de la validation du token d'activation.
 * Contient les informations du compte (pré-remplies par l'admin) pour affichage
 * sur la page "Créer votre mot de passe".
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AccountActivationInfoDTO {

    private String firstName;
    private String lastName;
    private String email;
    private String phone;

    /** Le token validé, retourné pour l'appel suivant (définition du mot de passe). */
    private String token;
}
