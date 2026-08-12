package com.example.solimus.services.tenant.profil;

import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationRowDTO;
import com.example.solimus.dtos.syndic.settings.ChangePasswordDTO;
import com.example.solimus.dtos.tenant.profil.TenantProfileDTO;
import com.example.solimus.entities.Notification;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.User;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.NotificationRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class TenantProfilServiceImpl implements TenantProfilService {

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;

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
    // NOTIFICATIONS (CLOCHE)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponseDTO getMyNotifications(int page, int size) {

        User currentTenant = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notificationPage = notificationRepository.findByUserOrderByCreatedAtDesc(currentTenant, pageable);

        List<NotificationRowDTO> rows = new ArrayList<>();
        for (Notification notification : notificationPage.getContent()) {
            rows.add(NotificationRowDTO.builder()
                    .id(notification.getId())
                    .title(notification.getTitle())
                    .body(notification.getBody())
                    .read(notification.getRead())
                    .createdAt(notification.getCreatedAt())
                    .build());
        }

        return NotificationListResponseDTO.builder()
                .totalCount(notificationPage.getTotalElements())
                .notifications(rows)
                .currentPage(notificationPage.getNumber())
                .totalPages(notificationPage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public void markAllNotificationsAsRead() {
        User currentTenant = getCurrentUser();
        notificationRepository.markAllAsReadByUser(currentTenant);
    }

    // =========================================================================
    // PRÉFÉRENCES DE NOTIFICATIONS
    // =========================================================================

    @Override
    @Transactional
    public void activateNotifications() {
        User currentTenant = getCurrentUser();
        currentTenant.setNotificationsEnabled(true);
        userRepository.save(currentTenant);
        log.info("Notifications activées pour le locataire : {}", currentTenant.getEmail());
    }

    @Override
    @Transactional
    public void deactivateNotifications() {
        User currentTenant = getCurrentUser();
        currentTenant.setNotificationsEnabled(false);
        userRepository.save(currentTenant);
        log.info("Notifications désactivées pour le locataire : {}", currentTenant.getEmail());
    }

    // =========================================================================
    // PARAMÈTRES DU COMPTE
    // =========================================================================

    @Override
    @Transactional
    public void changePassword(ChangePasswordDTO dto) {
        User currentTenant = getCurrentUser();

        // Vérifier que le mot de passe actuel est correct
        if (!passwordEncoder.matches(dto.getCurrentPassword(), currentTenant.getPassword())) {
            throw new BadRequestException("Le mot de passe actuel est incorrect");
        }

        // Vérifier que confirmPassword correspond à newPassword si fourni
        if (dto.getConfirmPassword() != null && !dto.getConfirmPassword().equals(dto.getNewPassword())) {
            throw new BadRequestException("La confirmation du mot de passe ne correspond pas");
        }

        // Encoder et définir le nouveau mot de passe
        currentTenant.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(currentTenant);
        log.info("Mot de passe changé pour le locataire : {}", currentTenant.getEmail());
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
