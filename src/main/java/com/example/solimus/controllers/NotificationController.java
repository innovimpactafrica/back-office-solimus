package com.example.solimus.controllers;

import com.example.solimus.services.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "9. Notifications", description = "Gestion des notifications push des utilisateurs")
public class NotificationController {

    private final NotificationService notificationService;

    // Appelé par l'app mobile au démarrage pour enregistrer ou mettre à jour le token FCM de l'utilisateur connecté
    @Operation(summary = "Enregistrer mon token FCM")
    @PutMapping("/fcm-token")
    public ResponseEntity<String> saveFcmToken(@RequestParam String fcmToken) {
        notificationService.saveFcmToken(fcmToken);
        return ResponseEntity.ok("Token FCM enregistré avec succès");
    }
}
