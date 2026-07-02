package com.example.solimus.services.syndic.residence;

import com.example.solimus.dtos.syndic.residence.*;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.entities.*;
import com.example.solimus.entities.FacilityType;
import com.example.solimus.entities.PropertyType;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicResidenceServiceImpl implements SyndicResidenceService {

    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final CommonFacilityRepository facilityRepository;
    private final FacilityTypeRepository facilityTypeRepository;
    private final PropertyTypeRepository propertyTypeRepository;
    private final ResidenceContactRepository contactRepository;
    private final ChargeAllocationRepository allocationRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final SyndicCoOwnerRelationRepository syndicCoOwnerRelationRepository;
    private final SecurityFeatureRepository securityFeatureRepository;
    // =========================================================================
    // ÉTAPE 1 — CRÉER LA RÉSIDENCE COMPLÈTE (avec photo et contacts)
    // =========================================================================
    @Override
    @Transactional
    public ResidenceDTO createResidenceComplete(CreateResidenceDTO dto, MultipartFile photo) {

        // 1. Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // 2. Construire l'adresse complète
        String adresseComplete = dto.getFullAddress()
            + ", " + dto.getCity()
            + ", " + dto.getCountry();

        // 3. Créer et sauvegarder l'entité Residence
        Residence residence = new Residence();
        residence.setName(dto.getName());
        residence.setDescription(dto.getDescription());
        residence.setFullAddress(adresseComplete);
        residence.setCity(dto.getCity());
        residence.setCountry(dto.getCountry());
        residence.setLatitude(dto.getLatitude());
        residence.setLongitude(dto.getLongitude());
        residence.setConstructionYear(dto.getConstructionYear());
        residence.setRenovationYear(dto.getRenovationYear());
        residence.setHealthStatus(ResidenceHealthStatus.EXCELLENT);
        residence.setSyndic(currentSyndic);

        Residence saved = residenceRepository.save(residence);

        // 4. Uploader la photo vers MinIO et l'associer à la résidence
        String photoUrl = minioService.uploadFile(photo, "residences");
        saved.setPhotoUrl(photoUrl);
        saved = residenceRepository.save(saved);

        // 5. Créer les contacts liés à cette résidence
        if (dto.getContacts() != null && !dto.getContacts().isEmpty()) {
            for (ContactInputDTO contactDto : dto.getContacts()) {
                ResidenceContact contact = new ResidenceContact();
                contact.setFullName(contactDto.getFullName());
                contact.setRole(contactDto.getRole());
                contact.setEmail(contactDto.getEmail());
                contact.setPhone(contactDto.getPhone());
                contact.setResidence(saved);
                contactRepository.save(contact);
            }
        }

        log.info("Résidence '{}' créée par le syndic {} avec {} contacts",
            saved.getName(), currentSyndic.getEmail(),
            dto.getContacts() != null ? dto.getContacts().size() : 0);

        // 6. Retourner le DTO de la résidence créée
        return mapToResidenceDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 2 — AJOUTER UN LOT / APPARTEMENT à une résidence géré par le syndic
    // =========================================================================
    @Override
    @Transactional
    public PropertyDTO addProperty(Long residenceId, AddPropertyDTO dto) {

        // Récupère une résidence ou lève une exception si introuvable
        Residence residence = getResidenceOrThrow(residenceId);

        // Vérifie que la résidence appartient au syndic connecté
        verifyResidenceOwnership(residence);

        Property property = new Property();
        property.setReference(dto.getReference());
        property.setBloc(dto.getBloc());
        property.setFloor(dto.getFloor());
        property.setSuperficie(dto.getSuperficie());
        property.setTantieme(dto.getTantieme());
        property.setResidence(residence);

        // Récupérer le type de bien
        PropertyType propertyType = propertyTypeRepository.findById(dto.getPropertyTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Type de bien introuvable"));
        property.setTypeBien(propertyType);

        // Assigner un propriétaire si fourni et calculer le statut
        if (dto.getOwnerId() != null) {
            User owner = userRepository.findById(dto.getOwnerId())
                .orElseThrow(() -> new ResourceNotFoundException(
                    "Copropriétaire introuvable"));

            // Vérifier que c'est bien un copropriétaire
            if (!owner.getRole().getName().equals(ERole.ROLE_COPROPRIETAIRE)) {
                throw new BadRequestException(
                    "Seul un copropriétaire peut être propriétaire d'un lot");
            }

            // Vérifier que le compte est actif
            if (owner.getStatus() != UserStatus.ACTIVE) {
                throw new BadRequestException(
                    "Le copropriétaire doit avoir un compte actif");
            }

            property.setOwner(owner);
            property.setStatus(PropertyStatus.OCCUPE);
        } else {
            property.setStatus(PropertyStatus.VACANT);
        }

        Property saved = propertyRepository.save(property);

        log.info("Lot '{}' ajouté à la résidence '{}'",
            saved.getReference(), residence.getName());

        return mapToPropertyDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 2 — MODIFIER UN LOT / APPARTEMENT
    // =========================================================================
    @Override
    @Transactional
    public PropertyDTO updateProperty(Long residenceId, Long propertyId, UpdatePropertyDTO dto) {

        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Récupérer le bien
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Vérifier que le bien appartient à la résidence
        if (!property.getResidence().getId().equals(residenceId)) {
            throw new BadRequestException("Ce bien n'appartient pas à cette résidence");
        }

        // Mettre à jour uniquement les champs non-null (mise à jour partielle)
        if (dto.getReference() != null) {
            property.setReference(dto.getReference());
        }
        if (dto.getBloc() != null) {
            property.setBloc(dto.getBloc());
        }
        if (dto.getFloor() != null) {
            property.setFloor(dto.getFloor());
        }
        if (dto.getSuperficie() != null) {
            property.setSuperficie(dto.getSuperficie());
        }
        if (dto.getTantieme() != null) {
            property.setTantieme(dto.getTantieme());
        }

        // Mettre à jour le type de bien si fourni
        if (dto.getPropertyTypeId() != null) {
            PropertyType propertyType = propertyTypeRepository.findById(dto.getPropertyTypeId())
                    .orElseThrow(() -> new ResourceNotFoundException("Type de bien introuvable"));
            property.setTypeBien(propertyType);
        }

        // Mettre à jour le propriétaire et recalculer le statut si ownerId fourni
        if (dto.getOwnerId() != null) {
            User owner = userRepository.findById(dto.getOwnerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

            // Vérifier que c'est bien un copropriétaire
            if (!owner.getRole().getName().equals(ERole.ROLE_COPROPRIETAIRE)) {
                throw new BadRequestException("Seul un copropriétaire peut être propriétaire d'un lot");
            }

            // Vérifier que le compte est actif
            if (owner.getStatus() != UserStatus.ACTIVE) {
                throw new BadRequestException("Le copropriétaire doit avoir un compte actif");
            }

            property.setOwner(owner);
            property.setStatus(PropertyStatus.OCCUPE);
        }

        Property saved = propertyRepository.save(property);

        log.info("Lot '{}' modifié dans la résidence '{}'",
                saved.getReference(), residence.getName());

        return mapToPropertyDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 2 — SUPPRIMER UN LOT / APPARTEMENT
    // =========================================================================
    @Override
    @Transactional
    public void deleteProperty(Long residenceId, Long propertyId) {

        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Récupérer le bien
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Vérifier que le bien appartient à la résidence
        if (!property.getResidence().getId().equals(residenceId)) {
            throw new BadRequestException("Ce bien n'appartient pas à cette résidence");
        }

        // Vérifier si le bien a un historique financier (ChargeAllocation)
        long allocationCount = allocationRepository.countByPropertyId(propertyId);
        if (allocationCount > 0) {
            throw new BadRequestException(
                "Impossible de supprimer ce lot car il est lié à un historique financier (charges).");
        }

        propertyRepository.delete(property);

        log.info("Lot '{}' supprimé de la résidence '{}'",
                property.getReference(), residence.getName());
    }

    // =========================================================================
    // ÉTAPE 2 — LISTER LES LOTS D'UNE RÉSIDENCE (PAGINÉ)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<PropertyListDTO> getPropertiesPaginated(Long residenceId, Integer page, Integer size) {

        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Construire Pageable manuellement
        Pageable pageable = PageRequest.of(page, size);

        // Récupérer les lots paginés
        Page<Property> propertiesPage = propertyRepository.findByResidenceId(residenceId, pageable);

        // Mapper vers DTO
        return propertiesPage.map(this::mapToPropertyListDTO);
    }

    // =========================================================================
    // ÉTAPE 2 - LISTER LES TYPES DE BIENS (pour dropdown lors de la création d'un lot)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<PropertyTypeDTO> getAllPropertyTypes() {
        return propertyTypeRepository.findAll()
                .stream()
                .map(type -> PropertyTypeDTO.builder()
                        .id(type.getId())
                        .name(type.getName())
                        .build())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // ÉTAPE 2 - LISTER LES COPROPRIÉTAIRES POUR L'AFFECTATION D'UN LOT
    // =========================================================================
    @Override
    public List<CoOwnerSelectionDTO> searchCoOwnersForSelection(String search) {

        // Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Normaliser le terme de recherche
        String normalizedSearch = search != null && !search.isBlank()
                ? search.trim()
                : null;

        // Récupérer les copropriétaires liés à ce syndic via SyndicCoOwnerRelation avec filtre de recherche
        // Uniquement ceux qui ont au moins un bien (car pas de lots = pas propriétaire)
        return syndicCoOwnerRelationRepository
                .findCoOwnersWithPropertiesBySyndicIdWithSearch(currentSyndic.getId(), normalizedSearch)
                .stream()
                .map(this::mapToCoOwnerSelectionDTO)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // ÉTAPE 3 — AJOUTER OU METTRE À JOUR UN ÉQUIPEMENT COMMUN
    // =========================================================================
    @Override
    @Transactional
    public void addFacility(Long residenceId, AddFacilityDTO dto) {

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier cette résidence");
        }

        // Récupérer le type d'équipement
        FacilityType facilityType = facilityTypeRepository.findById(dto.getFacilityTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Type d'équipement introuvable"));

        // Vérifier si un équipement de ce type existe déjà pour cette résidence
        CommonFacility facility = facilityRepository
                .findByResidenceIdAndFacilityTypeId(residenceId, dto.getFacilityTypeId())
                .orElse(new CommonFacility());

        // Créer ou mettre à jour l'équipement
        facility.setFacilityType(facilityType);
        facility.setResidence(residence);

        // Copier les champs pertinents selon le type
        facility.setCount(dto.getCount());
        facility.setIsHeated(dto.getIsHeated());
        facility.setCapacity(dto.getCapacity());
        facility.setFloorsCovered(dto.getFloorsCovered());
        facility.setSuperficie(dto.getSuperficie());
        facility.setEtat(dto.getEtat());
        facility.setIndoorSpots(dto.getIndoorSpots());
        facility.setOutdoorSpots(dto.getOutdoorSpots());
        facility.setChargingStations(dto.getChargingStations());
        facility.setPowerKva(dto.getPowerKva());
        facility.setFuelType(dto.getFuelType());
        facility.setCapacityLiters(dto.getCapacityLiters());
        facility.setPumpStatus(dto.getPumpStatus());

        facilityRepository.save(facility);
        log.info("Équipement '{}' sauvegardé pour la résidence '{}'",
                facilityType.getName(), residence.getName());
    }

    // =========================================================================
    // ÉTAPE 3 — METTRE À JOUR LES OPTIONS DE SÉCURITÉ D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    @Transactional
    public void updateSecurityFeatures(Long residenceId, UpdateSecurityFeaturesDTO dto) {

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier cette résidence");
        }

        // Récupérer les options de sécurité à partir des IDs
        List<SecurityFeature> securityFeatures = securityFeatureRepository.findAllById(
                dto.getSecurityFeatureIds() != null ? dto.getSecurityFeatureIds() : List.of()
        );

        // Remplacer la liste complète des options de sécurité
        residence.setSecurityFeatures(securityFeatures);
        residenceRepository.save(residence);

        log.info("Options de sécurité mises à jour pour la résidence '{}' ({} options)",
                residence.getName(), securityFeatures.size());
    }

    // =========================================================================
    // ÉTAPE 3 — RÉCUPÉRER LES TYPES D'ÉQUIPEMENTS AVEC LEURS CHAMPS
    // =========================================================================

    // map qui associe chaque nom de type à ses champs — définie une seule fois
    private static final Map<String, List<String>> FACILITY_FIELDS = Map.of(
            "Piscine",            List.of("count", "isHeated"),
            "Ascenseur",          List.of("count", "capacity"),
            "Couloir",            List.of("count", "floorsCovered"),
            "Jardin",             List.of("superficie", "etat"),
            "Parking",            List.of("indoorSpots", "outdoorSpots", "chargingStations"),
            "Groupe électrogène", List.of("powerKva", "fuelType"),
            "Réservoir d'eau",    List.of("capacityLiters", "pumpStatus")
    );

    @Override
    @Transactional(readOnly = true)
    public List<FacilityTypeDTO> getFacilityTypes() {

        return facilityTypeRepository.findByIsActiveTrue()
                .stream()
                .map(type -> FacilityTypeDTO.builder()
                        .id(type.getId())
                        .name(type.getName())
                        .icon(type.getIcon())
                        // on cherche les champs dans la map — liste vide si type inconnu
                        .fields(FACILITY_FIELDS.getOrDefault(type.getName(), List.of()))
                        .build())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================
    @Override
    public List<ResidenceDTO> getMesResidences() {
        User currentSyndic = getCurrentUser();
        return residenceRepository.findAllBySyndicId(currentSyndic.getId())
            .stream()
            .map(this::mapToResidenceDTO)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // DÉTAIL D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    public ResidenceDTO getResidenceDetail(Long residenceId) {
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);
        return mapToResidenceDTOWithKPIs(residence);
    }

    // =========================================================================
    // MÉTHODES PRIVÉES UTILITAIRES
    // =========================================================================

    // Récupère une résidence ou lève une exception si introuvable
    private Residence getResidenceOrThrow(Long residenceId) {
        return residenceRepository.findById(residenceId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Résidence introuvable"));
    }

    // Vérifie que la résidence appartient au syndic connecté
    private void verifyResidenceOwnership(Residence residence) {
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException(
                "Vous n'êtes pas autorisé à modifier cette résidence");
        }
    }

    // Récupère l'utilisateur connecté depuis le token JWT
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Utilisateur introuvable"));
    }

    // DTO simple — pour la liste
    private ResidenceDTO mapToResidenceDTO(Residence res) {
        return ResidenceDTO.builder()
            .id(res.getId())
            .name(res.getName())
            .description(res.getDescription())
            .photoUrl(res.getPhotoUrl())
            .fullAddress(res.getFullAddress())
            .city(res.getCity())
            .country(res.getCountry())
            .latitude(res.getLatitude())
            .longitude(res.getLongitude())
            .lotsCount(res.getLotsCount())
            .constructionYear(res.getConstructionYear())
            .renovationYear(res.getRenovationYear())
            .annualBudget(res.getAnnualBudget())
            .healthStatus(res.getHealthStatus())
            .syndicId(res.getSyndic() != null
                ? res.getSyndic().getId() : null)
            .syndicName(res.getSyndic() != null
                ? res.getSyndic().getFirstName() + " "
                    + res.getSyndic().getLastName() : null)
            .createdAt(res.getCreatedAt())
            .updatedAt(res.getUpdatedAt())
            .build();
    }

    // DTO avec KPIs calculés — pour le détail
    private ResidenceDTO mapToResidenceDTOWithKPIs(Residence res) {

        ResidenceDTO dto = mapToResidenceDTO(res);

        // Nombre de copropriétaires
        int totalCopros = (int) res.getProperties().stream()
            .filter(p -> p.getOwner() != null)
            .count();

        // Incidents ouverts (non résolus)
        long incidentsOuverts = res.getInterventionRequests().stream()
            .filter(i -> i.getStatus() != InterventionStatus.FINAL_VALIDATION
                      && i.getStatus() != InterventionStatus.CANCELLED)
            .count();

        // Taux d'impayés
        long totalAllocations = allocationRepository
            .countByChargeResidenceId(res.getId());
        long allocationsEnRetard = allocationRepository
            .countByChargeResidenceIdAndStatus(
                res.getId(), ChargeStatus.EN_RETARD);

        double tauxImpayes = totalAllocations > 0
            ? (double) allocationsEnRetard / totalAllocations * 100
            : 0.0;

        // Trésorerie — total des charges payées
        BigDecimal tresorerie = allocationRepository
            .sumByResidenceIdAndStatus(res.getId(), ChargeStatus.PAYEE);

        // Mettre à jour les KPIs dans le DTO
        dto.setTotalCoproprietaires(totalCopros);
        dto.setIncidentsOuverts((int) incidentsOuverts);
        dto.setTauxImpayes(Math.round(tauxImpayes * 10.0) / 10.0);
        dto.setTresorerie(tresorerie != null ? tresorerie : BigDecimal.ZERO);

        // Recalculer le healthStatus
        dto.setHealthStatus(calculerStatutSante(tauxImpayes, 0));

        return dto;
    }

    private ResidenceHealthStatus calculerStatutSante(
            double tauxImpayes, int incidentsCritiques) {
        if (tauxImpayes > 10.0 || incidentsCritiques > 0) {
            return ResidenceHealthStatus.CRITIQUE;
        } else if (tauxImpayes > 5.0) {
            return ResidenceHealthStatus.ATTENTION;
        }
        return ResidenceHealthStatus.EXCELLENT;
    }

    private PropertyDTO mapToPropertyDTO(Property p) {
        return PropertyDTO.builder()
            .id(p.getId())
            .reference(p.getReference())
            .floor(p.getFloor())
            .superficie(p.getSuperficie())
            .typeName(p.getTypeBien() != null ? p.getTypeBien().getName() : null)
            .residenceId(p.getResidence().getId())
            .residenceName(p.getResidence().getName())
            .ownerId(p.getOwner() != null ? p.getOwner().getId() : null)
            .ownerName(p.getOwner() != null
                ? p.getOwner().getFirstName() + " " + p.getOwner().getLastName()
                : null)
            .build();
    }

    private PropertyListDTO mapToPropertyListDTO(Property p) {
        return PropertyListDTO.builder()
            .id(p.getId())
            .reference(p.getReference())
            .bloc(p.getBloc())
            .floor(p.getFloor())
            .typeName(p.getTypeBien() != null ? p.getTypeBien().getName() : null)
            .superficie(p.getSuperficie())
            .tantieme(p.getTantieme())
            .build();
    }

    private CoOwnerSelectionDTO mapToCoOwnerSelectionDTO(User user) {
        return CoOwnerSelectionDTO.builder()
            .id(user.getId())
            .firstName(user.getFirstName())
            .lastName(user.getLastName())
            .fullName(user.getFirstName() + " " + user.getLastName())
            .email(user.getEmail())
            .profilePhotoUrl(user.getProfilePhotoUrl())
            .ownedPropertiesCount(propertyRepository.countByOwnerId(user.getId()))
            .build();
    }


}
