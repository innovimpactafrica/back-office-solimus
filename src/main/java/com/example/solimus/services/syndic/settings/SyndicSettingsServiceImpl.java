package com.example.solimus.services.syndic.settings;

import com.example.solimus.dtos.syndic.settings.EstimatedDelayDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationListResponseDTO;
import com.example.solimus.dtos.owner.dashboard.NotificationRowDTO;
import com.example.solimus.dtos.syndic.settings.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.Currency;
import com.example.solimus.enums.FacilityCategory;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicSettingsServiceImpl implements SyndicSettingsService {

    private final FacilityTypeRepository facilityTypeRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final SecurityFeatureRepository securityFeatureRepository;
    private final EstimatedDelayRepository estimatedDelayRepository;
    private final ResidenceRepository residenceRepository;
    private final SyndicFinancialSettingsRepository syndicFinancialSettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;
    private final SyndicProfileRepository syndicProfileRepository;
    private final NotificationRepository notificationRepository;

    //--------------------------------------------------------
    // ===== TYPES D'ÉQUIPEMENTS =====
    //--------------------------------------------------------

    //Listing — uniquement le catalogue du syndic connecté
    @Override
    @Transactional(readOnly = true)
    public Page<FacilityTypeDTO> getAllFacilityTypes(int page, int size) {
        User currentSyndic = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return facilityTypeRepository.findBySyndicId(currentSyndic.getId(), pageable)
                .map(this::toDTO);
    }

    @Override
    @Transactional
    public void createFacilityType(String name, String category, String description, Boolean isActive, MultipartFile icon) {

        User currentSyndic = getCurrentUser();

        if (facilityTypeRepository.existsByNameIgnoreCaseAndSyndicId(name, currentSyndic.getId())) {
            throw new BadRequestException("Un type d'équipement avec ce nom existe déjà");
        }

        FacilityCategory facilityCategory = FacilityCategory.valueOf(category.toUpperCase());

        FacilityType facilityType = new FacilityType();
        facilityType.setSyndic(currentSyndic);
        facilityType.setName(name);
        facilityType.setCategory(facilityCategory);
        facilityType.setDescription(description);
            facilityType.setIsActive(isActive != null ? isActive : true);

        if (icon != null && !icon.isEmpty()) {
            String iconUrl = minioService.uploadFile(icon, "facility-types");
            facilityType.setIcon(iconUrl);
        }

        FacilityType saved = facilityTypeRepository.save(facilityType);
        log.info("Type d'équipement créé : {}", saved.getName());
    }

    //Modification
    @Override
    @Transactional
    public void updateFacilityType(Long id, String name, String category, String description, Boolean isActive, MultipartFile icon) {

        User currentSyndic = getCurrentUser();

        // Récupérer le type d'équipement — uniquement s'il appartient au syndic connecté
        FacilityType facilityType = facilityTypeRepository.findByIdAndSyndicId(id, currentSyndic.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Type d'équipement introuvable"));

        // Vérifier l'unicité du nom si modifié (dans le catalogue de ce syndic uniquement)
        if (name != null
                && !facilityType.getName().equalsIgnoreCase(name)
                && facilityTypeRepository.existsByNameIgnoreCaseAndSyndicId(name, currentSyndic.getId())) {
            throw new BadRequestException("Un type d'équipement avec ce nom existe déjà");
        }

        if (name != null) facilityType.setName(name);
        if (category != null) {
            FacilityCategory facilityCategory = FacilityCategory.valueOf(category.toUpperCase());
            facilityType.setCategory(facilityCategory);
        }
        if (description != null) facilityType.setDescription(description);
        if (isActive != null) facilityType.setIsActive(isActive);

        if (icon != null && !icon.isEmpty()) {
            String iconUrl = minioService.uploadFile(icon, "facility-types");
            facilityType.setIcon(iconUrl);
        }

        FacilityType saved = facilityTypeRepository.save(facilityType);
        log.info("Type d'équipement modifié : {}", saved.getName());
    }
    //Suppression
    @Override
    @Transactional
    public void deleteFacilityType(Long id) {

        User currentSyndic = getCurrentUser();

        //Récupérer le type d'équipement — uniquement s'il appartient au syndic connecté
        FacilityType facilityType = facilityTypeRepository.findByIdAndSyndicId(id, currentSyndic.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Type d'équipement introuvable"));

        // Empêcher la suppression si des résidences utilisent encore ce type
        int residenceCount = commonFacilityRepository
                .countResidenceByFacilityTypeId(id);

        if (residenceCount > 0) {
            throw new BadRequestException(
                "Impossible de supprimer ce type : utilisé par " + residenceCount + " résidence(s). Désactivez-le plutôt."
            );
        }

        facilityTypeRepository.delete(facilityType);
        log.info("Type d'équipement supprimé : {}", facilityType.getName());
    }

    //--------------------------------------------------------
    // ===== SPÉCIALITÉS =====
    //--------------------------------------------------------

    //Listing
    @Override
    @Transactional(readOnly = true)
    public Page<SpecialtyDTO> getAllSpecialties(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return specialtyRepository.findAll(pageable)
                .map(s -> new SpecialtyDTO(s.getId(), s.getName(), s.getDescription(), s.getIcon()));
    }

    //Création
    @Override
    @Transactional
    public void createSpecialty(String name, String description, MultipartFile icon) {
        if (specialtyRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("La spécialité '" + name + "' existe déjà.");
        }
        Specialty specialty = new Specialty();
        specialty.setName(name);
        specialty.setDescription(description);
        if (icon != null && !icon.isEmpty()) {
            String iconUrl = minioService.uploadFile(icon, "specialties");
            specialty.setIcon(iconUrl);
        }
        specialtyRepository.save(specialty);
        log.info("Spécialité créée : {}", name);
    }

    //Modification
    @Override
    @Transactional
    public void updateSpecialty(Long id, String name, String description, MultipartFile icon) {
        //Vérifier que la spécialité existe
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité non trouvée"));
        //Vérifier que le nom de la spécialité n'est pas déjà utilisé
        if (name != null
                && !specialty.getName().equalsIgnoreCase(name)
                && specialtyRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("La spécialité '" + name + "' existe déjà.");
        }
        if (name != null) specialty.setName(name);
        if (description != null) specialty.setDescription(description);
        if (icon != null && !icon.isEmpty()) {
            String iconUrl = minioService.uploadFile(icon, "specialties");
            specialty.setIcon(iconUrl);
        }
        specialtyRepository.save(specialty);
        log.info("Spécialité modifiée : {}", specialty.getName());
    }

    //Suppression
    @Override
    @Transactional
    public void deleteSpecialty(Long id) {
        if (!specialtyRepository.existsById(id)) {
            throw new ResourceNotFoundException("Spécialité non trouvée");
        }
        specialtyRepository.deleteById(id);
        log.info("Spécialité supprimée : id={}", id);
    }

    //--------------------------------------------------------
    // ===== TYPES D'APPARTEMENT =====
    //--------------------------------------------------------

    //Listing — uniquement le catalogue du syndic connecté
    @Override
    @Transactional(readOnly = true)
    public Page<PropertyTypeDTO> getAllPropertyTypes(int page, int size) {
        User currentSyndic = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return propertyTypeRepository.findBySyndicId(currentSyndic.getId(), pageable)
                .map(pt -> PropertyTypeDTO.builder()
                        .id(pt.getId())
                        .name(pt.getName())
                        .description(pt.getDescription())
                        .build());
    }

    //Création
    @Override
    @Transactional
    public void createPropertyType(CreatePropertyTypeDTO dto) {
        User currentSyndic = getCurrentUser();
        if (propertyTypeRepository.existsByNameIgnoreCaseAndSyndicId(dto.getName(), currentSyndic.getId())) {
            throw new BadRequestException("Un type d'appartement avec ce nom existe déjà");
        }
        PropertyType propertyType = new PropertyType();
        propertyType.setSyndic(currentSyndic);
        propertyType.setName(dto.getName());
        propertyType.setDescription(dto.getDescription());
        propertyTypeRepository.save(propertyType);
        log.info("Type d'appartement créé : {}", dto.getName());
    }

    //Modification
    @Override
    @Transactional
    public void updatePropertyType(Long id, CreatePropertyTypeDTO dto) {
        User currentSyndic = getCurrentUser();
        // Uniquement s'il appartient au syndic connecté
        PropertyType propertyType = propertyTypeRepository.findByIdAndSyndicId(id, currentSyndic.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Type d'appartement introuvable"));
        if (dto.getName() != null
                && !propertyType.getName().equalsIgnoreCase(dto.getName())
                && propertyTypeRepository.existsByNameIgnoreCaseAndSyndicId(dto.getName(), currentSyndic.getId())) {
            throw new BadRequestException("Un type d'appartement avec ce nom existe déjà");
        }
        if (dto.getName() != null) propertyType.setName(dto.getName());
        if (dto.getDescription() != null) propertyType.setDescription(dto.getDescription());
        propertyTypeRepository.save(propertyType);
        log.info("Type d'appartement modifié : {}", propertyType.getName());
    }

    //Suppression
    @Override
    @Transactional
    public void deletePropertyType(Long id) {
        User currentSyndic = getCurrentUser();
        // Uniquement s'il appartient au syndic connecté
        PropertyType propertyType = propertyTypeRepository.findByIdAndSyndicId(id, currentSyndic.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Type d'appartement introuvable"));
        propertyTypeRepository.delete(propertyType);
        log.info("Type d'appartement supprimé : id={}", id);
    }

    // =========================================================================
    // OPTIONS DE SÉCURITÉ
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<SecurityFeatureDTO> getAllSecurityFeatures(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("label").ascending());
        return securityFeatureRepository.findAll(pageable)
                .map(sf -> SecurityFeatureDTO.builder()
                        .id(sf.getId())
                        .label(sf.getLabel())
                        .description(sf.getDescription())
                        .active(sf.isActive())
                        .icon(sf.getIcon())
                        .build());
    }

    @Override
    @Transactional
    public void createSecurityFeature(String label, String description, Boolean isActive, MultipartFile icon) {
        // Vérifier que le label n'existe pas déjà (insensible à la casse)
        if (securityFeatureRepository.existsByLabelIgnoreCase(label)) {
            throw new BadRequestException("Une option de sécurité avec ce label existe déjà");
        }

        SecurityFeature securityFeature = new SecurityFeature();
        securityFeature.setLabel(label);
        securityFeature.setDescription(description);
        securityFeature.setActive(isActive != null ? isActive : true);

        // Upload de l'icône si fournie
        if (icon != null && !icon.isEmpty()) {
            String iconUrl = minioService.uploadFile(icon, "security-features");
            securityFeature.setIcon(iconUrl);
        }

        securityFeatureRepository.save(securityFeature);
        log.info("Option de sécurité créée : label={}", label);
    }

    @Override
    @Transactional
    public void updateSecurityFeature(Long id, String label, String description, Boolean isActive, MultipartFile icon) {
        SecurityFeature securityFeature = securityFeatureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Option de sécurité introuvable"));

        // Mise à jour partielle des champs
        if (label != null) {
            // Vérifier que le nouveau label n'existe pas déjà (insensible à la casse)
            if (!label.equalsIgnoreCase(securityFeature.getLabel()) &&
                securityFeatureRepository.existsByLabelIgnoreCase(label)) {
                throw new BadRequestException("Une option de sécurité avec ce label existe déjà");
            }
            securityFeature.setLabel(label);
        }
        if (description != null) {
            securityFeature.setDescription(description);
        }
        if (isActive != null) {
            securityFeature.setActive(isActive);
        }

        // Upload de la nouvelle icône si fournie
        if (icon != null && !icon.isEmpty()) {
            String iconUrl = minioService.uploadFile(icon, "security-features");
            securityFeature.setIcon(iconUrl);
        }

        securityFeatureRepository.save(securityFeature);
        log.info("Option de sécurité modifiée : id={}", id);
    }

    @Override
    @Transactional
    public void deleteSecurityFeature(Long id) {
        SecurityFeature securityFeature = securityFeatureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Option de sécurité introuvable"));

        // Vérifier si des résidences utilisent cette option de sécurité
        long residenceCount = residenceRepository.countBySecurityFeatureId(id);
        if (residenceCount > 0) {
            throw new BadRequestException(
                "Impossible de supprimer cette option de sécurité car elle est utilisée par " +
                residenceCount + " résidence(s)");
        }

        securityFeatureRepository.delete(securityFeature);
        log.info("Option de sécurité supprimée : id={}", id);
    }

    // =========================================================================
    // DÉLAIS ESTIMÉS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<EstimatedDelayDTO> getAllEstimatedDelays() {
        return estimatedDelayRepository.findAll().stream()
                .map(d -> new EstimatedDelayDTO(d.getId(), d.getLabel(), d.getDays()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public EstimatedDelayDTO createEstimatedDelay(String label, Integer days) {
        if (estimatedDelayRepository.existsByLabelIgnoreCase(label)) {
            throw new BadRequestException("Un délai estimé avec ce label existe déjà");
        }

        EstimatedDelay delay = EstimatedDelay.builder()
                .label(label)
                .days(days)
                .build();
        EstimatedDelay saved = estimatedDelayRepository.save(delay);
        log.info("Délai estimé créé : {}", saved.getLabel());
        return new EstimatedDelayDTO(saved.getId(), saved.getLabel(), saved.getDays());
    }

    @Override
    @Transactional
    public void deleteEstimatedDelay(Long id) {
        EstimatedDelay delay = estimatedDelayRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Délai estimé introuvable"));
        estimatedDelayRepository.delete(delay);
        log.info("Délai estimé supprimé : id={}", id);
    }

    // =========================================================================
    // PARAMÈTRES FINANCIERS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicFinancialSettingsDTO getFinancialSettings() {

        User currentSyndic = getCurrentUser();

        // On cherche si le syndic a déjà sauvegardé une configuration
        return syndicFinancialSettingsRepository.findBySyndicId(currentSyndic.getId())
                .map(this::mapToDTO)
                // Sinon on retourne les valeurs par défaut SANS les persister
                .orElseGet(this::getDefaultSettings);
    }

    @Override
    @Transactional
    public void saveFinancialSettings(UpdateSyndicFinancialSettingsDTO dto) {

        User currentSyndic = getCurrentUser();

        // Create-or-update : on cherche une config existante, sinon on en crée une nouvelle
        SyndicFinancialSettings settings = syndicFinancialSettingsRepository
                .findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicFinancialSettings nouvelle = new SyndicFinancialSettings();
                    nouvelle.setSyndic(currentSyndic);
                    return nouvelle; //=> settings
                });

        // On applique les nouvelles valeurs uniquement si non null (mise à jour partielle)
        if (dto.getCurrency() != null) {
            settings.setCurrency(dto.getCurrency());
        }
        if (dto.getChargeFrequency() != null) {
            settings.setChargeFrequency(dto.getChargeFrequency());
        }
        if (dto.getLatePenaltyRate() != null) {
            settings.setLatePenaltyRate(dto.getLatePenaltyRate());
        }
        if (dto.getReminderDelayDays() != null) {
            settings.setReminderDelayDays(dto.getReminderDelayDays());
        }
        if (dto.getPenaltyGracePeriodDays() != null) {
            settings.setPenaltyGracePeriodDays(dto.getPenaltyGracePeriodDays());
        }
        if (dto.getReserveFundPercentage() != null) {
            settings.setReserveFundPercentage(dto.getReserveFundPercentage());
        }

        syndicFinancialSettingsRepository.save(settings);
    }

    // =========================================================================
    // PROFIL SYNDIC
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicProfileDTO getSyndicProfile() {
        User currentSyndic = getCurrentUser();
        return SyndicProfileDTO.builder()
                .id(currentSyndic.getId())
                .firstName(currentSyndic.getFirstName())
                .lastName(currentSyndic.getLastName())
                .fullName(currentSyndic.getFirstName() + " " + currentSyndic.getLastName())
                .email(currentSyndic.getEmail())
                .phone(currentSyndic.getPhone())
                .role(currentSyndic.getRole().getName().name())
                .photoUrl(currentSyndic.getProfilePhotoUrl())
                .build();
    }

    @Override
    @Transactional
    public void updateSyndicProfile(String firstName, String lastName, String phone, MultipartFile photo) {
        User currentSyndic = getCurrentUser();

        if (firstName != null) {
            currentSyndic.setFirstName(firstName);
        }
        if (lastName != null) {
            currentSyndic.setLastName(lastName);
        }
        if (phone != null) {
            // Vérifier si le téléphone existe déjà pour un autre utilisateur
            if (userRepository.existsByPhoneAndIdNot(phone, currentSyndic.getId())) {
                throw new BadRequestException("Ce numéro de téléphone est déjà utilisé par un autre utilisateur");
            }
            currentSyndic.setPhone(phone);
        }
        if (photo != null && !photo.isEmpty()) {
            String photoUrl = minioService.uploadFile(photo, "syndic-photos");
            currentSyndic.setProfilePhotoUrl(photoUrl);
        }

        userRepository.save(currentSyndic);
        log.info("Profil syndic mis à jour : {}", currentSyndic.getEmail());
    }

    @Override
    @Transactional
    public void updateProfilePhoto(MultipartFile photo) {
        User currentSyndic = getCurrentUser();

        if (photo == null || photo.isEmpty()) {
            throw new BadRequestException("Aucune photo fournie");
        }

        String photoUrl = minioService.uploadFile(photo, "syndic-photos");
        currentSyndic.setProfilePhotoUrl(photoUrl);
        userRepository.save(currentSyndic);
        log.info("Photo de profil mise à jour pour le syndic : {}", currentSyndic.getEmail());
    }

    @Override
    @Transactional
    public void changePassword(ChangePasswordDTO dto) {
        User currentSyndic = getCurrentUser();

        // Vérifier que le mot de passe actuel est correct
        if (!passwordEncoder.matches(dto.getCurrentPassword(), currentSyndic.getPassword())) {
            throw new BadRequestException("Le mot de passe actuel est incorrect");
        }

        // Vérifier que confirmPassword correspond à newPassword si fourni
        if (dto.getConfirmPassword() != null && !dto.getConfirmPassword().equals(dto.getNewPassword())) {
            throw new BadRequestException("La confirmation du mot de passe ne correspond pas");
        }

        // Encoder et définir le nouveau mot de passe
        currentSyndic.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(currentSyndic);
        log.info("Mot de passe changé pour le syndic : {}", currentSyndic.getEmail());
    }

    // =========================================================================
    // Récupère les préférences de notification du syndic connecté
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public SyndicNotificationPreferencesDTO getNotificationPreferences() {

        User currentUser = getCurrentUser();

        SyndicProfile profile = syndicProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profil syndic introuvable"));

        return SyndicNotificationPreferencesDTO.builder()
                .notifNewPayments(profile.getNotifNewPayments())
                .notifUrgentIncidents(profile.getNotifUrgentIncidents())
                .notifUnpaidReminders(profile.getNotifUnpaidReminders())
                .notifAgReminders(profile.getNotifAgReminders())
                .build();
    }

    // =========================================================================
    // Met à jour les préférences de notification du syndic connecté
    // =========================================================================
    @Override
    @Transactional
    public SyndicNotificationPreferencesDTO updateNotificationPreferences(SyndicNotificationPreferencesDTO dto) {

        User currentUser = getCurrentUser();

        SyndicProfile profile = syndicProfileRepository.findByUserId(currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profil syndic introuvable"));

        // Ne met à jour que les champs réellement envoyés
        if (dto.getNotifNewPayments() != null) {
            profile.setNotifNewPayments(dto.getNotifNewPayments());
        }
        if (dto.getNotifUrgentIncidents() != null) {
            profile.setNotifUrgentIncidents(dto.getNotifUrgentIncidents());
        }
        if (dto.getNotifUnpaidReminders() != null) {
            profile.setNotifUnpaidReminders(dto.getNotifUnpaidReminders());
        }
        if (dto.getNotifAgReminders() != null) {
            profile.setNotifAgReminders(dto.getNotifAgReminders());
        }

        SyndicProfile saved = syndicProfileRepository.save(profile);

        return SyndicNotificationPreferencesDTO.builder()
                .notifNewPayments(saved.getNotifNewPayments())
                .notifUrgentIncidents(saved.getNotifUrgentIncidents())
                .notifUnpaidReminders(saved.getNotifUnpaidReminders())
                .notifAgReminders(saved.getNotifAgReminders())
                .build();
    }

    // =========================================================================
    // Liste paginée des notifications du syndic connecté
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public NotificationListResponseDTO getMyNotifications(int page, int size) {

        User currentUser = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notificationPage = notificationRepository.findByUserOrderByCreatedAtDesc(currentUser, pageable);

        List<NotificationRowDTO> rows = notificationPage.getContent().stream()
                .map(notification -> NotificationRowDTO.builder()
                        .id(notification.getId())
                        .title(notification.getTitle())
                        .body(notification.getBody())
                        .read(notification.getRead())
                        .createdAt(notification.getCreatedAt())
                        .build())
                .toList();

        return NotificationListResponseDTO.builder()
                .totalCount(notificationPage.getTotalElements())
                .notifications(rows)
                .currentPage(notificationPage.getNumber())
                .totalPages(notificationPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // Marque toutes les notifications du syndic connecté comme lues
    // =========================================================================
    @Override
    @Transactional
    public void markAllNotificationsAsRead() {
        User currentUser = getCurrentUser();
        notificationRepository.markAllAsReadByUser(currentUser);
    }

    //--------------------------------------------------------
    // Méthodes Utilitaires
    //--------------------------------------------------------
    private FacilityTypeDTO toDTO(FacilityType facilityType) {

        //Compter le nombre de résidences qui utilisent ce type d'équipement
        int residenceCount = commonFacilityRepository
                .countResidenceByFacilityTypeId(facilityType.getId());

        //Retourner le DTO
        return FacilityTypeDTO.builder()
                .id(facilityType.getId())
                .name(facilityType.getName())
                .category(facilityType.getCategory())
                .categoryLabel(facilityType.getCategory().getLabel())
                .icon(facilityType.getIcon())
                .description(facilityType.getDescription())
                .isActive(facilityType.getIsActive())
                .residenceCount(residenceCount)
                .build();
    }


    // =========================================================================
    // HELPERS — PARAMÈTRES FINANCIERS
    // =========================================================================

    /**
     * Construit le DTO des valeurs par défaut, identiques à celles
     * pré-remplies sur la maquette (FCFA, TRIMESTRIEL, 1.5%, 30j, 5%).
     */
    private SyndicFinancialSettingsDTO getDefaultSettings() {
        return SyndicFinancialSettingsDTO.builder()
                .currency(Currency.FCFA)
                .chargeFrequency(ChargeFrequency.TRIMESTRIEL)
                .latePenaltyRate(BigDecimal.valueOf(1.5))
                .reminderDelayDays(30)
                .penaltyGracePeriodDays(15)
                .reserveFundPercentage(BigDecimal.valueOf(5))
                .build();
    }

    private SyndicFinancialSettingsDTO mapToDTO(SyndicFinancialSettings settings) {
        return SyndicFinancialSettingsDTO.builder()
                .currency(settings.getCurrency())
                .chargeFrequency(settings.getChargeFrequency())
                .latePenaltyRate(settings.getLatePenaltyRate())
                .reminderDelayDays(settings.getReminderDelayDays())
                .penaltyGracePeriodDays(settings.getPenaltyGracePeriodDays())
                .reserveFundPercentage(settings.getReserveFundPercentage())
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
