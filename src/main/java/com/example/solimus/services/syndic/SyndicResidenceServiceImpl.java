package com.example.solimus.services.syndic;

import com.example.solimus.dtos.residence.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicResidenceServiceImpl implements SyndicResidenceService {

    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final CommonFacilityRepository facilityRepository;
    private final ResidenceContactRepository contactRepository;
    private final ChargeAllocationRepository allocationRepository;
    private final InterventionRequestRepository interventionRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final SecurityFeatureRepository securityFeatureRepository;

    // =========================================================================
    // ÉTAPE 1 — CRÉER LA RÉSIDENCE
    // =========================================================================
    @Override
    @Transactional
    public ResidenceDTO createResidence(CreateResidenceDTO dto) {

        //Récupérer le syndic connecté
        User currentSyndic = getCurrentUser();

        // Construire l'adresse complète côté back
        // Le front envoie fullAddress, city et country séparément
        String adresseComplete = dto.getFullAddress()
            + ", " + dto.getCity()
            + ", " + dto.getCountry();

        Residence residence = new Residence();
        residence.setName(dto.getName());
        residence.setDescription(dto.getDescription());
        residence.setFullAddress(adresseComplete);
        residence.setCity(dto.getCity());
        residence.setCountry(dto.getCountry());
        residence.setLatitude(dto.getLatitude());
        residence.setLongitude(dto.getLongitude());
        residence.setLotsCount(dto.getLotsCount());
        residence.setConstructionYear(dto.getConstructionYear());
        residence.setRenovationYear(dto.getRenovationYear());
        residence.setAnnualBudget(dto.getAnnualBudget());
        residence.setHealthStatus(ResidenceHealthStatus.EXCELLENT);
        residence.setSyndic(currentSyndic);

        Residence saved = residenceRepository.save(residence);

        log.info("Résidence '{}' créée par le syndic {}",
            saved.getName(), currentSyndic.getEmail());

        return mapToResidenceDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 1 — UPLOADER LA PHOTO
    // =========================================================================
    @Override
    @Transactional
    public ResidenceDTO uploadPhoto(Long residenceId, MultipartFile photo) {

        // Récupère une résidence ou lève une exception si introuvable
        Residence residence = getResidenceOrThrow(residenceId);

        // Vérifie que la résidence appartient au syndic connecté
        verifyResidenceOwnership(residence);

        // Upload vers Minio
        String photoUrl = minioService.uploadFile(photo, "residences");
        residence.setPhotoUrl(photoUrl);

        return mapToResidenceDTO(residenceRepository.save(residence));
    }

    // =========================================================================
    // ÉTAPE 1 AJOUTER UN CONTACT CLÉ
    // =========================================================================
    @Override
    @Transactional
    public AddResidenceContactDTO addContact(
            Long residenceId, AddResidenceContactDTO dto) {

        //Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);

        //Vérifier si elle est géré par le Syndic
        verifyResidenceOwnership(residence);

        ResidenceContact contact = new ResidenceContact();
        contact.setFullName(dto.getFullName());
        contact.setRole(dto.getRole());
        contact.setEmail(dto.getEmail());
        contact.setPhone(dto.getPhone());
        contact.setPhotoUrl(dto.getPhotoUrl());
        contact.setResidence(residence);

        contactRepository.save(contact);

        return dto;
    }

    // =========================================================================
    // ÉTAPE 2 — AJOUTER UN LOT / APPARTEMENT
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
        property.setFloor(dto.getFloor());
        property.setSuperficie(dto.getSuperficie());
        property.setTypeBien(dto.getTypeBien());
        property.setResidence(residence);

        // Assigner un propriétaire si fourni
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
        }

        Property saved = propertyRepository.save(property);

        log.info("Lot '{}' ajouté à la résidence '{}'",
            saved.getReference(), residence.getName());

        return mapToPropertyDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 2 - LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    public List<PropertyListDTO> getPropertiesByResidence(Long residenceId) {
        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        // Vérifier si elle est géré par le Syndic
        verifyResidenceOwnership(residence);

        return propertyRepository.findByResidenceId(residenceId)
                .stream()
                .map(this::mapToPropertyListDTO)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // ÉTAPE 2 - COMPTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    public long countPropertiesByResidence(Long residenceId) {
        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        // Vérifier si elle est géré par le Syndic
        verifyResidenceOwnership(residence);

        return propertyRepository.countByResidenceId(residenceId);
    }

    // =========================================================================
    // ÉTAPE 2 - MODIFIER UN LOT D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    public PropertyListDTO updateProperty(Long residenceId, Long propertyId, UpdatePropertyDTO dto) {
        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        // Vérifier si elle est géré par le Syndic
        verifyResidenceOwnership(residence);

        // Récupérer le bien
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Vérifier que le bien appartient à la résidence
        if (!property.getResidence().getId().equals(residenceId)) {
            throw new BadRequestException("Ce bien n'appartient pas à cette résidence");
        }

        // Mettre à jour les champs uniquement si non null
        if (dto.getReference() != null) {
            property.setReference(dto.getReference());
        }
        if (dto.getBloc() != null) {
            property.setBloc(dto.getBloc());
        }
        if (dto.getFloor() != null) {
            property.setFloor(dto.getFloor());
        }
        if (dto.getTypeBien() != null) {
            property.setTypeBien(dto.getTypeBien());
        }
        if (dto.getSuperficie() != null) {
            property.setSuperficie(dto.getSuperficie());
        }
        if (dto.getTantieme() != null) {
            property.setTantieme(dto.getTantieme());
        }

        Property saved = propertyRepository.save(property);

        log.info("Lot '{}' modifié dans la résidence '{}'", saved.getReference(), residence.getName());

        return mapToPropertyListDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 2 - SUPPRIMER UN LOT D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    public void deleteProperty(Long residenceId, Long propertyId) {
        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        // Vérifier si elle est géré par le Syndic
        verifyResidenceOwnership(residence);

        // Récupérer le bien
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Vérifier que le bien appartient à la résidence
        if (!property.getResidence().getId().equals(residenceId)) {
            throw new BadRequestException("Ce bien n'appartient pas à cette résidence");
        }

        // Vérifier que le bien n'a pas de propriétaire
        if (property.getOwner() != null) {
            throw new BadRequestException("Impossible de supprimer un lot qui a un propriétaire");
        }

        propertyRepository.delete(property);

        log.info("Lot '{}' supprimé de la résidence '{}'", property.getReference(), residence.getName());
    }

    // =========================================================================
    // ÉTAPE 2 - RETIRER UN COPROPRIÉTAIRE D'UN LOT
    // =========================================================================
    @Override
    public PropertyListDTO removeOwnerFromProperty(Long residenceId, Long propertyId) {
        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        // Vérifier si elle est géré par le Syndic
        verifyResidenceOwnership(residence);

        // Récupérer le bien
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Vérifier que le bien appartient à la résidence
        if (!property.getResidence().getId().equals(residenceId)) {
            throw new BadRequestException("Ce bien n'appartient pas à cette résidence");
        }

        // Vérifier que le bien a un propriétaire
        if (property.getOwner() == null) {
            throw new BadRequestException("Ce lot n'a pas de propriétaire");
        }

        // Retirer le propriétaire
        property.setOwner(null);
        // Mettre le statut à VACANT
        property.setStatus(PropertyStatus.VACANT);

        Property saved = propertyRepository.save(property);

        log.info("Propriétaire retiré du lot '{}' dans la résidence '{}'", saved.getReference(), residence.getName());

        return mapToPropertyListDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 2 - AFFECTER UN COPROPRIÉTAIRE À UN LOT
    // =========================================================================
    @Override
    public PropertyListDTO assignOwnerToProperty(Long residenceId, Long propertyId, Long ownerId) {
        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        // Vérifier si elle est géré par le Syndic
        verifyResidenceOwnership(residence);

        // Récupérer le bien
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Vérifier que le bien appartient à la résidence
        if (!property.getResidence().getId().equals(residenceId)) {
            throw new BadRequestException("Ce bien n'appartient pas à cette résidence");
        }

        // Vérifier que le lot n'est pas déjà occupé par un autre copropriétaire
        if (property.getOwner() != null && !property.getOwner().getId().equals(ownerId)) {
            throw new BadRequestException("Ce lot est déjà affecté à un propriétaire");
        }

        // Récupérer le copropriétaire au cas où le lot n'est pas occupé
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

        // Vérifier que c'est bien un copropriétaire
        if (owner.getRole() == null || !owner.getRole().getName().equals(ERole.ROLE_COPROPRIETAIRE)) {
            throw new BadRequestException("Seul un copropriétaire peut être propriétaire d'un lot");
        }

        // Vérifier que le compte est actif
        if (owner.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Le copropriétaire doit avoir un compte actif");
        }

        property.setOwner(owner); // affecté le lot au propriétaire
        property.setStatus(PropertyStatus.OCCUPE); // Mettre le statut du lot à "occupé"

        Property saved = propertyRepository.save(property);

        log.info("Copropriétaire '{}' affecté au lot '{}' dans la résidence '{}'",
                owner.getEmail(), saved.getReference(), residence.getName());

        return mapToPropertyListDTO(saved);
    }

    // =========================================================================
    // ÉTAPE 2 - LISTER LES COPROPRIÉTAIRES POUR L'AFFECTATION D'UN LOT
    // =========================================================================
    @Override
    public List<CoOwnerSelectionDTO> searchCoOwnersForSelection(String search) {

        //Normaliser le terme de recherche
        String normalizedSearch = search != null && !search.isBlank()
                ? search.trim()
                : null;

        return userRepository.findActiveCoOwnersForSelection(normalizedSearch)
                .stream()
                .map(this::mapToCoOwnerSelectionDTO)
                .collect(Collectors.toList());
    }


    // =========================================================================
    // ÉTAPE 3 — AJOUTER UN ÉQUIPEMENT COMMUN
    // =========================================================================
    @Override
    @Transactional
    public AddFacilityDTO addFacility(Long residenceId, AddFacilityDTO dto) {

        // Récupérer la résidence 
        Residence residence = getResidenceOrThrow(residenceId);

        // Vérifier que le syndic est bien le propriétaire de la résidence
        verifyResidenceOwnership(residence);

        CommonFacility facility = new CommonFacility();
        facility.setFacilityType(dto.getFacilityType());
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

        CommonFacility saved = facilityRepository.save(facility);

        log.info("Équipement '{}' ajouté à la résidence '{}'",
            saved.getFacilityType(), residence.getName());

        return dto;
    }

    //=======================================================================
    // ÉTAPE 3 — LISTER LES OPTIONS DE SÉCURITÉ ACTIVES
    //========================================================================
    @Override
    public List<SecurityFeatureSimpleDTO> getActiveSecurityFeatures() {
        return securityFeatureRepository.findByActiveTrue()
                .stream()
                .map(this::mapToSecurityFeatureSimpleDTO)
                .collect(Collectors.toList());
    }

    //=======================================================================
    // ÉTAPE 3 — AJOUTER LES OPTIONS DE SÉCURITÉ À UNE RÉSIDENCE
    //========================================================================
    @Override
    @Transactional
    public void addSecurityFeatures(Long residenceId, AddSecurityFeaturesDTO dto) {

        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);

        // Vérifier que le syndic est bien le propriétaire de la résidence
        verifyResidenceOwnership(residence);

        // Récupérer les options de sécurité à partir des IDs
        List<SecurityFeature> features = securityFeatureRepository.findAllById(dto.getSecurityFeatureIds());

        // Vérifier que toutes les options existent
        // Le nombre d'options récupérées en base doit être égal au nombre d'IDs reçus. Sinon, cela signifie qu'au moins un ID envoyé par le front n'existe pas.
        if (features.size() != dto.getSecurityFeatureIds().size()) {
            throw new BadRequestException("Une ou plusieurs options de sécurité introuvables");
        }

        // Vérifier que toutes les options sont actives
        for (SecurityFeature feature : features) {
            if (!feature.isActive()) {
                throw new BadRequestException("L'option de sécurité '" + feature.getLabel() + "' n'est pas active");
            }
        }

        // Ajouter les options à la résidence
        residence.getSecurityFeatures().addAll(features);
        residenceRepository.save(residence);

        log.info("Options de sécurité ajoutées à la résidence '{}'", residence.getName());
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
            .superficie(p.getSuperficie())
            .type(p.getTypeBien())
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
            .typeBien(p.getTypeBien())
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


    private SecurityFeatureSimpleDTO mapToSecurityFeatureSimpleDTO(SecurityFeature feature) {
        return SecurityFeatureSimpleDTO.builder()
            .id(feature.getId())
            .label(feature.getLabel())
            .build();
    }
}
