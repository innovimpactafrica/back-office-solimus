package com.example.solimus.services.profile;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import org.springframework.web.multipart.MultipartFile;

public interface CoOwnerProfileService {
    CoOwnerProfileDTO getProfile();

    CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto, MultipartFile photo);
}
