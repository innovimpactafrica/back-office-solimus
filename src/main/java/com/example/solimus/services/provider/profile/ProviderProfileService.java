package com.example.solimus.services.provider.profile;


import com.example.solimus.dtos.provider.profile.UpdateLocationDTO;

public interface ProviderProfileService {

    // ============================================================
    // Gestion Notifications
    // ============================================================
    void toggleNotifications();
    
    // ============================================================
    // Gestion Localisation
    // ============================================================
    void updateLocation(UpdateLocationDTO dto);
}
