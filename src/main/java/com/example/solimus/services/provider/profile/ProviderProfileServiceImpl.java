package com.example.solimus.services.provider.profile;

import com.example.solimus.dtos.provider.profile.UpdateLocationDTO;
import com.example.solimus.entities.ProviderProfile;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ProviderProfileRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderProfileServiceImpl implements ProviderProfileService {

    private final ProviderProfileRepository providerProfileRepository;
    private final UserRepository userRepository;

    // =========================================================================
    // NOTIFICATIONS
    // =========================================================================

    @Override
    @Transactional
    public void toggleNotifications() {

        // On identifie le prestataire connecté via son JWT
        User currentProvider = getCurrentUser();

        // On inverse la valeur actuelle
        currentProvider.setNotificationsEnabled(!currentProvider.isNotificationsEnabled());

        // On sauvegarde le changement
        userRepository.save(currentProvider);
        log.info("Préférences de notification mises à jour pour le prestataire : {}",
                currentProvider.isNotificationsEnabled() ? "activées" : "désactivées");
    }

    // ============================================================
    // Mise à jour Localisation Prestataire
    // ============================================================

    @Override
    @Transactional
    public void updateLocation(UpdateLocationDTO dto) {

        // On identifie le prestataire connecté via son JWT
        User currentUser = getCurrentUser();


        // Récupérer le profil prestataire
        ProviderProfile profile = providerProfileRepository.findByUser(currentUser)
                .orElseThrow(() -> new RuntimeException("Profil prestataire introuvable"));

        // Mettre à jour les coordonnées GPS et l'horodatage
        profile.setGpsLatitude(dto.getLatitude());
        profile.setGpsLongitude(dto.getLongitude());
        profile.setGpsUpdatedAt(LocalDateTime.now());

        providerProfileRepository.save(profile);
    }

    // ============================================================
    // Méthodes Utilitaires
    // ============================================================

    // Récupère l'utilisateur actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }
}
