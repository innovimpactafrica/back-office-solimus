package com.example.solimus.controllers;

import com.example.solimus.dtos.settings.NotificationSettingsDTO;
import com.example.solimus.entities.User;
import com.example.solimus.repositories.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/account")
@RequiredArgsConstructor
@Tag(name = "Paramètres du compte", description = "Gestion des préférences du compte utilisateur")
public class AccountSettingsController {

    private final UserRepository userRepository;

    @Operation(summary = "Récupérer les préférences de notification")
    @GetMapping("/notification-settings")
    public ResponseEntity<NotificationSettingsDTO> getNotificationSettings() {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(
                NotificationSettingsDTO.builder()
                        .notificationsEnabled(currentUser.isNotificationsEnabled())
                        .build()
        );
    }

    @Operation(summary = "Modifier les préférences de notification")
    @PutMapping("/notification-settings")
    public ResponseEntity<NotificationSettingsDTO> updateNotificationSettings(
            @RequestBody @Valid NotificationSettingsDTO dto) {
        User currentUser = getCurrentUser();
        currentUser.setNotificationsEnabled(dto.isNotificationsEnabled());
        userRepository.save(currentUser);
        return ResponseEntity.ok(
                NotificationSettingsDTO.builder()
                        .notificationsEnabled(currentUser.isNotificationsEnabled())
                        .build()
        );
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }
}
