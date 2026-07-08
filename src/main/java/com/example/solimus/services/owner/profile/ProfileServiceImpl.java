package com.example.solimus.services.owner.profile;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileServiceImpl implements ProfileService{

    private final UserRepository userRepository;
    private final MinioService minioService;

    @Override
    @Transactional(readOnly = true)
    public CoOwnerProfileDTO getProfile() {
        User currentUser = getCurrentUser();

        return CoOwnerProfileDTO.builder()
                .firstName(currentUser.getFirstName())
                .lastName(currentUser.getLastName())
                .phone(currentUser.getPhone())
                .email(currentUser.getEmail())
                .photoUrl(currentUser.getProfilePhotoUrl())
                .memberSince(currentUser.getCreatedAt() != null ? currentUser.getCreatedAt().toLocalDate() : null)
                .build();
    }

    @Override
    @Transactional
    public CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto, MultipartFile photo) {
        User currentUser = getCurrentUser();

        if (dto.getFirstName() != null) currentUser.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) currentUser.setLastName(dto.getLastName());
        if (dto.getPhone() != null) currentUser.setPhone(dto.getPhone());

        if (photo != null && !photo.isEmpty()) {
            try {
                String photoUrl = minioService.uploadFile(photo, "profiles");
                if (photoUrl != null) {
                    currentUser.setProfilePhotoUrl(photoUrl);
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de la photo de profil", e);
                throw new RuntimeException("Erreur lors de l'upload de la photo de profil");
            }
        }

        userRepository.save(currentUser);
        log.info("Profil mis à jour pour l'utilisateur {}", currentUser.getEmail());

        return getProfile();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}


