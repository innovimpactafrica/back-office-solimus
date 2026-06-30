package com.example.solimus.services.provider;

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

    // =========================================================================
    // PARAMÈTRES DU COMPTE
    // =========================================================================
    void changePassword(String currentPassword, String newPassword);


}
