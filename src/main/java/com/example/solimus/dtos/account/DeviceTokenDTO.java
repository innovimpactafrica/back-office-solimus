package com.example.solimus.dtos.account;

import com.example.solimus.enums.DeviceType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Corps de la requête POST /api/account/device-token, envoyée par l'app mobile
 * juste après la connexion pour enregistrer le token FCM du téléphone.
 */
@Data
public class DeviceTokenDTO {

    @NotBlank(message = "Le deviceToken est obligatoire")
    private String deviceToken;

    @NotNull(message = "Le deviceType est obligatoire")
    private DeviceType deviceType;
}
