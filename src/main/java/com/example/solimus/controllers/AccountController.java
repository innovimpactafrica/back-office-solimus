package com.example.solimus.controllers;

import com.example.solimus.dtos.account.DeviceTokenDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.notification.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final UserRepository userRepository;

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

    // Envoie un push de test à l'utilisateur connecté — pour vérifier de bout en bout que la chaîne
    // (device token enregistré + clé Firebase + config Firebase du mobile) fonctionne réellement,
    // sans attendre un vrai événement métier (paiement, réunion, incident...). Contrairement au push
    // "normal" (sendPush), le résultat réel de l'envoi Firebase est renvoyé dans la réponse — pas besoin
    // d'aller chercher les logs serveur pour savoir si ça a marché ou pourquoi ça a échoué
    @Operation(summary = "Envoyer un push de test à mon propre téléphone (diagnostic)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résultat réel de l'envoi : accepté par Firebase, ou raison précise de l'échec"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    @PostMapping("/test-push")
    public ResponseEntity<String> sendTestPush() {
        User currentUser = getCurrentUser();
        String result = notificationService.sendTestPushWithDiagnostic(currentUser.getId());
        return ResponseEntity.ok(result);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
