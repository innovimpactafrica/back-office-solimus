package com.example.solimus.services.tenant.profil;

import com.example.solimus.dtos.tenant.profil.TenantProfileDTO;

public interface TenantProfilService {

    /**
     * Profil du locataire connecté : infos personnelles + infos du bien loué.
     */
    TenantProfileDTO getProfile();
}
