package com.example.solimus.services.admin.notification;

import com.example.solimus.dtos.admin.notification.AdminNotificationPreferenceDTO;
import com.example.solimus.dtos.admin.notification.UpdateAdminNotificationPreferenceDTO;
import com.example.solimus.entities.AdminNotificationPreference;
import com.example.solimus.entities.User;
import com.example.solimus.enums.AdminNotificationEventType;
import com.example.solimus.enums.ERole;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.AdminNotificationPreferenceRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminNotificationPreferenceServiceImpl implements AdminNotificationPreferenceService {

    private final AdminNotificationPreferenceRepository preferenceRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final EmailService emailService;

    // Valeurs posées automatiquement pour tout type d'événement encore sans préférence enregistrée
    private static final boolean DEFAULT_PLATFORM_ENABLED = true;
    private static final boolean DEFAULT_EMAIL_ENABLED = true;
    private static final boolean DEFAULT_SMS_ENABLED = false;

    //Retourne la liste complète des préférences de notification de l'admin courant, en créant les lignes manquantes avec les valeurs par défaut
    @Override
    @Transactional
    public List<AdminNotificationPreferenceDTO> getMyPreferences() {
        User currentAdmin = getCurrentAdmin();
        // On récupère la matrice complète (créée si besoin)
        Map<AdminNotificationEventType, AdminNotificationPreference> matrix = ensureFullMatrix(currentAdmin);
        return toSortedDTOList(matrix);
    }

    // Met à jour les préférences de notification de l'admin courant, en créant les lignes manquantes avec les valeurs par défaut
    @Override
    @Transactional
    public List<AdminNotificationPreferenceDTO> updateMyPreferences(List<UpdateAdminNotificationPreferenceDTO> updates) {
        User currentAdmin = getCurrentAdmin();

        // On part de la matrice complète existante (créée si besoin), pour ne jamais perdre
        // une ligne qui ne serait pas envoyée dans le body
        Map<AdminNotificationEventType, AdminNotificationPreference> matrix = ensureFullMatrix(currentAdmin);

        // On met à jour les préférences existantes avec les valeurs envoyées dans le body —
        // seuls les champs non null sont modifiés (mise à jour partielle au niveau de la ligne ET du champ)
        for (UpdateAdminNotificationPreferenceDTO update : updates) {
            AdminNotificationPreference preference = matrix.get(update.getEventType());
            if (update.getPlatformEnabled() != null) preference.setPlatformEnabled(update.getPlatformEnabled());
            if (update.getEmailEnabled() != null) preference.setEmailEnabled(update.getEmailEnabled());
            if (update.getSmsEnabled() != null) preference.setSmsEnabled(update.getSmsEnabled());
            preferenceRepository.save(preference);
        }

        log.info("Préférences de notification mises à jour pour l'admin : {}", currentAdmin.getEmail());
        return toSortedDTOList(matrix);
    }

    // Diffuse un événement à tous les admins, en respectant la préférence de chacun pour ce type
    // d'événement. Ne crée aucune ligne en base pour les admins sans préférence enregistrée —
    // on utilise simplement les valeurs par défaut pour cette diffusion précise.
    @Override
    @Transactional
    public void notifyAdmins(AdminNotificationEventType eventType, String title, String body) {
        List<User> admins = userRepository.findByRole_NameOrderByCreatedAtDesc(ERole.ROLE_ADMIN, Pageable.unpaged());

        //On parcourt tous les admins et on envoie la notification selon la préférence de chacun
        for (User admin : admins) {

            // On récupère la préférence de l'admin pour ce type d'événement, si elle existe
            Optional<AdminNotificationPreference> preference =
                    preferenceRepository.findByAdminIdAndEventType(admin.getId(), eventType);

            // on récupére les valeurs de préférence, ou les valeurs par défaut si la ligne n'existe pas
            boolean platformEnabled = preference.map(AdminNotificationPreference::isPlatformEnabled)
                    .orElse(DEFAULT_PLATFORM_ENABLED);
            boolean emailEnabled = preference.map(AdminNotificationPreference::isEmailEnabled)
                    .orElse(DEFAULT_EMAIL_ENABLED);
            // smsEnabled : stocké mais non exploité ici, aucun provider SMS branché pour l'instant

            if (platformEnabled) {
                try {
                    // Crée la ligne "Notification" (cloche) et tente le push Firebase si un token existe
                    notificationService.sendPush(admin.getId(), title, body);
                } catch (Exception e) {
                    log.warn("Échec notification plateforme pour l'admin {} : {}", admin.getEmail(), e.getMessage());
                }
            }

            if (emailEnabled) {
                try {
                    emailService.sendEmail(admin.getEmail(), title, body);
                } catch (Exception e) {
                    log.warn("Échec email de notification pour l'admin {} : {}", admin.getEmail(), e.getMessage());
                }
            }
        }
    }

    // =========================================================================
    // Méthodes Utilitaires
    // =========================================================================

    // Retourne la matrice (event type -> préférence) de l'admin, en créant les lignes
    // manquantes avec les valeurs par défaut — appelé aussi bien au GET qu'au PUT
    private Map<AdminNotificationEventType, AdminNotificationPreference> ensureFullMatrix(User admin)
     {
        // On récupère les préférences existantes de l'admin, et on les met dans une map
        Map<AdminNotificationEventType, AdminNotificationPreference> matrix = preferenceRepository
                .findByAdminId(admin.getId()).stream()
                .collect(Collectors.toMap(AdminNotificationPreference::getEventType, p -> p,
                        (a, b) -> a, () -> new EnumMap<>(AdminNotificationEventType.class)));

        // On parcourt tous les types d'événements, et on crée les préférences manquantes avec les valeurs par défaut
        for (AdminNotificationEventType eventType : AdminNotificationEventType.values()) {
            // Si la préférence n'existe pas encore pour ce type d'événement, on la crée avec les valeurs par défaut
            if (!matrix.containsKey(eventType)) {
                AdminNotificationPreference preference = new AdminNotificationPreference();
                preference.setAdmin(admin);
                preference.setEventType(eventType);
                preference.setPlatformEnabled(DEFAULT_PLATFORM_ENABLED);
                preference.setEmailEnabled(DEFAULT_EMAIL_ENABLED);
                preference.setSmsEnabled(DEFAULT_SMS_ENABLED);
                matrix.put(eventType, preferenceRepository.save(preference));
            }
        }

        return matrix;
    }

    
    private List<AdminNotificationPreferenceDTO> toSortedDTOList(Map<AdminNotificationEventType, AdminNotificationPreference> matrix) {
        return Arrays.stream(AdminNotificationEventType.values())
                .map(matrix::get)
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    private AdminNotificationPreferenceDTO toDTO(AdminNotificationPreference preference) {
        AdminNotificationEventType eventType = preference.getEventType();
        return AdminNotificationPreferenceDTO.builder()
                .eventType(eventType)
                .label(eventType.getLabel())
                .description(eventType.getDescription())
                .platformEnabled(preference.isPlatformEnabled())
                .emailEnabled(preference.isEmailEnabled())
                .smsEnabled(preference.isSmsEnabled())
                .build();
    }

    private User getCurrentAdmin() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Administrateur introuvable"));
    }
}