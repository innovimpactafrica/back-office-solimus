package com.example.solimus.services.profile;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;

public interface CoOwnerProfileService {

    /**
     * Récupère le profil du copropriétaire connecté.
     */
    CoOwnerProfileDTO getProfile();

    /**
     * Met à jour le profil du copropriétaire connecté.
     */
    CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto);
}
