package com.example.solimus.services.admin.profile;

import com.example.solimus.dtos.admin.profile.AdminChangePasswordDTO;
import com.example.solimus.dtos.admin.profile.AdminProfileDTO;
import com.example.solimus.dtos.admin.profile.ChangePasswordResultDTO;
import com.example.solimus.dtos.admin.profile.UpdateAdminProfileDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface AdminProfileService {

    /**
     * Récupère les informations personnelles de l'admin connecté.
     */
    AdminProfileDTO getMyProfile();

    /**
     * Met à jour les informations personnelles de l'admin connecté (nom, email, téléphone).
     * Le rôle n'est jamais modifiable via cette méthode.
     */
    AdminProfileDTO updateMyProfile(UpdateAdminProfileDTO dto);

    /**
     * Ajoute ou remplace la photo de profil de l'admin connecté.
     */
    AdminProfileDTO updateProfilePhoto(MultipartFile photo);

    /**
     * Change le mot de passe de l'admin connecté.
     */
    ChangePasswordResultDTO changePassword(AdminChangePasswordDTO dto);

    /**
     * Retourne l'historique paginé des notifications (cloche) de l'admin connecté,
     * du plus récent au plus ancien.
     */
    NotificationListResponseDTO getMyNotifications(int page, int size);

    /**
     * Marque toutes les notifications de l'admin connecté comme lues.
     */
    void markAllNotificationsAsRead();
}