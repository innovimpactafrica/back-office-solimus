package com.example.solimus.services.syndic.settings;

import com.example.solimus.dtos.syndic.settings.ChangePasswordDTO;
import com.example.solimus.dtos.syndic.settings.CreateFacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreatePropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreateSpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.PropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.SyndicFinancialSettingsDTO;
import com.example.solimus.dtos.syndic.settings.SyndicProfileDTO;
import com.example.solimus.dtos.syndic.settings.UpdateSyndicProfileDTO;
import com.example.solimus.entities.FacilityType;
import com.example.solimus.entities.PropertyType;
import com.example.solimus.entities.Specialty;
import com.example.solimus.entities.SyndicFinancialSettings;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.Currency;
import com.example.solimus.enums.FacilityCategory;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.CommonFacilityRepository;
import com.example.solimus.repositories.FacilityTypeRepository;
import com.example.solimus.repositories.PropertyTypeRepository;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.repositories.SyndicFinancialSettingsRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SyndicFinancialSettingsRepository syndicFinancialSettingsRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MinioService minioService;

    //--------------------------------------------------------
    // ===== TYPES D'ÉQUIPEMENTS =====
    //--------------------------------------------------------

    //Listing
    @Override
    @Transactional(readOnly = true)
    public List<FacilityTypeDTO> getAllFacilityTypes() {
        return facilityTypeRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createFacilityType(String name, FacilityCategory category, String description, Boolean isActive, MultipartFile icon) {

        if (facilityTypeRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Un type d'équipement avec ce nom existe déjà");
        }

        FacilityType facilityType = new FacilityType();
        facilityType.setName(name);
        facilityType.setCategory(category);
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
    //Modification
    @Override
    @Transactional
    public void updateFacilityType(Long id, String name, FacilityCategory category, String description, Boolean isActive, MultipartFile icon) {

        //Récupérer le type d'équipement
        FacilityType facilityType = facilityTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type d'équipement introuvable"));

        // Vérifier l'unicité du nom si modifié
        if (name != null
                && !facilityType.getName().equalsIgnoreCase(name)
                && facilityTypeRepository.existsByNameIgnoreCase(name)) {
            throw new BadRequestException("Un type d'équipement avec ce nom existe déjà");
        }

        if (name != null) facilityType.setName(name);
        if (category != null) facilityType.setCategory(category);
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

        //Récupérer le type d'équipement
        FacilityType facilityType = facilityTypeRepository.findById(id)
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
    public List<SpecialtyDTO> getAllSpecialties() {
        return specialtyRepository.findAll().stream()
                .map(s -> new SpecialtyDTO(s.getId(), s.getName(), s.getDescription(), s.getIcon()))
                .collect(Collectors.toList());
    }

    //Création
    @Override
    @Transactional
    public void createSpecialty(CreateSpecialtyDTO dto) {
        if (specialtyRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("La spécialité '" + dto.getName() + "' existe déjà.");
        }
        Specialty specialty = new Specialty();
        specialty.setName(dto.getName());
        specialty.setDescription(dto.getDescription());
        specialty.setIcon(dto.getIcon());
        specialtyRepository.save(specialty);
        log.info("Spécialité créée : {}", dto.getName());
    }

    //Modification
    @Override
    @Transactional
    public void updateSpecialty(Long id, CreateSpecialtyDTO dto) {
        //Vérifier que la spécialité existe
        Specialty specialty = specialtyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité non trouvée"));
        //Vérifier que le nom de la spécialité n'est pas déjà utilisé
        if (dto.getName() != null
                && !specialty.getName().equalsIgnoreCase(dto.getName())
                && specialtyRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("La spécialité '" + dto.getName() + "' existe déjà.");
        }
        if (dto.getName() != null) specialty.setName(dto.getName());
        if (dto.getDescription() != null) specialty.setDescription(dto.getDescription());
        if (dto.getIcon() != null) specialty.setIcon(dto.getIcon());
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

    //Listing
    @Override
    @Transactional(readOnly = true)
    public List<PropertyTypeDTO> getAllPropertyTypes() {
        return propertyTypeRepository.findAll().stream()
                .map(pt -> PropertyTypeDTO.builder()
                        .id(pt.getId())
                        .name(pt.getName())
                        .description(pt.getDescription())
                        .build())
                .collect(Collectors.toList());
    }

    //Création
    @Override
    @Transactional
    public void createPropertyType(CreatePropertyTypeDTO dto) {
        if (propertyTypeRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Un type d'appartement avec ce nom existe déjà");
        }
        PropertyType propertyType = new PropertyType();
        propertyType.setName(dto.getName());
        propertyType.setDescription(dto.getDescription());
        propertyTypeRepository.save(propertyType);
        log.info("Type d'appartement créé : {}", dto.getName());
    }

    //Modification
    @Override
    @Transactional
    public void updatePropertyType(Long id, CreatePropertyTypeDTO dto) {
        PropertyType propertyType = propertyTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type d'appartement introuvable"));
        if (dto.getName() != null
                && !propertyType.getName().equalsIgnoreCase(dto.getName())
                && propertyTypeRepository.existsByNameIgnoreCase(dto.getName())) {
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
        if (!propertyTypeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Type d'appartement introuvable");
        }
        propertyTypeRepository.deleteById(id);
        log.info("Type d'appartement supprimé : id={}", id);
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
    public void saveFinancialSettings(SyndicFinancialSettingsDTO dto) {

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
    public void updateSyndicProfile(UpdateSyndicProfileDTO dto) {
        User currentSyndic = getCurrentUser();

        if (dto.getFirstName() != null) {
            currentSyndic.setFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            currentSyndic.setLastName(dto.getLastName());
        }
        if (dto.getPhone() != null) {
            currentSyndic.setPhone(dto.getPhone());
        }
        if (dto.getPhotoUrl() != null) {
            currentSyndic.setProfilePhotoUrl(dto.getPhotoUrl());
        }

        userRepository.save(currentSyndic);
        log.info("Profil syndic mis à jour : {}", currentSyndic.getEmail());
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
                .reserveFundPercentage(BigDecimal.valueOf(5))
                .build();
    }

    private SyndicFinancialSettingsDTO mapToDTO(SyndicFinancialSettings settings) {
        return SyndicFinancialSettingsDTO.builder()
                .currency(settings.getCurrency())
                .chargeFrequency(settings.getChargeFrequency())
                .latePenaltyRate(settings.getLatePenaltyRate())
                .reminderDelayDays(settings.getReminderDelayDays())
                .reserveFundPercentage(settings.getReserveFundPercentage())
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
