package com.example.solimus.services.profile;

import com.example.solimus.dtos.owner.signalement.CreateSignalementDTO;
import com.example.solimus.dtos.owner.signalement.OwnerSignalementDTO;
import com.example.solimus.dtos.owner.signalement.OwnerSignalementDetailDTO;
import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.enums.SignalementStatus;
import org.springframework.web.multipart.MultipartFile;

public interface CoOwnerProfileService {
    CoOwnerProfileDTO getProfile();

    CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto, MultipartFile photo);

    void createSignalement(CreateSignalementDTO dto, MultipartFile[] photos);

    OwnerSignalementDTO getMySignalements(String search, SignalementStatus status, Long residenceId, int page, int size);

    OwnerSignalementDetailDTO getSignalementDetail(Long id);
}
