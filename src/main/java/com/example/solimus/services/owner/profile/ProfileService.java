package com.example.solimus.services.owner.profile;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import org.springframework.web.multipart.MultipartFile;

public interface ProfileService {
    CoOwnerProfileDTO getProfile();

    CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto, MultipartFile photo);
}
