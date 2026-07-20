package com.example.solimus.services.provider;

import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.provider.*;
import com.example.solimus.dtos.provider.profile.ProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.UpdateProviderProfileDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;

import java.math.BigDecimal;

public interface ProviderService {



    // =========================================================================
    // TABLEAU DE BORD (DASHBOARD)
    // =========================================================================
    ProviderDashboardDTO getDashboard();

    // Liste paginée des notifications du prestataire connecté
    NotificationListResponseDTO getMyNotifications(int page, int size);

    // Marque toutes les notifications du prestataire connecté comme lues
    void markAllNotificationsAsRead();

    // =========================================================================
    // PARAMÈTRES DU COMPTE
    // =========================================================================
    void changePassword(String currentPassword, String newPassword);


}
