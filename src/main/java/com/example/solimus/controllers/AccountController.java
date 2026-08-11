package com.example.solimus.controllers;

import com.example.solimus.dtos.account.DeviceTokenDTO;
import com.example.solimus.services.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints "compte" partagés par toutes les apps mobiles (copropriétaire, locataire, prestataire...).
 * Actuellement : enregistrement du token FCM pour les notifications push.
 */
@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Compte", description = "Endpoints communs liés au compte de l'utilisateur connecté")
public class AccountController {

    private final NotificationService notificationService;

    // Appelé par l'app mobile juste après la connexion pour enregistrer le token FCM
    // du téléphone (et son type) de l'utilisateur connecté, afin de recevoir les push système
    @Operation(summary = "Enregistrer le token FCM de mon téléphone")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Device token enregistré avec succès"),
            @ApiResponse(responseCode = "400", description = "deviceToken/deviceType manquant ou invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @PostMapping("/device-token")
    public ResponseEntity<String> registerDeviceToken(@Valid @RequestBody DeviceTokenDTO dto) {
        notificationService.registerDeviceToken(dto.getDeviceToken(), dto.getDeviceType());
        return ResponseEntity.ok("Device token enregistré avec succès");
    }
}
