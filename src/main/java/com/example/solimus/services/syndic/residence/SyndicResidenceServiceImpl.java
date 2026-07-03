package com.example.solimus.services.syndic.residence;

import com.example.solimus.dtos.syndic.residence.*;
import com.example.solimus.dtos.syndic.settings.FacilityTypeDTO;
import com.example.solimus.entities.*;
import com.example.solimus.entities.Budget;
import com.example.solimus.entities.ChargeCall;
import com.example.solimus.entities.ChargeCallItem;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
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
    private final InterventionRequestRepository interventionRequestRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final SyndicCoOwnerRelationRepository syndicCoOwnerRelationRepository;
    private final SecurityFeatureRepository securityFeatureRepository;
    private final BudgetRepository budgetRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final ChargeCallRepository chargeCallRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final InterventionStatusHistoryRepository interventionStatusHistoryRepository;
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
    // MODIFIER LES INFORMATIONS GÉNÉRALES D'UNE RÉSIDENCE (MISE À JOUR PARTIELLE)
    // =========================================================================
    @Override
    @Transactional
    public void updateResidence(Long residenceId, CreateResidenceDTO dto, MultipartFile photo) {

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier cette résidence");
        }

        // Mise à jour partielle des champs scalaires (uniquement si non null)
        if (dto.getName() != null) {
            residence.setName(dto.getName());
        }
        if (dto.getDescription() != null) {
            residence.setDescription(dto.getDescription());
        }
        if (dto.getFullAddress() != null) {
            String adresseComplete = dto.getFullAddress()
                    + ", " + (dto.getCity() != null ? dto.getCity() : residence.getCity())
                    + ", " + (dto.getCountry() != null ? dto.getCountry() : residence.getCountry());
            residence.setFullAddress(adresseComplete);
        }
        if (dto.getCity() != null) {
            residence.setCity(dto.getCity());
        }
        if (dto.getCountry() != null) {
            residence.setCountry(dto.getCountry());
        }
        if (dto.getLatitude() != null) {
            residence.setLatitude(dto.getLatitude());
        }
        if (dto.getLongitude() != null) {
            residence.setLongitude(dto.getLongitude());
        }
        if (dto.getConstructionYear() != null) {
            residence.setConstructionYear(dto.getConstructionYear());
        }
        if (dto.getRenovationYear() != null) {
            residence.setRenovationYear(dto.getRenovationYear());
        }

        // Gestion des contacts : remplacement complet si fourni, sinon rien
        if (dto.getContacts() != null) {
            // Supprimer tous les contacts existants
            contactRepository.deleteByResidenceId(residenceId);

            // Créer les nouveaux contacts
            for (ContactInputDTO contactDto : dto.getContacts()) {
                ResidenceContact contact = new ResidenceContact();
                contact.setFullName(contactDto.getFullName());
                contact.setRole(contactDto.getRole());
                contact.setEmail(contactDto.getEmail());
                contact.setPhone(contactDto.getPhone());
                contact.setResidence(residence);
                contactRepository.save(contact);
            }
        }

        // Gestion de la photo : upload si fournie, sinon conservation de l'existante
        if (photo != null && !photo.isEmpty()) {
            String photoUrl = minioService.uploadFile(photo, "residences");
            residence.setPhotoUrl(photoUrl);
        }

        residenceRepository.save(residence);

        log.info("Résidence '{}' modifiée par le syndic {} ({} contacts mis à jour)",
                residence.getName(), currentSyndic.getEmail(),
                dto.getContacts() != null ? dto.getContacts().size() : "aucun");
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

        // Vérifier si le bien a un historique financier (ChargeCallItem)
        // Remplace l'ancien système ChargeAllocation
        // On vérifie via le propriétaire actuel du lot et les appels de charges générés pour cette résidence
        // Si le propriétaire a des ChargeCallItem liés à des ChargeCall de cette résidence, on refuse la suppression
        if (property.getOwner() != null) {
            long chargeCallItemCount = chargeCallItemRepository.countByCoOwnerIdAndResidenceId(
                    property.getOwner().getId(), residenceId);
            if (chargeCallItemCount > 0) {
                throw new BadRequestException(
                    "Impossible de supprimer ce lot car il est lié à un historique financier (charges).");
            }
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
    // ÉTAPE 3 — SAUVEGARDER L'ÉTAPE 3 COMPLÈTE (ÉQUIPEMENTS + SÉCURITÉ)
    // =========================================================================
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

    @Override
    @Transactional
    public void saveStep3(Long residenceId, Step3DTO dto) {

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier cette résidence");
        }

        // 1. Traiter les équipements (boucle sur facilities)
        if (dto.getFacilities() != null) {
            for (AddFacilityDTO facilityDto : dto.getFacilities()) {
                addFacility(residenceId, facilityDto);
            }
        }

        // 2. Remplacer la liste des options de sécurité
        List<SecurityFeature> securityFeatures = securityFeatureRepository.findAllById(
                dto.getSecurityFeatureIds() != null ? dto.getSecurityFeatureIds() : List.of()
        );
        residence.setSecurityFeatures(securityFeatures);
        residenceRepository.save(residence);

        log.info("Étape 3 sauvegardée pour la résidence '{}' ({} équipements, {} options de sécurité)",
                residence.getName(),
                dto.getFacilities() != null ? dto.getFacilities().size() : 0,
                securityFeatures.size());
    }

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================

    // STATISTIQUES DU BANDEAU D'INDICATEURS
    @Override
    @Transactional(readOnly = true)
    public ResidenceHeaderStatsDTO getResidenceStats(Long residenceId) {

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // 1. Nombre total de lots
        long totalApartments = propertyRepository.countByResidenceId(residenceId);

        // 2. Nombre de propriétaires distincts
        long coOwnersCount = propertyRepository.countDistinctOwnersByResidenceId(residenceId);

        // 3. Budget annuel (budgetTotal du Budget le plus récent)
        BigDecimal annualBudget = budgetRepository.findMostRecentByResidenceId(residenceId)
                .map(Budget::getBudgetTotal)
                .orElse(null);

        // 4. Nombre d'interventions en cours (STARTED)
        long worksInProgress = interventionRequestRepository.countByResidenceIdAndStatus(
                residenceId, InterventionStatus.STARTED);

        // 4. Nombre d'interventions en attente (PENDING)
        long pendingQuotes = interventionRequestRepository.countByResidenceIdAndStatus(
                residenceId, InterventionStatus.PENDING);

        // 5. Nombre d'incidents ouverts (non clôturés ni annulés)
        long openIncidents = interventionRequestRepository.countOpenByResidenceId(residenceId);

        // 6. Calcul du statut de santé
        ResidenceHealthStatus healthStatus = calculateHealthStatus(residenceId);

        return ResidenceHeaderStatsDTO.builder()
                .name(residence.getName())
                .photoUrl(residence.getPhotoUrl())
                .fullAddress(residence.getFullAddress())
                .city(residence.getCity())
                .healthStatus(healthStatus)
                .totalApartments(totalApartments)
                .annualBudget(annualBudget)
                .coOwnersCount(coOwnersCount)
                .worksInProgress(worksInProgress)
                .pendingQuotes(pendingQuotes)
                .openIncidents(openIncidents)
                .build();
    }

    // CONTENU DE L'ONGLET VUE GÉNÉRALE d'une résidence spécifique
    @Override
    @Transactional(readOnly = true)
    public ResidenceDetailDTO getResidenceGeneralView(Long residenceId) {

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // Calculer le niveau de sécurité (concaténation des noms des SecurityFeature actives)
        String securityLevel = residence.getSecurityFeatures().stream()
                .filter(SecurityFeature::isActive)
                .map(SecurityFeature::getLabel)
                .collect(Collectors.joining(" & "));

        // Mapper les contacts clés
        List<ResidenceDetailDTO.KeyContactDTO> keyContacts = contactRepository.findByResidenceId(residenceId)
                .stream()
                .map(contact -> ResidenceDetailDTO.KeyContactDTO.builder()
                        .fullName(contact.getFullName())
                        .role(contact.getRole())
                        .email(contact.getEmail())
                        .phone(contact.getPhone())
                        .photo(contact.getPhotoUrl())
                        .build())
                .collect(Collectors.toList());

        return ResidenceDetailDTO.builder()
                .id(residence.getId())
                .description(residence.getDescription())
                .country(residence.getCountry())
                .latitude(residence.getLatitude() != null ? residence.getLatitude().doubleValue() : null)
                .longitude(residence.getLongitude() != null ? residence.getLongitude().doubleValue() : null)
                .constructionYear(residence.getConstructionYear())
                .renovationYear(residence.getRenovationYear())
                .securityLevel(securityLevel)
                .keyContacts(keyContacts)
                .build();
    }

    // =========================================================================
    // DASHBOARD RÉSIDENCES
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ResidenceDashboardStatsDTO getDashboardStats() {

        User currentSyndic = getCurrentUser();

        // 1. Nombre total de résidences du syndic
        long totalResidences = residenceRepository.countBySyndicId(currentSyndic.getId());

        // 2. Nombre total d'appartements (lots) du syndic
        long totalAppartements = propertyRepository.countByResidenceSyndicId(currentSyndic.getId());

        // 3. Trésorerie globale (somme des paidAmount des ChargeCallItem)
        // NOTE : cumul provisoire en attendant le module Wallet
        BigDecimal tresorerieGlobale = chargeCallItemRepository.sumPaidAmountBySyndic(currentSyndic);
        if (tresorerieGlobale == null) {
            tresorerieGlobale = BigDecimal.ZERO;
        }

        // 4. Résidences avec impayés et pourcentage
        long residencesAvecImpayes = chargeCallItemRepository.countResidencesWithUnpaidBySyndic(
                currentSyndic, PaymentStatus.COMPLETED);

        double pourcentageResidencesImpayees = 0.0;
        if (totalResidences > 0) {
            pourcentageResidencesImpayees = (double) residencesAvecImpayes / totalResidences * 100;
        }

        // 5. Interventions ouvertes (non clôturées ni annulées)
        long interventionsOuvertes = interventionRequestRepository.countOpenBySyndic(currentSyndic);

        // 6. Interventions en cours (STARTED)
        long interventionsEnCours = interventionRequestRepository.countStartedBySyndic(currentSyndic);

        // 7. Interventions planifiées (PENDING)
        long interventionsPlanifiees = interventionRequestRepository.countPendingBySyndic(currentSyndic);

        return ResidenceDashboardStatsDTO.builder()
                .totalResidences(totalResidences)
                .totalApartments(totalAppartements)
                .globalTreasury(tresorerieGlobale)
                .residencesWithUnpaid(residencesAvecImpayes)
                .percentageResidencesWithUnpaid(pourcentageResidencesImpayees)
                .openInterventions(interventionsOuvertes)
                .inProgressInterventions(interventionsEnCours)
                .pendingInterventions(interventionsPlanifiees)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResidenceCardDTO> getResidencesPaginated(String search, String city, String status, Integer page, Integer size) {

        User currentSyndic = getCurrentUser();

        // Pagination
        Pageable pageable = PageRequest.of(page, size);

        // Récupérer les résidences filtrées
        Page<Residence> residencePage = residenceRepository.findBySyndicIdWithFilters(
                currentSyndic.getId(), search, city, pageable);

        // Mapper vers ResidenceCardDTO avec calculs à la volée
        List<ResidenceCardDTO> filteredCards = residencePage.getContent().stream()
                .map(residence -> {
                    // Calculer le taux d'impayés pour cette résidence
                    BigDecimal amountDue = chargeCallItemRepository.sumQuotePartByResidenceId(residence.getId());
                    BigDecimal amountPaid = chargeCallItemRepository.sumPaidAmountByResidenceId(residence.getId());

                    double tauxImpayes = 0.0;
                    if (amountDue != null && amountDue.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal unpaid = amountDue.subtract(amountPaid != null ? amountPaid : BigDecimal.ZERO);
                        tauxImpayes = unpaid.divide(amountDue, 4, java.math.RoundingMode.HALF_UP)
                                .multiply(BigDecimal.valueOf(100))
                                .doubleValue();
                    }

                    // Calculer le healthStatus
                    // Vérifier s'il y a une intervention URGENT active
                    boolean hasUrgentActiveIntervention = interventionRequestRepository
                            .findAllByResidenceId(residence.getId())
                            .stream()
                            .anyMatch(ir -> ir.getUrgencyLevel() == UrgencyLevel.URGENT
                                    && ir.getStatus() != InterventionStatus.FINAL_VALIDATION
                                    && ir.getStatus() != InterventionStatus.CANCELLED);

                    ResidenceHealthStatus healthStatus;
                    if (tauxImpayes > 10.0 || hasUrgentActiveIntervention) {
                        healthStatus = ResidenceHealthStatus.CRITIQUE;
                    } else if (tauxImpayes > 5.0) {
                        healthStatus = ResidenceHealthStatus.ATTENTION;
                    } else {
                        healthStatus = ResidenceHealthStatus.EXCELLENT;
                    }

                    // Filtrer par healthStatus si demandé
                    if (status != null && !healthStatus.name().equals(status)) {
                        return null; // sera filtré après le mapping
                    }

                    // Nombre d'appartements
                    long appartementsCount = propertyRepository.countByResidenceId(residence.getId());

                    // Trésorerie de la résidence
                    BigDecimal tresorerie = amountPaid != null ? amountPaid : BigDecimal.ZERO;

                    // Interventions ouvertes pour cette résidence
                    long openInterventions = interventionRequestRepository.countOpenByResidenceId(residence.getId());

                    return ResidenceCardDTO.builder()
                            .id(residence.getId())
                            .name(residence.getName())
                            .city(residence.getCity())
                            .photoUrl(residence.getPhotoUrl())
                            .healthStatus(healthStatus)
                            .appartementsCount(appartementsCount)
                            .tauxImpayes(tauxImpayes)
                            .tresorerie(tresorerie)
                            .openInterventions(openInterventions)
                            .build();
                })
                .filter(dto -> dto != null) // filtrer les nulls (healthStatus mismatch)
                .toList();

        // Reconstruire la page avec les résultats filtrés
        return new PageImpl<>(
                filteredCards,
                pageable,
                filteredCards.size());
    }

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC (POUR DROPDOWNS)
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
    // LISTER LES LOTS D'UNE RÉSIDENCE AVEC FILTRES (ONGLET APPARTEMENTS)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<PropertyListItemDTO> getPropertiesPaginatedWithFilters(
            Long residenceId, String search, Integer floor, String status, Integer page, Integer size) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Récupérer TOUS les lots filtrés (sans pagination)
        // Filtres DB uniquement : search (reference/nom) et floor
        List<Property> allProperties = propertyRepository.findByResidenceIdWithFilters(
                residenceId, search, floor, Pageable.unpaged()).getContent();

        // Précharger le ChargeCall le plus récent
        var mostRecentChargeCall = chargeCallRepository.findMostRecentByResidenceId(residenceId);

        // Construire une map pour retrouver rapidement le ChargeCallItem d'un copropriétaire.
        //  Clé   = id du copropriétaire.
        // Valeur = son ChargeCallItem du dernier appel de charges.
        Map<Long, ChargeCallItem> chargeCallItemByOwnerId = new HashMap<>();

       // Vérifier qu'un appel de charges existe pour cette résidence.
        if (mostRecentChargeCall.isPresent()) {

            // Récupérer le dernier appel de charges.
            ChargeCall chargeCall = mostRecentChargeCall.get();

            // Parcourir tous les ChargeCallItem de cet appel.
            for (ChargeCallItem item : chargeCall.getItems()) {

                // Enregistrer chaque ChargeCallItem dans la Map.
                // Cela permettra ensuite de retrouver directement  les informations d'un copropriétaire grâce à son id
                chargeCallItemByOwnerId.put(item.getCoOwner().getId(), item);
            }
        }
        // Date du jour pour vérifier les retards
        LocalDate today = LocalDate.now();

        // Calculer le statut de chaque lot
        List<PropertyListItemDTO> allItems = allProperties.stream()
                .map(property -> {
                    // 1. Calculer le statut composite
                    String calculatedStatus = calculatePropertyStatus(
                            property, chargeCallItemByOwnerId, today);

                    // 2. Calculer la charge pour ce lot
                    BigDecimal charge = calculatePropertyCharge(
                            property, chargeCallItemByOwnerId);

                    // 3. Construire le DTO
                    return PropertyListItemDTO.builder()
                            .id(property.getId())
                            .reference(property.getReference())
                            .propertyType(property.getTypeBien() != null ? property.getTypeBien().getName() : null)
                            .floor(property.getFloor())
                            .owner(property.getOwner() != null
                                    ? PropertyListItemDTO.OwnerInfo.builder()
                                            .fullName(property.getOwner().getFirstName() + " " + property.getOwner().getLastName())
                                            .photoUrl(property.getOwner().getProfilePhotoUrl())
                                            .build()
                                    : null)
                            .status(calculatedStatus)
                            .charge(charge)
                            .build();
                })
                .toList();

        // Filtrer par statut si demandé (après calcul de tous les statuts)
        if (status != null) {
            allItems = allItems.stream()
                    .filter(dto -> status.equals(dto.getStatus()))
                    .toList();
        }

        // Appliquer la pagination manuellement sur la liste finale
        int totalElements = allItems.size();
        int startIndex = page * size;
        int endIndex = Math.min(startIndex + size, totalElements);

        List<PropertyListItemDTO> pageContent = startIndex < totalElements
                ? allItems.subList(startIndex, endIndex)
                : List.of();

        // Reconstruire la page avec les résultats paginés
        return new PageImpl<>(
                pageContent,
                PageRequest.of(page, size),
                totalElements);
    }

    // =========================================================================
    // LISTER LES ÉQUIPEMENTS COMMUNS D'UNE RÉSIDENCE AVEC FILTRES (ONGLET BIENS COMMUNS)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<CommonFacilityListItemDTO> getCommonFacilitiesWithFilters(
            Long residenceId, String search, String status) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Récupérer tous les équipements communs de la résidence
        List<CommonFacility> facilities = facilityRepository.findByResidenceId(residenceId);

        // Précharger toutes les interventions liées aux équipements de cette résidence
        List<InterventionRequest> interventions = interventionRequestRepository
                .findByCommonFacilityResidenceId(residenceId);

        // Grouper les interventions par équipement (commonFacility.id)
        Map<Long, List<InterventionRequest>> interventionsByFacilityId = interventions.stream()
                .collect(Collectors.groupingBy(ir -> ir.getCommonFacility().getId()));

        // Mapper vers CommonFacilityListItemDTO avec calculs à la volée
        List<CommonFacilityListItemDTO> allItems = facilities.stream()
                .filter(facility -> {
                    // Filtre search sur le nom de l'équipement (facilityType.name)
                    if (search != null && facility.getFacilityType() != null) {
                        return facility.getFacilityType().getName().toLowerCase()
                                .contains(search.toLowerCase());
                    }
                    return true;
                })
                .map(facility -> {
                    // Récupérer les interventions de cet équipement
                    List<InterventionRequest> facilityInterventions = interventionsByFacilityId
                            .getOrDefault(facility.getId(), List.of());

                    // Calculer le statut composite
                    String calculatedStatus = calculateFacilityStatus(facilityInterventions);

                    // Calculer la date de dernière maintenance
                    LocalDate lastMaintenanceDate = calculateLastMaintenanceDate(facilityInterventions);

                    return CommonFacilityListItemDTO.builder()
                            .id(facility.getId())
                            .name(facility.getFacilityType() != null ? facility.getFacilityType().getName() : null)
                            .icon(facility.getFacilityType() != null ? facility.getFacilityType().getIcon() : null)
                            .status(calculatedStatus)
                            .lastMaintenanceDate(lastMaintenanceDate)
                            .build();
                })
                .toList();

        // Filtrer par statut si demandé (après calcul de tous les statuts)
        if (status != null) {
            allItems = allItems.stream()
                    .filter(dto -> status.equals(dto.getStatus()))
                    .toList();
        }

        return allItems;
    }

    // =========================================================================
    // DÉTAIL D'UN ÉQUIPEMENT COMMUN (ONGLET BIENS COMMUNS)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public CommonFacilityDetailDTO getCommonFacilityDetail(Long residenceId, Long facilityId) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Récupérer le bien commun
        CommonFacility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new ResourceNotFoundException("Équipement introuvable"));

        // Vérifier que le bien appartient bien à la résidence
        if (!facility.getResidence().getId().equals(residenceId)) {
            throw new ForbiddenException("Cet équipement n'appartient pas à cette résidence");
        }

        // Récupérer toutes les interventions de ce bien pour calculer le statut et la date de maintenance
        List<InterventionRequest> facilityInterventions = interventionRequestRepository
                .findByCommonFacilityId(facilityId);

        // Calculer le statut du bien (réutiliser la méthode existante)
        String status = calculateFacilityStatus(facilityInterventions);

        // Calculer la date de dernière maintenance (réutiliser la méthode existante)
        LocalDate lastMaintenanceDate = calculateLastMaintenanceDate(facilityInterventions);

        // Récupérer les 4 interventions les plus récentes pour l'historique
        Pageable pageable = PageRequest.of(0, 4);
        List<InterventionRequest> recentInterventions = interventionRequestRepository
                .findRecentByCommonFacilityId(facilityId, pageable);

        // Construire l'historique des interventions
        List<InterventionHistoryItemDTO> interventionHistory = new ArrayList<>();
        for (InterventionRequest intervention : recentInterventions) {
            InterventionHistoryItemDTO historyItem = InterventionHistoryItemDTO.builder()
                    .title(intervention.getTitle())
                    .date(intervention.getCreatedAt())
                    .provider(intervention.getSelectedProvider() != null
                            ? intervention.getSelectedProvider().getFirstName() + " " + intervention.getSelectedProvider().getLastName()
                            : null)
                    .status(intervention.getStatus().getLabel())
                    .build();
            interventionHistory.add(historyItem);
        }

        // Construire et retourner le DTO de détail
        return CommonFacilityDetailDTO.builder()
                .id(facility.getId())
                .name(facility.getFacilityType() != null ? facility.getFacilityType().getName() : null)
                .icon(facility.getFacilityType() != null ? facility.getFacilityType().getIcon() : null)
                .category(facility.getFacilityType() != null ? facility.getFacilityType().getCategory() : null)
                .description(facility.getFacilityType() != null ? facility.getFacilityType().getDescription() : null)
                .residenceName(residence.getName())
                .city(residence.getCity())
                .status(status)
                .lastMaintenanceDate(lastMaintenanceDate)
                .interventionHistory(interventionHistory)
                .build();
    }

    // =========================================================================
    // ÉVOLUTION MENSUELLE DES PAIEMENTS COLLECTÉS (ONGLET FINANCES)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<MonthlyPaymentDTO> getMonthlyPaymentsEvolution(Long residenceId, Integer year) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Année courante par défaut si non fournie
        int targetYear = (year != null) ? year : java.time.Year.now().getValue();

        // Récupérer les sommes par mois depuis la base (seuls les mois avec paiements)
        List<Object[]> monthlySums = chargeCallPaymentRepository
                .sumCompletedPaymentsByMonth(residenceId, targetYear);

        // Construire une map pour un accès rapide par mois
        Map<Integer, BigDecimal> amountsByMonth = new java.util.HashMap<>();
        for (Object[] row : monthlySums) {
            Integer month = (Integer) row[0];
            BigDecimal total = (BigDecimal) row[1];
            amountsByMonth.put(month, total);
        }

        // Construire la liste complète de 12 mois (janvier à décembre)
        // Les mois sans paiement auront un montant à zéro
        List<MonthlyPaymentDTO> result = new java.util.ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            BigDecimal amount = amountsByMonth.getOrDefault(month, BigDecimal.ZERO);
            result.add(MonthlyPaymentDTO.builder()
                    .month(month)
                    .amount(amount)
                    .build());
        }

        return result;
    }

    // =========================================================================
    // RÉPARTITION DU BUDGET PRÉVISIONNEL PAR CATÉGORIE (ONGLET FINANCES)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public ExpenseBreakdownDTO getExpensesBreakdown(Long residenceId, Integer year) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Année courante par défaut si non fournie
        int targetYear = (year != null) ? year : java.time.Year.now().getValue();

        // Récupérer le budget pour cette résidence et cette année
        // S'il n'existe pas, on retourne une réponse vide (total à zéro, liste vide)
        Optional<Budget> budgetOptional = budgetRepository
                .findByResidenceIdAndAnnee(residenceId, targetYear);

        if (budgetOptional.isEmpty()) {
            // Aucun budget trouvé pour cette année
            return ExpenseBreakdownDTO.builder()
                    .totalAmount(BigDecimal.ZERO)
                    .categories(List.of())
                    .build();
        }

        Budget budget = budgetOptional.get();

        // Récupérer tous les postes de budget (BudgetItem) de ce budget
        List<BudgetItem> budgetItems = budget.getItems();

        // Si le budget n'a aucun poste, on retourne une réponse vide
        if (budgetItems == null || budgetItems.isEmpty()) {
            return ExpenseBreakdownDTO.builder()
                    .totalAmount(budget.getBudgetTotal() != null ? budget.getBudgetTotal() : BigDecimal.ZERO)
                    .categories(List.of())
                    .build();
        }

        // Étape 1 : Regrouper les postes par leur libellé
        // On utilise une Map pour accumuler les montants par libellé
        // La clé est le libellé (String), la valeur est le montant total (BigDecimal)
        Map<String, BigDecimal> amountsByLabel = new HashMap<>();

        // On parcourt tous les postes de budget un par un
        for (BudgetItem item : budgetItems) {
            String label = item.getLibelle();
            BigDecimal amount = item.getMontant();

            // Si le libellé est null ou vide, on l'ignore
            if (label == null || label.trim().isEmpty()) {
                continue;
            }

            // Si le montant est null, on considère que c'est zéro
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }

            // On ajoute ce montant au total déjà accumulé pour ce libellé
            // Si c'est la première fois qu'on voit ce libellé, on part de zéro
            BigDecimal currentTotal = amountsByLabel.getOrDefault(label, BigDecimal.ZERO);
            amountsByLabel.put(label, currentTotal.add(amount));
        }

        // Étape 2 : Calculer le montant total du budget
        // On peut soit additionner tous les BudgetItem, soit utiliser budgetTotal
        // Ici on additionne les montants regroupés pour être cohérent
        BigDecimal totalBudgetAmount = BigDecimal.ZERO;
        for (BigDecimal amount : amountsByLabel.values()) {
            totalBudgetAmount = totalBudgetAmount.add(amount);
        }

        // Si le total est zéro (cas rare mais possible), on retourne une réponse vide
        if (totalBudgetAmount.compareTo(BigDecimal.ZERO) == 0) {
            return ExpenseBreakdownDTO.builder()
                    .totalAmount(BigDecimal.ZERO)
                    .categories(List.of())
                    .build();
        }

        // Étape 3 : Construire la liste des catégories avec leurs pourcentages
       List<ExpenseCategoryDTO> categories = new java.util.ArrayList<>();

        // On parcourt chaque libellé regroupé pour créer un DTO
        for (Map.Entry<String, BigDecimal> entry : amountsByLabel.entrySet()) {
            String label = entry.getKey();
            BigDecimal amount = entry.getValue();

            // Calcul du pourcentage : (montant de la catégorie / total) * 100
            // On utilise divide avec une précision de 2 décimales pour l'arrondi
            BigDecimal percentageDecimal = amount
                    .multiply(new java.math.BigDecimal("100"))
                    .divide(totalBudgetAmount, 2, java.math.RoundingMode.HALF_UP);

            // On convertit en Double pour le DTO
            Double percentage = percentageDecimal.doubleValue();

            // On crée le DTO pour cette catégorie
            ExpenseCategoryDTO categoryDTO = ExpenseCategoryDTO.builder()
                    .label(label)
                    .amount(amount)
                    .percentage(percentage)
                    .build();

            categories.add(categoryDTO);
        }

        // Étape 4 : Retourner le résultat final
        return ExpenseBreakdownDTO.builder()
                .totalAmount(totalBudgetAmount)
                .categories(categories)
                .build();
    }

    // =========================================================================
    // LISTE DES APPELS DE CHARGES PAR COPROPRIÉTAIRE (ONGLET FINANCES)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<ChargeCallItemSummaryDTO> getChargeCallsSummary(Long residenceId) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Limite fixe à 5 résultats
        int limit = 5;

        // Créer l'objet Pageable pour limiter à 5 résultats
        Pageable pageable = PageRequest.of(0, limit);

        // Récupérer les ChargeCallItem pour cette résidence avec limite
        // Ils sont déjà triés du plus récent au plus ancien par la requête
        Page<ChargeCallItem> chargeCallItemsPage = chargeCallItemRepository
                .findByResidenceId(residenceId, pageable);

        // S'il n'y a aucun item, on retourne une liste vide
        if (chargeCallItemsPage.isEmpty()) {
            return List.of();
        }

        // Liste pour stocker les DTOs à retourner
        List<ChargeCallItemSummaryDTO> summaryDTOs = new ArrayList<>();

        // On parcourt chaque ChargeCallItem pour construire son DTO
        for (ChargeCallItem item : chargeCallItemsPage.getContent()) {

            // Étape 1 : Récupérer le nom du copropriétaire
            User coOwner = item.getCoOwner();
            String coOwnerName = coOwner.getFirstName() + " " + coOwner.getLastName();

            // Étape 2 : Récupérer tous les lots de ce copropriétaire dans cette résidence
            // On utilise le repository Property pour trouver tous les lots du coOwner dans cette résidence
            List<Property> properties = propertyRepository.findByOwnerIdAndResidenceId(coOwner.getId(), residenceId);

            // On construit la liste de PropertySummaryDTO pour chaque lot
            List<PropertySummaryDTO> propertySummaries = new ArrayList<>();
            for (Property property : properties) {
                PropertySummaryDTO propertySummary = PropertySummaryDTO.builder()
                        .reference(property.getReference())
                        .typeName(property.getTypeBien() != null ? property.getTypeBien().getName() : null)
                        .build();
                propertySummaries.add(propertySummary);
            }

            // Étape 3 : Récupérer le montant dû
            // Le montant dû est la quote-part du copropriétaire pour cet appel de charges
            BigDecimal amountDue = item.getQuotePart();

            // Étape 4 : Récupérer le statut
            // On utilise directement le statut PaymentStatus de l'item
            PaymentStatus status = item.getStatus();

            // Étape 5 : Récupérer la date limite
            // La date limite est sur le ChargeCall parent
            LocalDate dueDate = item.getChargeCall().getDueDate();

            // Étape 6 : Récupérer le mode de paiement du dernier paiement complété
            // On cherche le dernier paiement COMPLETED pour ce ChargeCallItem
            Optional<ChargeCallPayment> latestPayment = chargeCallPaymentRepository
                    .findLatestCompletedByChargeCallItemId(item.getId());

            String paymentMethod = null;
            if (latestPayment.isPresent()) {
                // Si un paiement complété existe, on récupère son mode de paiement
                paymentMethod = latestPayment.get().getMethod().name();
            }

            // Étape 7 : Construire le DTO pour cet item
            ChargeCallItemSummaryDTO summaryDTO = ChargeCallItemSummaryDTO.builder()
                    .coOwnerName(coOwnerName)
                    .properties(propertySummaries)
                    .amountDue(amountDue)
                    .status(status)
                    .dueDate(dueDate)
                    .paymentMethod(paymentMethod)
                    .build();

            summaryDTOs.add(summaryDTO);
        }

        // Retourner la liste de DTOs
        return summaryDTOs;
    }


    // =========================================================================
    // KANBAN DES INTERVENTIONS (ONGLET TRAVAUX)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public InterventionKanbanResponseDTO getInterventionsKanban(Long residenceId) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Limite fixe à 10 éléments par colonne
        int limit = 10;
        Pageable pageable = PageRequest.of(0, limit);

        // ============================================================
        // COLONNE "SIGNALÉ" (PENDING, SYNDIC_ASSIGNED, QUOTE_VALIDATED)
        // ============================================================

        // Compter le nombre réel total
        Long reportedCount = interventionRequestRepository.countReportedByResidenceId(residenceId);

        // Récupérer les 10 plus récentes
        List<InterventionRequest> reportedInterventions = interventionRequestRepository
                .findReportedByResidenceId(residenceId, pageable);

        // Construire les DTOs pour la colonne "Signalé"
        List<InterventionKanbanCardDTO> reportedItems = new ArrayList<>();

        for (InterventionRequest intervention : reportedInterventions) {
            InterventionKanbanCardDTO card = InterventionKanbanCardDTO.builder()
                    .id(intervention.getId())
                    .reference(intervention.getReference())
                    .title(intervention.getTitle())
                    .urgencyLevel(intervention.getUrgencyLevel().getLabel())
                    .status(intervention.getStatus().getLabel())
                    .commentsCount(intervention.getComments() != null ? intervention.getComments().size() : 0)
                    .reportedAt(intervention.getCreatedAt())
                    .build();

            // Déterminer le rapporteur selon initiatedBy
            if (intervention.getInitiatedBy() == InitiatedBy.SYNDIC && intervention.getSyndic() != null) {
                card.setReportedBy(InterventionKanbanCardDTO.UserInfoDTO.builder()
                        .fullName(intervention.getSyndic().getFirstName() + " " + intervention.getSyndic().getLastName())
                        .photoUrl(intervention.getSyndic().getProfilePhotoUrl())
                        .build());
            } else if (intervention.getInitiatedBy() == InitiatedBy.OWNER && intervention.getOwner() != null) {
                card.setReportedBy(InterventionKanbanCardDTO.UserInfoDTO.builder()
                        .fullName(intervention.getOwner().getFirstName() + " " + intervention.getOwner().getLastName())
                        .photoUrl(intervention.getOwner().getProfilePhotoUrl())
                        .build());
            }

            reportedItems.add(card);
        }

        // ============================================================
        // COLONNE "EN COURS" (STARTED)
        // ============================================================

        // Compter le nombre réel total
        Long inProgressCount = interventionRequestRepository.countInProgressByResidenceId(residenceId);

        // Récupérer les 10 plus récentes
        List<InterventionRequest> inProgressInterventions = interventionRequestRepository
                .findInProgressByResidenceId(residenceId, pageable);

        // Construire les DTOs pour la colonne "En cours"
        List<InterventionKanbanCardDTO> inProgressItems = new ArrayList<>();
        for (InterventionRequest intervention : inProgressInterventions) {
            InterventionKanbanCardDTO card = InterventionKanbanCardDTO.builder()
                    .id(intervention.getId())
                    .reference(intervention.getReference())
                    .title(intervention.getTitle())
                    .urgencyLevel(intervention.getUrgencyLevel().getLabel())
                    .status(intervention.getStatus().getLabel())
                    .commentsCount(intervention.getComments() != null ? intervention.getComments().size() : 0)
                    .startedAt(intervention.getStartedAt())
                    .build();

            // Ajouter le prestataire sélectionné
            if (intervention.getSelectedProvider() != null) {
                card.setProvider(InterventionKanbanCardDTO.UserInfoDTO.builder()
                        .fullName(intervention.getSelectedProvider().getFirstName() + " " + intervention.getSelectedProvider().getLastName())
                        .photoUrl(intervention.getSelectedProvider().getProfilePhotoUrl())
                        .build());
            }

            inProgressItems.add(card);
        }

        // ============================================================
        // COLONNE "RÉSOLU" (FINISHED, FINAL_VALIDATION)
        // ============================================================

        // Compter le nombre réel total
        Long resolvedCount = interventionRequestRepository.countResolvedByResidenceId(residenceId);

        // Récupérer les 10 plus récentes
        List<InterventionRequest> resolvedInterventions = interventionRequestRepository
                .findResolvedByResidenceId(residenceId, pageable);

        // Précharger l'historique des statuts
        List<InterventionStatusHistory> resolvedHistory = interventionStatusHistoryRepository
                .findResolvedHistoryByResidenceId(residenceId);

        // Grouper l'historique par interventionRequest.id avec une boucle for
        Map<Long, InterventionStatusHistory> historyByInterventionId = new HashMap<>();

        for (InterventionStatusHistory history : resolvedHistory) {
            Long interventionId = history.getInterventionRequest().getId();
            // On garde la première entrée trouvée (la plus récente car triée par createdAt DESC)
            if (!historyByInterventionId.containsKey(interventionId)) {
                historyByInterventionId.put(interventionId, history);
            }
        }

        // Construire les DTOs pour la colonne "Résolu"
        List<InterventionKanbanCardDTO> resolvedItems = new ArrayList<>();
        for (InterventionRequest intervention : resolvedInterventions) {
            InterventionKanbanCardDTO card = InterventionKanbanCardDTO.builder()
                    .id(intervention.getId())
                    .reference(intervention.getReference())
                    .title(intervention.getTitle())
                    .urgencyLevel(intervention.getUrgencyLevel().getLabel())
                    .status(intervention.getStatus().getLabel())
                    .commentsCount(intervention.getComments() != null ? intervention.getComments().size() : 0)
                    .build();

            // Récupérer l'historique de cette intervention depuis la map préchargée
            InterventionStatusHistory history = historyByInterventionId.get(intervention.getId());
            if (history != null) {
                card.setResolvedAt(history.getCreatedAt());
                card.setResolvedBy(InterventionKanbanCardDTO.UserInfoDTO.builder()
                        .fullName(history.getChangedBy().getFirstName() + " " + history.getChangedBy().getLastName())
                        .photoUrl(history.getChangedBy().getProfilePhotoUrl())
                        .build());
            }

            resolvedItems.add(card);
        }

        // ============================================================
        // CONSTRUIRE LA RÉPONSE
        // ============================================================

        return InterventionKanbanResponseDTO.builder()
                .reported(InterventionKanbanResponseDTO.KanbanColumn.builder()
                        .count(reportedCount.intValue())
                        .items(reportedItems)
                        .build())
                .inProgress(InterventionKanbanResponseDTO.KanbanColumn.builder()
                        .count(inProgressCount.intValue())
                        .items(inProgressItems)
                        .build())
                .resolved(InterventionKanbanResponseDTO.KanbanColumn.builder()
                        .count(resolvedCount.intValue())
                        .items(resolvedItems)
                        .build())
                .build();
    }

    // =========================================================================
    // LISTE DES TRANSACTIONS RÉCENTES DU WALLET (ONGLET FINANCES)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<WalletTransactionDTO> getRecentWalletTransactions(Long residenceId, Integer limit) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Valeur par défaut pour la limite
        int transactionLimit = (limit != null) ? limit : 5;

        // Créer un Pageable pour limiter le nombre de résultats
        Pageable pageable = PageRequest.of(0, transactionLimit);

        // Récupérer les transactions récentes pour cette résidence
        List<SyndicWalletTransaction> transactions = syndicWalletTransactionRepository
                .findRecentByResidenceIdWithLimit(residenceId, pageable);

        // Construire la liste de DTOs
        List<WalletTransactionDTO> transactionDTOs = new ArrayList<>();
        for (SyndicWalletTransaction tw : transactions) {
            transactionDTOs.add(toDTO(tw));
        }

        return transactionDTOs;
    }

    /**
     * Convertit une SyndicWalletTransaction en WalletTransactionDTO
     * Le montant est signé : positif pour CHARGES, négatif pour TRAVAUX et RETRAIT
     */
    private WalletTransactionDTO toDTO(SyndicWalletTransaction tw) {
        BigDecimal signedAmount = switch (tw.getCategory()) {
            case CHARGES -> tw.getAmount();
            case TRAVAUX, RETRAIT -> tw.getAmount().negate();
        };

        return WalletTransactionDTO.builder()
                .id(tw.getId())
                .label(tw.getLabel())
                .reference(tw.getReference())
                .transactionDate(tw.getTransactionDate())
                .amount(signedAmount)
                .mode(tw.getMode())
                .category(tw.getCategory())
                .build();
    }


    // =========================================================================
    // MÉTHODES PRIVÉES UTILITAIRES
    // =========================================================================

    /**
     * Calcule le statut composite d'un lot
     * Ordre de priorité : MAINTENANCE > OVERDUE > OCCUPE/VACANT
     */
    private String calculatePropertyStatus(Property property, Map<Long, ChargeCallItem> chargeCallItemByOwnerId, LocalDate today) {

        // 1. Priorité : MAINTENANCE
        if (property.getStatus() == PropertyStatus.MAINTENANCE) {
            return "MAINTENANCE";
        }

        // 2. Priorité : OVERDUE (si le lot a un propriétaire)
        if (property.getOwner() != null) {
            //on cherche la charge du propriétaire du bien dans le dictionnaire
            ChargeCallItem item = chargeCallItemByOwnerId.get(property.getOwner().getId());

            // Si un appel de charges existe pour ce copropriétaire et qu'une date d'échéance est définie.
            if (item != null && item.getChargeCall().getDueDate() != null) {

                // Vérifier si la date d'échéance est dépassée et que la charge n'a toujours pas été payée.
                if (item.getChargeCall().getDueDate().isBefore(today)
                        && item.getStatus() != PaymentStatus.COMPLETED) {

                    // Le lot est considéré en retard de paiement.
                    return "OVERDUE";
                }
            }
        }

        // 3. Sinon : statut du lot (OCCUPE ou VACANT)
        return property.getStatus().name();
    }

    /**
     * Calcule la charge pour un lot précis
     * Formule : ChargeCallItem.quotePart × (Property.tantieme / ChargeCallItem.tantieme)
     * Explication : répartir le montant total du copropriétaire au prorata du poids de ce lot
     */
    private BigDecimal calculatePropertyCharge(
            Property property,
            Map<Long, ChargeCallItem> chargeCallItemByOwnerId) {

        // Si le lot est vacant ou pas de ChargeCallItem, retourner ZERO
        if (property.getOwner() == null) {
            return BigDecimal.ZERO;
        }

        ChargeCallItem item = chargeCallItemByOwnerId.get(property.getOwner().getId());
        if (item == null) {
            return BigDecimal.ZERO;
        }

        // Éviter la division par zéro
        if (item.getTantieme().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        // Calculer la charge au prorata du tantième de ce lot
        return item.getQuotePart()
                .multiply(property.getTantieme())
                .divide(item.getTantieme(), 2, java.math.RoundingMode.HALF_UP);
    }


    /**
     * Calcule le statut composite d'un équipement commun
     * MAINTENANCE si intervention active, FUNCTIONAL sinon
     */
    private String calculateFacilityStatus(List<InterventionRequest> interventions) {
        // Statuts terminaux : intervention clôturée
        List<InterventionStatus> terminalStatuses = List.of(
                InterventionStatus.FINISHED,
                InterventionStatus.FINAL_VALIDATION,
                InterventionStatus.CANCELLED
        );

        // Vérifier s'il existe une intervention active (non terminale)
        for (InterventionRequest intervention : interventions) {
            if (!terminalStatuses.contains(intervention.getStatus())) {
                return "MAINTENANCE";
            }
        }

        return "FUNCTIONAL";
    }

    /**
     * Calcule la date de la dernière maintenance terminée sur un équipement
     * MAX(finishedAt) des interventions terminées
     */
    private LocalDate calculateLastMaintenanceDate(List<InterventionRequest> interventions) {
        return interventions.stream()
                .filter(ir -> ir.getFinishedAt() != null)
                .map(InterventionRequest::getFinishedAt)
                .max(LocalDateTime::compareTo)
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
    }


    /**
     * Calcule le statut de santé d'une résidence
     * CRITIQUE si taux d'impayés > 10% OU intervention URGENT non terminée
     * ATTENTION si taux d'impayés > 5%
     * EXCELLENT sinon
     */
    private ResidenceHealthStatus calculateHealthStatus(Long residenceId) {
        // Calculer le taux d'impayés à partir des ChargeCallItem
        // via Budget -> ChargeCall -> ChargeCallItem
        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        // Récupérer tous les budgets de la résidence
        List<Budget> budgets = budgetRepository.findByResidenceId(residenceId);

        for (Budget budget : budgets) {
            for (ChargeCall chargeCall : budget.getChargeCalls()) {
                for (ChargeCallItem item : chargeCall.getItems()) {
                    totalDue = totalDue.add(item.getQuotePart());
                    totalPaid = totalPaid.add(item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO);
                }
            }
        }

        // Calculer le taux d'impayés
        double unpaidRate = 0.0;
        if (totalDue.compareTo(BigDecimal.ZERO) > 0) {
            unpaidRate = totalDue.subtract(totalPaid)
                    .divide(totalDue, 4, java.math.RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        // Vérifier s'il existe une intervention URGENT non terminée
        boolean hasUrgentActiveIntervention = interventionRequestRepository
                .findAllByResidenceId(residenceId)
                .stream()
                .anyMatch(ir -> ir.getUrgencyLevel() == UrgencyLevel.URGENT
                        && ir.getStatus() != InterventionStatus.FINAL_VALIDATION
                        && ir.getStatus() != InterventionStatus.CANCELLED);

        // Déterminer le statut
        if (unpaidRate > 10.0 || hasUrgentActiveIntervention) {
            return ResidenceHealthStatus.CRITIQUE;
        } else if (unpaidRate > 5.0) {
            return ResidenceHealthStatus.ATTENTION;
        } else {
            return ResidenceHealthStatus.EXCELLENT;
        }
    }


    // ÉTAPE 3 — RÉCUPÉRER LES TYPES D'ÉQUIPEMENTS AVEC LEURS CHAMPS
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
