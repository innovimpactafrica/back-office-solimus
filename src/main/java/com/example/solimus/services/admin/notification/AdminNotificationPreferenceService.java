package com.example.solimus.services.admin.notification;

import com.example.solimus.dtos.admin.notification.AdminNotificationPreferenceDTO;
import com.example.solimus.dtos.admin.notification.UpdateAdminNotificationPreferenceDTO;
import com.example.solimus.enums.AdminNotificationEventType;

import java.util.List;

public interface AdminNotificationPreferenceService {

    /**
     * Récupère la matrice complète des préférences de notification de l'admin connecté.
     * Crée automatiquement les lignes manquantes (valeurs par défaut) pour tout type
     * d'événement qui n'aurait pas encore de préférence enregistrée.
     */
    List<AdminNotificationPreferenceDTO> getMyPreferences();

    /**
     * Met à jour toutes les lignes de la matrice pour l'admin connecté, en une seule transaction.
     */
    List<AdminNotificationPreferenceDTO> updateMyPreferences(List<UpdateAdminNotificationPreferenceDTO> updates);

    /**
     * Diffuse un événement à tous les admins de la plateforme, en respectant la préférence de
     * chacun pour ce type d'événement (canal plateforme = notification en base + push,
     * canal email). Un admin sans préférence enregistrée reçoit les valeurs par défaut
     * (plateforme + email activés, sms désactivé). Le canal SMS n'est pas encore implémenté.
     */
    void notifyAdmins(AdminNotificationEventType eventType, String title, String body);
}