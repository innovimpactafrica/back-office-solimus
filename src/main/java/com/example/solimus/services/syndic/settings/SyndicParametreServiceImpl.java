package com.example.solimus.services.syndic.settings;

import com.example.solimus.dtos.syndic.settings.CreateFacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreatePropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.CreateSpecialtyDTO;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.dtos.syndic.settings.PropertyTypeDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.entities.FacilityType;
import com.example.solimus.entities.PropertyType;
import com.example.solimus.entities.Specialty;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.CommonFacilityRepository;
import com.example.solimus.repositories.FacilityTypeRepository;
import com.example.solimus.repositories.PropertyTypeRepository;
import com.example.solimus.repositories.SpecialtyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicParametreServiceImpl implements SyndicParametreService {

    private final FacilityTypeRepository facilityTypeRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final SpecialtyRepository specialtyRepository;
    private final PropertyTypeRepository propertyTypeRepository;

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

    //Création
    @Override
    @Transactional
    public void createFacilityType(CreateFacilityTypeDTO dto) {

        // Vérifier l'unicité du nom
        if (facilityTypeRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Un type d'équipement avec ce nom existe déjà");
        }

        FacilityType facilityType = new FacilityType();
        facilityType.setName(dto.getName());
        facilityType.setCategory(dto.getCategory());
        facilityType.setIcon(dto.getIcon());
        facilityType.setDescription(dto.getDescription());
        facilityType.setIsActive(dto.getIsActive() != null ? dto.getIsActive() : true);

        FacilityType saved = facilityTypeRepository.save(facilityType);
        log.info("Type d'équipement créé : {}", saved.getName());
    }

    //Modification
    @Override
    @Transactional
    public void updateFacilityType(Long id, CreateFacilityTypeDTO dto) {
        
        //Récupérer le type d'équipement
        FacilityType facilityType = facilityTypeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Type d'équipement introuvable"));

        // Vérifier l'unicité du nom si modifié
        if (dto.getName() != null
                && !facilityType.getName().equalsIgnoreCase(dto.getName())
                && facilityTypeRepository.existsByNameIgnoreCase(dto.getName())) {
            throw new BadRequestException("Un type d'équipement avec ce nom existe déjà");
        }

        if (dto.getName() != null) facilityType.setName(dto.getName());
        if (dto.getCategory() != null) facilityType.setCategory(dto.getCategory());
        if (dto.getIcon() != null) facilityType.setIcon(dto.getIcon());
        if (dto.getDescription() != null) facilityType.setDescription(dto.getDescription());
        if (dto.getIsActive() != null) facilityType.setIsActive(dto.getIsActive());

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
}
