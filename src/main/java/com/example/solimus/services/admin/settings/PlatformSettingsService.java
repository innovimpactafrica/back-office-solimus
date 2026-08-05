package com.example.solimus.services.admin.settings;

import com.example.solimus.dtos.admin.settings.PlatformSettingsDTO;
import com.example.solimus.dtos.admin.settings.UpdatePlatformSettingsDTO;

public interface PlatformSettingsService {

    /**
     * Récupère les réglages globaux de la plateforme.
     * Crée l'enregistrement par défaut s'il n'existe pas encore.
     */
    PlatformSettingsDTO getPlatformSettings();

    /**
     * Modifie les réglages globaux de la plateforme, et trace l'admin qui les a modifiés.
     */
    PlatformSettingsDTO updatePlatformSettings(UpdatePlatformSettingsDTO dto);
}