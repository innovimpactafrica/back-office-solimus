package com.example.solimus.services.tenant.profil;

import com.example.solimus.dtos.tenant.profil.TenantProfileDTO;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.User;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantProfilServiceImpl implements TenantProfilService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public TenantProfileDTO getProfile() {

        // Récupère le locataire connecté et son bien
        User currentTenant = getCurrentUser();
        Property property = propertyRepository.findByTenantId(currentTenant.getId())
                .orElseThrow(() -> new ForbiddenException("Aucun bien n'est assigné à ce compte locataire"));

        return TenantProfileDTO.builder()
                .firstName(currentTenant.getFirstName())
                .lastName(currentTenant.getLastName())
                .email(currentTenant.getEmail())
                .phone(currentTenant.getPhone())
                .photoUrl(currentTenant.getProfilePhotoUrl())
                .statusLabel(currentTenant.getStatus() == UserStatus.ACTIVE ? "Locataire actif" : "Locataire inactif")
                .residenceName(property.getResidence().getName())
                .propertyReference(property.getReference())
                .entryDate(property.getTenantAssignedAt())
                .build();
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
