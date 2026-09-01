package com.example.solimus.controllers;

import com.example.solimus.services.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Gestion des notifications push des utilisateurs")
public class NotificationController {

    private final NotificationService notificationService;

    // Appelé par l'app mobile au démarrage pour enregistrer ou mettre à jour le token FCM de l'utilisateur connecté
    @Operation(summary = "Enregistrer mon token FCM")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token FCM enregistré avec succès")
    })
    @PutMapping("/fcm-token")
    public ResponseEntity<String> saveFcmToken(@RequestParam String fcmToken) {
        notificationService.saveFcmToken(fcmToken);
        return ResponseEntity.ok("Token FCM enregistré avec succès");
    }

    // Marque UNE notification précise comme lue — n'importe quel rôle authentifié peut appeler cet
    // endpoint pour SA PROPRE notification (vérifié côté service, 403 sinon)
    @Operation(summary = "Marquer une notification comme lue")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Notification marquée comme lue avec succès"),
            @ApiResponse(responseCode = "403", description = "Cette notification ne vous appartient pas"),
            @ApiResponse(responseCode = "404", description = "Notification introuvable")
    })
    @PatchMapping("/{id}/read")
    public ResponseEntity<String> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok("Notification marquée comme lue avec succès");
    }
}
