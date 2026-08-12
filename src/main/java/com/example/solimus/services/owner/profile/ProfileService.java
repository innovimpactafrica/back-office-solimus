package com.example.solimus.services.owner.profile;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.dtos.syndic.settings.ChangePasswordDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {
    CoOwnerProfileDTO getProfile();

    CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto, MultipartFile photo);

    // Change le mot de passe du copropriétaire connecté (même règles que côté syndic)
    void changePassword(ChangePasswordDTO dto);
}
