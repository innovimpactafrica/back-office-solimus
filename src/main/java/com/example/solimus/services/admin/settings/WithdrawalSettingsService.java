package com.example.solimus.services.admin.settings;

import com.example.solimus.dtos.admin.settings.UpdateWithdrawalSettingsDTO;
import com.example.solimus.dtos.admin.settings.WithdrawalSettingsDTO;

public interface WithdrawalSettingsService {

    /**
     * Récupère la limite mensuelle de retrait actuellement configurée.
     * Crée l'enregistrement par défaut (20 000 000 FCFA) s'il n'existe pas encore.
     */
    WithdrawalSettingsDTO getMonthlyLimit();

    /**
     * Modifie la limite mensuelle de retrait, et trace l'admin qui l'a modifiée.
     */
    WithdrawalSettingsDTO updateMonthlyLimit(UpdateWithdrawalSettingsDTO dto);
}