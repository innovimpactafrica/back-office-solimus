package com.example.solimus.dtos.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour les préférences de notification de l'utilisateur.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsDTO {
    /**
     * Indique si l'utilisateur souhaite recevoir les notifications par email.
     * true = notifications activées
     * false = notifications désactivées (seuls les emails critiques sont envoyés)
     */
    private boolean notificationsEnabled;
}
