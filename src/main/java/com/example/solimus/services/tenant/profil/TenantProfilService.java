package com.example.solimus.services.tenant.profil;

import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.tenant.profil.TenantProfileDTO;

public interface TenantProfilService {

    /**
     * Profil du locataire connecté : infos personnelles + infos du bien loué.
     */
    TenantProfileDTO getProfile();

    /**
     * Liste paginée des notifications du locataire connecté (pour la cloche).
     */
    NotificationListResponseDTO getMyNotifications(int page, int size);

    /**
     * Marque toutes les notifications du locataire connecté comme lues.
     */
    void markAllNotificationsAsRead();

    /**
     * Active les notifications push pour le locataire connecté.
     */
    void activateNotifications();

    /**
     * Désactive les notifications push pour le locataire connecté.
     */
    void deactivateNotifications();
}
