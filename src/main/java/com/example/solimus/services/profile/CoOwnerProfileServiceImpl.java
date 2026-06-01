package com.example.solimus.services.profile;

import com.example.solimus.dtos.profile.CoOwnerProfileDTO;
import com.example.solimus.dtos.profile.UpdateCoOwnerProfileDTO;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoOwnerProfileServiceImpl implements CoOwnerProfileService {

    private final UserRepository userRepository;

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
    public CoOwnerProfileDTO updateProfile(UpdateCoOwnerProfileDTO dto) {
        User currentUser = getCurrentUser();

        currentUser.setFirstName(dto.getFirstName());
        currentUser.setLastName(dto.getLastName());
        currentUser.setPhone(dto.getPhone());

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
