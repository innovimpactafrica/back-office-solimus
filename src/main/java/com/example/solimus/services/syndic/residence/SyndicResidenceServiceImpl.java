package com.example.solimus.services.syndic.residence;

import com.example.solimus.dtos.syndic.charge.CommonFacilitySuggestionDTO;
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
import com.example.solimus.security.PlanLimitGuard;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.utils.PasswordGeneratorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
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
    private final SyndicWalletRepository syndicWalletRepository;
    private final SyndicWithdrawalRequestRepository syndicWithdrawalRequestRepository;
    private final InterventionStatusHistoryRepository interventionStatusHistoryRepository;
    private final ActivityLogRepository activityLogRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final PlanLimitGuard planLimitGuard;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final NotificationService notificationService;

    // =========================================================================
    // CRÉATION EN UN SEUL APPEL — infos générales + lots + équipements + sécurité
    // =========================================================================
    @Override
    @Transactional
    public ResidenceDTO createResidenceFull(CreateResidenceFullDTO dto, MultipartFile photo) {

        // Récupère le syndic connecté
        User currentSyndic = getCurrentUser();

        // Vérifie que la superficie totale de la résidence est fournie
        if (dto.getTotalArea() == null) {
            throw new BadRequestException("La superficie totale de la résidence est obligatoire");
        }

        // Bloque la création si la limite de résidences de sa formule est déjà atteinte
        planLimitGuard.assertCanAddResidence(currentSyndic);

        // Bloque si la limite d'appartements de sa formule serait dépassée par ce lot de créations
        int propertiesCount = dto.getProperties() != null ? dto.getProperties().size() : 0;
        planLimitGuard.assertCanAddApartments(currentSyndic, propertiesCount);

        // Additionne la superficie de tous les lots demandés
        BigDecimal totalPropertiesArea = BigDecimal.ZERO;
        if (dto.getProperties() != null) {
            for (AddPropertyDTO propertyDto : dto.getProperties()) {
                totalPropertiesArea = totalPropertiesArea.add(propertyDto.getArea());
            }
        }

        // Vérifie que la superficie totale des lots ne dépasse pas celle de la résidence, avant toute insertion
        if (totalPropertiesArea.compareTo(dto.getTotalArea()) > 0) {
            throw new BadRequestException(
                    "La superficie totale des lots dépasse la superficie de la résidence. "
                            + "Lots : " + totalPropertiesArea + " m², résidence : " + dto.getTotalArea() + " m²");
        }

        // Construit l'adresse complète
        String adresseComplete = dto.getFullAddress() + ", " + dto.getCity() + ", " + dto.getCountry();

        // Crée et sauvegarde l'entité Residence
        Residence residence = new Residence();
        residence.setName(dto.getName());
        residence.setDescription(dto.getDescription());
        residence.setFullAddress(adresseComplete);
        residence.setCity(dto.getCity());
        residence.setCountry(dto.getCountry());
        residence.setLatitude(dto.getLatitude());
        residence.setLongitude(dto.getLongitude());
        residence.setConstructionDate(dto.getConstructionDate());
        residence.setRenovationDate(dto.getRenovationDate());
        residence.setTotalArea(dto.getTotalArea());
        residence.setHealthStatus(ResidenceHealthStatus.EXCELLENT);
        residence.setSyndic(currentSyndic);

        Residence saved = residenceRepository.save(residence);

        // Uploade la photo vers MinIO et l'associe à la résidence
        String photoUrl = minioService.uploadFile(photo, "residences");
        saved.setPhotoUrl(photoUrl);
        saved = residenceRepository.save(saved);

        // Crée les contacts liés à cette résidence
        if (dto.getContacts() != null) {
            for (ContactInputDTO contactDto : dto.getContacts()) {
                ResidenceContact contact = new ResidenceContact();
                contact.setFullName(contactDto.getFullName());
                contact.setPhone(contactDto.getPhone());
                contact.setResidence(saved);
                contactRepository.save(contact);
            }
        }

        // Crée chaque lot, avec son tantième calculé automatiquement
        List<Property> propertiesToSave = new ArrayList<>();
        if (dto.getProperties() != null) {
            for (AddPropertyDTO propertyDto : dto.getProperties()) {
                propertiesToSave.add(buildProperty(saved, currentSyndic, propertyDto));
            }
        }
        List<Property> savedProperties = propertyRepository.saveAll(propertiesToSave);

        // Crée les équipements communs
        if (dto.getFacilities() != null) {
            for (AddFacilityDTO facilityDto : dto.getFacilities()) {
                upsertFacility(saved, currentSyndic, facilityDto);
            }
        }
        List<CommonFacility> savedFacilities = facilityRepository.findByResidenceId(saved.getId());

        // Associe les options de sécurité sélectionnées
        List<SecurityFeature> securityFeatures = securityFeatureRepository.findAllById(
                dto.getSecurityFeatureIds() != null ? dto.getSecurityFeatureIds() : List.of());
        saved.setSecurityFeatures(securityFeatures);
        saved = residenceRepository.save(saved);

        log.info("Résidence '{}' créée en un seul appel par le syndic {} ({} lots, {} équipements, {} options de sécurité)",
                saved.getName(), currentSyndic.getEmail(),
                savedProperties.size(), savedFacilities.size(), securityFeatures.size());

        // Construit le DTO complet de la résidence créée (résidence + lots + équipements + sécurité)
        ResidenceDTO result = mapToResidenceDTO(saved);
        result.setProperties(savedProperties.stream().map(this::mapToPropertyDTO).toList());
        result.setFacilities(savedFacilities.stream()
                .map(facility -> CommonFacilityListItemDTO.builder()
                        .id(facility.getId())
                        .name(facility.getFacilityType() != null ? facility.getFacilityType().getName() : null)
                        .icon(facility.getFacilityType() != null ? facility.getFacilityType().getIcon() : null)
                        .status(calculateFacilityStatus(List.of()))
                        .build())
                .toList());
        result.setSecurityFeatures(securityFeatures.stream()
                .map(feature -> SecurityFeatureLabelDTO.builder()
                        .id(feature.getId())
                        .label(feature.getLabel())
                        .icon(feature.getIcon())
                        .build())
                .toList());

        return result;
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
        if (dto.getConstructionDate() != null) {
            residence.setConstructionDate(dto.getConstructionDate());
        }
        if (dto.getRenovationDate() != null) {
            residence.setRenovationDate(dto.getRenovationDate());
        }
        if (dto.getTotalArea() != null) {
            // Vérifie que la nouvelle superficie totale reste cohérente avec les lots déjà créés
            BigDecimal existingLotsArea = propertyRepository.sumAreaByResidenceId(residenceId);
            if (dto.getTotalArea().compareTo(existingLotsArea) < 0) {
                throw new BadRequestException(
                        "La superficie totale de la résidence ne peut pas être inférieure à la superficie déjà "
                                + "occupée par les lots existants (" + existingLotsArea + " m²)");
            }
            residence.setTotalArea(dto.getTotalArea());
        }

        // Gestion des contacts : remplacement complet si fourni, sinon rien
        if (dto.getContacts() != null) {
            // Supprimer tous les contacts existants
            contactRepository.deleteByResidenceId(residenceId);

            // Créer les nouveaux contacts
            for (ContactInputDTO contactDto : dto.getContacts()) {
                ResidenceContact contact = new ResidenceContact();
                contact.setFullName(contactDto.getFullName());
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
    //LISTER LES TYPES DE BIENS (pour dropdown lors de la création d'un lot)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<PropertyTypeDTO> getAllPropertyTypes(int page, int size) {
        User currentSyndic = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return propertyTypeRepository.findBySyndicId(currentSyndic.getId(), pageable)
                .map(type -> PropertyTypeDTO.builder()
                        .id(type.getId())
                        .name(type.getName())
                        .build());
    }
    // =========================================================================
    //  LISTER LES COPROPRIÉTAIRES POUR L'AFFECTATION D'UN LOT
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
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
    // AJOUTER UN OU PLUSIEURS LOTS À UNE RÉSIDENCE DÉJÀ CRÉÉE
    // =========================================================================
    @Override
    @Transactional
    public List<PropertyDTO> addProperties(Long residenceId, List<AddPropertyDTO> properties) {

        if (properties == null || properties.isEmpty()) {
            throw new BadRequestException("Au moins un lot doit être fourni");
        }

        // Vérifie l'appartenance de la résidence au syndic connecté
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);
        User currentSyndic = getCurrentUser();

        // Bloque si la limite d'appartements de sa formule serait dépassée par cet ajout
        planLimitGuard.assertCanAddApartments(currentSyndic, properties.size());

        // Vérifie qu'aucune référence n'est fournie deux fois dans cette même requête
        Set<String> referencesInBatch = new HashSet<>();
        for (AddPropertyDTO dto : properties) {
            if (!referencesInBatch.add(dto.getReference())) {
                throw new BadRequestException(
                        "La référence '" + dto.getReference() + "' est fournie plusieurs fois dans cette requête");
            }
        }

        // Vérifie que la superficie totale des lots (existants + nouveaux) ne dépasse pas celle de la résidence
        BigDecimal existingArea = propertyRepository.sumAreaByResidenceId(residenceId);
        BigDecimal newArea = properties.stream()
                .map(AddPropertyDTO::getArea)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalArea = existingArea.add(newArea);
        if (totalArea.compareTo(residence.getTotalArea()) > 0) {
            throw new BadRequestException(
                    "La superficie totale des lots dépasse la superficie de la résidence. "
                            + "Actuel : " + existingArea + " m², nouveaux : " + newArea
                            + " m², résidence : " + residence.getTotalArea() + " m²");
        }

        // Construit chaque lot (référence unique, tantième calculé, type de bien, propriétaire optionnel)
        List<Property> propertiesToSave = properties.stream()
                .map(dto -> buildProperty(residence, currentSyndic, dto))
                .toList();

        List<Property> saved = propertyRepository.saveAll(propertiesToSave);

        log.info("{} lot(s) ajouté(s) à la résidence '{}' par le syndic {}",
                saved.size(), residence.getName(), currentSyndic.getEmail());

        return saved.stream().map(this::mapToPropertyDTO).toList();
    }

    // =========================================================================
    // MODIFIER UN LOT / APPARTEMENT
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
            // Vérifier si la nouvelle référence existe déjà pour cette résidence (excluant le bien actuel)
            if (propertyRepository.existsByReferenceAndResidenceId(dto.getReference(), residenceId)
                    && !property.getReference().equals(dto.getReference())) {
                throw new BadRequestException(
                        "La référence '" + dto.getReference() + "' existe déjà pour cette résidence");
            }
            property.setReference(dto.getReference());
        }
        if (dto.getBloc() != null) {
            property.setBloc(dto.getBloc());
        }
        if (dto.getFloor() != null) {
            property.setFloor(dto.getFloor());
        }
        if (dto.getArea() != null) {
            // Récupère la superficie actuelle de tous les lots de la résidence (inclut l'ancienne superficie de ce lot)
            BigDecimal currentSum = propertyRepository.sumAreaByResidenceId(residenceId);
            // Ancienne superficie de ce lot avant modification (0 si jamais renseignée)
            BigDecimal oldArea = property.getArea() != null ? property.getArea() : BigDecimal.ZERO;
            // Nouvelle somme = somme actuelle - ancienne superficie + nouvelle superficie
            BigDecimal newSum = currentSum.subtract(oldArea).add(dto.getArea());
            if (newSum.compareTo(residence.getTotalArea()) > 0) {
                throw new BadRequestException(
                    "La superficie totale des lots dépasse la superficie de la résidence. "
                        + "Actuel : " + currentSum + " m², résidence : " + residence.getTotalArea() + " m²");
            }
            property.setArea(dto.getArea());

            // Recalcule le tantième proportionnel à la nouvelle superficie
            property.setShare(calculateShare(dto.getArea(), residence.getTotalArea()));
        }

        // Mettre à jour le type de bien si fourni — uniquement s'il appartient au syndic connecté
        if (dto.getPropertyTypeId() != null) {
            User currentSyndic = getCurrentUser();
            PropertyType propertyType = propertyTypeRepository.findByIdAndSyndicId(dto.getPropertyTypeId(), currentSyndic.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Type de bien introuvable"));
            property.setTypeBien(propertyType);
        }

        Property saved = propertyRepository.save(property);

        log.info("Lot '{}' modifié dans la résidence '{}'",
                saved.getReference(), residence.getName());

        return mapToPropertyDTO(saved);
    }

    // =========================================================================
    //  SUPPRIMER UN LOT / APPARTEMENT
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
    // LISTER LES LOTS D'UNE RÉSIDENCE (PAGINÉ)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<PropertyListDTO> getPropertiesPaginated(Long residenceId, Integer page, Integer size) {

        // Récupérer la résidence
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Construire Pageable
        Pageable pageable = PageRequest.of(page, size);

        // Récupérer les lots paginés
        Page<Property> propertiesPage = propertyRepository.findByResidenceId(residenceId, pageable);

        // Mapper vers DTO
        return propertiesPage.map(this::mapToPropertyListDTO);
    }

    // =========================================================================
    // Lister les options de sécurité configurées
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<SecurityFeatureLabelDTO> getSecurityFeatures() {
        List<SecurityFeature> features = securityFeatureRepository.findByActiveTrue();
        List<SecurityFeatureLabelDTO> result = new ArrayList<>();
        for (SecurityFeature feature : features) {
            SecurityFeatureLabelDTO dto = SecurityFeatureLabelDTO.builder()
                    .id(feature.getId())
                    .label(feature.getLabel())
                    .icon(feature.getIcon())
                    .build();
            result.add(dto);
        }
        return result;
    }

    // =========================================================================
    // METTRE À JOUR LES OPTIONS DE SÉCURITÉ D'UNE RÉSIDENCE
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
    // Lister les types d'équipements communs configurés
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<FacilityTypeDTO> getFacilityTypes(int page, int size) {
        User currentSyndic = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        return facilityTypeRepository.findBySyndicIdAndIsActiveTrue(currentSyndic.getId(), pageable)
                .map(type -> FacilityTypeDTO.builder()
                        .id(type.getId())
                        .name(type.getName())
                        .icon(type.getIcon())
                        .build());
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

        // 3. Trésorerie globale basée sur le module Wallet
        // Récupérer le wallet du syndic
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        Long walletId = (wallet != null) ? wallet.getId() : null;

        // Calculer le solde actuel et le solde à la fin du mois précédent
        LocalDateTime maintenant = LocalDateTime.now();
        LocalDateTime finMoisPrecedent = maintenant.withDayOfMonth(1).minusNanos(1);

        // ===== CALCUL DE LA TRÉSORERIE DISPONIBLE (le chiffre affiché maintenant) =====

        // On additionne toutes les transactions du wallet (charges reçues + travaux payés) jusqu'à aujourd'hui. C'est tout l'argent qui est
        // passé dans la caisse, entrées et sorties confondues.
        BigDecimal totalTransactions = calculerSoldeADate(walletId, maintenant);

        // On regarde combien d'argent est déjà "réservé" pour un retrait (demandé ou déjà validé). Cet argent ne doit plus compter comme disponible, même si le virement n'est pas encore parti.
        BigDecimal totalRetraitsEnCours = (walletId != null)
                ? syndicWithdrawalRequestRepository.sumPendingAndValidatedByWallet(walletId)
                : BigDecimal.ZERO;

        // La vraie trésorerie disponible = ce qu'il y a dans la caisse, moins ce qui est déjà réservé pour partir.
        BigDecimal tresorerieGlobale = totalTransactions.subtract(totalRetraitsEnCours);


        // ===== CALCUL DE LA VARIATION (le pourcentage +...% mois précédent) =====

        // Ici on ne regarde QUE les transactions, sans les retraits.on veut juste savoir si l'argent qui rentre et sort
        // a augmenté ou diminué par rapport au mois dernier — pas combien est "réservé" en ce moment.
        BigDecimal soldePrecedent = calculerSoldeADate(walletId, finMoisPrecedent);
        BigDecimal variationTresoreriePourcentage = calculerVariation(totalTransactions, soldePrecedent);

        // 4. Résidences avec impayés et pourcentage
        long residencesAvecImpayes = chargeCallItemRepository.countResidencesWithUnpaidBySyndic(
                currentSyndic, ChargeItemPaymentStatus.PAID);

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
                .variationTresoreriePourcentage(variationTresoreriePourcentage)
                .residencesWithUnpaid(residencesAvecImpayes)
                .percentageResidencesWithUnpaid(pourcentageResidencesImpayees)
                .openInterventions(interventionsOuvertes)
                .inProgressInterventions(interventionsEnCours)
                .pendingInterventions(interventionsPlanifiees)
                .build();
    }


    // =========================================================================
    // Listes RÉSIDENCES
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ResidenceCardDTO> getResidencesPaginated(String search, String city, ResidenceHealthStatus status, Integer page, Integer size) {

        User currentSyndic = getCurrentUser();

        // Requête réellement paginée : recherche, ville et statut de santé filtrés directement en SQL
        // (le statut est déjà persisté sur Residence.healthStatus, jamais recalculé ici ;
        Pageable pageable = PageRequest.of(page, size);
        Page<Residence> residencePage = residenceRepository.findBySyndicIdWithFilters(
                currentSyndic.getId(), search, city, status, pageable);

        // Construit le DTO de chaque résidence de la page
        return residencePage.map(residence -> {

            // Calcule le taux d'impayés pour affichage (purement informatif, indépendant du filtre)
            BigDecimal amountDue = chargeCallItemRepository.sumQuotePartByResidenceId(residence.getId());
            BigDecimal amountPaid = chargeCallItemRepository.sumPaidAmountByResidenceId(residence.getId());

            double tauxImpayes = 0.0;
            if (amountDue != null && amountDue.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal unpaid = amountDue.subtract(amountPaid != null ? amountPaid : BigDecimal.ZERO);
                tauxImpayes = unpaid.divide(amountDue, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
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
                    .healthStatus(residence.getHealthStatus())
                    .appartementsCount(appartementsCount)
                    .tauxImpayes(tauxImpayes)
                    .tresorerie(tresorerie)
                    .openInterventions(openInterventions)
                    .build();
        });
    }


    // =========================================================================
    // // STATISTIQUES DU BANDEAU D'INDICATEURS
    // =========================================================================
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

        // 6. Statut de santé — déjà persisté, recalculé sur événement et par le job quotidien
        ResidenceHealthStatus healthStatus = residence.getHealthStatus();

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
                        .phone(contact.getPhone())
                        .build())
                .collect(Collectors.toList());

        return ResidenceDetailDTO.builder()
                .id(residence.getId())
                .description(residence.getDescription())
                .country(residence.getCountry())
                .latitude(residence.getLatitude() != null ? residence.getLatitude().doubleValue() : null)
                .longitude(residence.getLongitude() != null ? residence.getLongitude().doubleValue() : null)
                .constructionDate(residence.getConstructionDate())
                .renovationDate(residence.getRenovationDate())
                .securityLevel(securityLevel)
                .keyContacts(keyContacts)
                .build();
    }

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC (POUR DROPDOWNS)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
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
            Long residenceId, String search, Integer floor, PropertyDisplayStatus status, Integer page, Integer size) {

        // Vérifie l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Requête réellement paginée : recherche, étage et statut filtrés directement en SQL
        // (le statut est déjà persisté sur Property.displayStatus, jamais recalculé ici ;
        // sa validité est garantie par la conversion automatique de Spring sur le paramètre d'URL)
        Pageable pageable = PageRequest.of(page, size);
        Page<Property> propertiesPage = propertyRepository.findByResidenceIdWithFilters(
                residenceId, search, floor, status, pageable);

        // Précharge le ChargeCall le plus récent, pour calculer la charge de chaque lot de cette page
        var mostRecentChargeCall = chargeCallRepository.findMostRecentByResidenceId(residenceId);
        Map<Long, ChargeCallItem> chargeCallItemByOwnerId = new HashMap<>();
        if (mostRecentChargeCall.isPresent()) {
            for (ChargeCallItem item : mostRecentChargeCall.get().getItems()) {
                chargeCallItemByOwnerId.put(item.getCoOwner().getId(), item);
            }
        }

        // Construit le DTO de chaque lot de la page
        return propertiesPage.map(property -> PropertyListItemDTO.builder()
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
                // Locataire du lot — null si le bien n'est pas loué
                .tenant(property.getTenant() != null
                        ? PropertyListItemDTO.OwnerInfo.builder()
                                .fullName(property.getTenant().getFirstName() + " " + property.getTenant().getLastName())
                                .photoUrl(property.getTenant().getProfilePhotoUrl())
                                .build()
                        : null)
                .status(property.getDisplayStatus() != null ? property.getDisplayStatus().name() : null)
                .charge(calculatePropertyCharge(property, chargeCallItemByOwnerId))
                .build());
    }

    // =========================================================================
    // AJOUTER UN LOCATAIRE À UN LOT (ONGLET APPARTEMENTS)
    // =========================================================================
    @Override
    @Transactional
    public PropertyDTO addTenant(Long residenceId, Long propertyId, String firstName, String lastName,
                                  String email, String phone, MultipartFile photo) {

        // Récupère la résidence et vérifie qu'elle appartient au syndic connecté
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Récupère le lot
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Vérifie que le lot appartient à cette résidence
        if (!property.getResidence().getId().equals(residenceId)) {
            throw new BadRequestException("Ce bien n'appartient pas à cette résidence");
        }

        // Impossible de louer un bien qui n'a pas encore de propriétaire
        if (property.getOwner() == null) {
            throw new BadRequestException("Ce lot n'a pas de propriétaire, impossible d'y ajouter un locataire");
        }

        // Un seul locataire actif à la fois par lot
        if (property.getTenant() != null) {
            throw new BadRequestException("Ce lot a déjà un locataire actif");
        }

        // Vérifie que l'email et le téléphone ne sont pas déjà utilisés
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Cet email est déjà utilisé");
        }
        if (userRepository.existsByPhone(phone)) {
            throw new BadRequestException("Ce numéro de téléphone est déjà utilisé");
        }

        // Récupère le rôle Locataire
        Role tenantRole = roleRepository.findByName(ERole.ROLE_LOCATAIRE)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle Locataire introuvable"));

        // Upload la photo si fournie
        String photoUrl = null;
        if (photo != null && !photo.isEmpty()) {
            photoUrl = minioService.uploadFile(photo, "tenants");
        }

        // Crée le compte utilisateur du locataire — accès direct, mot de passe temporaire par email,
        // comme pour un copropriétaire créé par le syndic
        String temporaryPassword = PasswordGeneratorUtil.generateTemporaryPassword();

        User tenant = new User();
        tenant.setFirstName(firstName);
        tenant.setLastName(lastName);
        tenant.setEmail(email);
        tenant.setPhone(phone);
        tenant.setRole(tenantRole);
        tenant.setStatus(UserStatus.ACTIVE);
        tenant.setPassword(passwordEncoder.encode(temporaryPassword));
        tenant.setProfilePhotoUrl(photoUrl);

        User savedTenant = userRepository.save(tenant);

        // Associe le locataire au lot
        property.setTenant(savedTenant);
        property.setTenantAssignedAt(LocalDateTime.now());
        Property savedProperty = propertyRepository.save(property);

        // Envoie les identifiants de connexion par email (mot de passe temporaire)
        emailService.sendTenantAccountCreated(savedTenant.getEmail(), temporaryPassword, savedTenant.getFirstName());

        // Notifie le propriétaire qu'un locataire a été ajouté à son bien
        notificationService.sendPush(
                property.getOwner().getId(),
                "Nouveau locataire",
                firstName + " " + lastName + " a été ajouté comme locataire de votre bien " + property.getReference());

        log.info("Locataire '{}' créé et rattaché au lot '{}' par le syndic {}",
                savedTenant.getEmail(), property.getReference(), getCurrentUser().getEmail());

        return mapToPropertyDTO(savedProperty);
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

        // Récupérer le poste budgétaire le plus récent lié à cet équipement, s'il existe
        Optional<BudgetItem> linkedBudgetItem = budgetItemRepository
                .findFirstByCommonFacilityIdOrderByBudgetAnneeDesc(facilityId);

        BigDecimal budgetAmount = linkedBudgetItem.map(BudgetItem::getMontant).orElse(BigDecimal.ZERO);

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
                .details(facility.getDescription())
                .residenceName(residence.getName())
                .city(residence.getCity())
                .status(status)
                .lastMaintenanceDate(lastMaintenanceDate)
                .interventionHistory(interventionHistory)
                .budgetAmount(budgetAmount)
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
    // RÉPARTITION DES VRAIES DÉPENSES PAR CATÉGORIE (ONGLET FINANCES)
    // Basé sur les transactions Wallet (TRAVAUX) et non sur le budget prévisionnel
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public ExpenseBreakdownDTO getExpensesBreakdown(Long residenceId, Integer year) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Année courante par défaut si non fournie
        int targetYear = (year != null) ? year : Year.now().getValue();

        // Récupérer toutes les transactions TRAVAUX de cette résidence pour l'année demandée
        List<SyndicWalletTransaction> transactions = syndicWalletTransactionRepository
                .findTravauxByResidenceAndYear(residenceId, targetYear);

        // Si aucune transaction trouvée, retourner une réponse vide
        if (transactions == null || transactions.isEmpty()) {
            return ExpenseBreakdownDTO.builder()
                    .totalAmount(BigDecimal.ZERO)
                    .categories(List.of())
                    .build();
        }
        //Sinon
        // Regrouper les transactions par label et calculer les montants
        // Les montants TRAVAUX sont négatifs, on utilise la valeur absolue pour afficher des dépenses positives
        Map<String, BigDecimal> groupedAmounts = new HashMap<>();

        for (SyndicWalletTransaction tx : transactions) {
            String label = tx.getLabel();
            // Valeur absolue car les dépenses TRAVAUX sont stockées négatives
            BigDecimal amount = tx.getAmount().abs();

            if (groupedAmounts.containsKey(label)) {
                // Additionner au montant existant pour ce label
                BigDecimal current = groupedAmounts.get(label);
                groupedAmounts.put(label, current.add(amount));
            } else {
                // Nouveau label, initialiser avec ce montant
                groupedAmounts.put(label, amount);
            }
        }

        // Calculer le montant total des dépenses
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (BigDecimal amount : groupedAmounts.values()) {
            totalAmount = totalAmount.add(amount);
        }

        // Construire la liste des catégories avec leurs pourcentages
        List<ExpenseCategoryDTO> categories = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : groupedAmounts.entrySet()) {
            String label = entry.getKey();
            BigDecimal amount = entry.getValue();

            // Calculer le pourcentage (éviter division par zéro)
            Double percentage = 0.0;
            if (totalAmount.compareTo(BigDecimal.ZERO) > 0) {
                percentage = amount.divide(totalAmount, 4,RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .doubleValue();
            }

            categories.add(ExpenseCategoryDTO.builder()
                    .label(label)
                    .amount(amount)
                    .percentage(percentage)
                    .build());
        }

        return ExpenseBreakdownDTO.builder()
                .totalAmount(totalAmount)
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
            ChargeItemPaymentStatus status = item.getStatus();

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

    // =========================================================================
    // JOURNAL D'ACTIVITÉ D'UNE RÉSIDENCE (PANNEAU ACTIVITÉ RÉCENTE)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public Page<ActivityLogItemDTO> getActivityLog(Long residenceId, int page, int size, String scope) {

        // Vérifier l'appartenance de la résidence au syndic
        Residence residence = getResidenceOrThrow(residenceId);
        verifyResidenceOwnership(residence);

        // Déterminer le filtre relatedEntityType selon le scope
        // scope=interventions → filtre sur "INTERVENTION"
        // autre ou null → pas de filtre (tout le journal)
        String relatedEntityType = null;
        if ("interventions".equals(scope)) {
            relatedEntityType = "INTERVENTION";
        }

        // Créer un Pageable pour la pagination
        Pageable pageable = PageRequest.of(page, size);

        // Récupérer les logs selon le filtre
        Page<ActivityLog> logsPage;
        if (relatedEntityType != null) {
            logsPage = activityLogRepository.findByResidenceIdAndRelatedEntityTypeOrderByCreatedAtDesc(
                    residenceId, relatedEntityType, pageable);
        } else {
            logsPage = activityLogRepository.findByResidenceIdOrderByCreatedAtDesc(residenceId, pageable);
        }

        // Convertir en DTOs
        List<ActivityLogItemDTO> dtoList = new ArrayList<>();
        for (ActivityLog log : logsPage.getContent()) {
            // Nom complet de l'acteur (null si pas d'acteur)
            String actorName = null;
            if (log.getActor() != null) {
                actorName = log.getActor().getFirstName() + " " + log.getActor().getLastName();
            }

            dtoList.add(ActivityLogItemDTO.builder()
                    .id(log.getId())
                    .type(log.getType())
                    .message(log.getMessage())
                    .detail(log.getDetail())
                    .actorName(actorName)
                    .createdAt(log.getCreatedAt())
                    .build());
        }

        // Retourner une nouvelle Page avec les DTOs
        return new PageImpl<>(dtoList, pageable, logsPage.getTotalElements());
    }


    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================


    // Crée ou met à jour un équipement commun — réutilisé par createResidenceFull
    private void upsertFacility(Residence residence, User currentSyndic, AddFacilityDTO dto) {

        // Récupère le type d'équipement — uniquement s'il appartient au syndic connecté
        FacilityType facilityType = facilityTypeRepository.findByIdAndSyndicId(dto.getFacilityTypeId(), currentSyndic.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Type d'équipement introuvable"));

        // Vérifie si un équipement de ce type existe déjà pour cette résidence
        CommonFacility facility = facilityRepository
                .findByResidenceIdAndFacilityTypeId(residence.getId(), dto.getFacilityTypeId())
                .orElse(new CommonFacility());

        // Crée ou met à jour l'équipement
        facility.setFacilityType(facilityType);
        facility.setResidence(residence);
        facility.setDescription(dto.getDescription());

        facilityRepository.save(facility);
        log.info("Équipement '{}' sauvegardé pour la résidence '{}'",
                facilityType.getName(), residence.getName());
    }


    /**
     * Convertit une SyndicWalletTransaction en WalletTransactionDTO
     * Le montant est stocké avec son signe (positif pour entrées, négatif pour sorties)
     */
    private WalletTransactionDTO toDTO(SyndicWalletTransaction tw) {
        return WalletTransactionDTO.builder()
                .id(tw.getId())
                .label(tw.getLabel())
                .reference(tw.getReference())
                .transactionDate(tw.getTransactionDate())
                .amount(tw.getAmount())
                .mode(tw.getMode())
                .category(tw.getCategory())
                .build();
    }


    // =========================================================================
    // MÉTHODES PRIVÉES UTILITAIRES
    // =========================================================================

    /**
     * Calcule le solde du wallet à une date donnée
     * Si walletId est null (pas de wallet créé), retourne ZERO
     */
    private BigDecimal calculerSoldeADate(Long walletId, LocalDateTime asOfDate) {
        if (walletId == null) return BigDecimal.ZERO;
        return syndicWalletTransactionRepository.sumTransactionsUpTo(walletId, asOfDate);
    }

    /**
     * Calcule la variation en pourcentage entre deux montants
     * Si le montant précédent est ZERO, retourne ZERO (évite division par zéro)
     * Le résultat peut être négatif (baisse) ou positif (hausse)
     */
    private BigDecimal calculerVariation(BigDecimal actuel, BigDecimal precedent) {
        if (precedent.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return actuel.subtract(precedent)
                .divide(precedent, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2,RoundingMode.HALF_UP);
    }

    // calculatePropertyStatus supprimée — remplacée par la lecture directe de
    // Property.displayStatus (persisté, recalculé par StatusRecalculationService)

    /**
     * Calcule la charge pour un lot précis
     * Formule : ChargeCallItem.quotePart proportionnel à (Property.share / ChargeCallItem.tantieme)
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
                .multiply(property.getShare())
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


    // calculateHealthStatus supprimée — déplacée dans StatusRecalculationService,
    // seule source de vérité pour ce calcul désormais

    // Calcule le tantième proportionnel à la superficie totale de la résidence
    private BigDecimal calculateShare(BigDecimal area, BigDecimal totalArea) {
        return area.divide(totalArea, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100));
    }

    // Construit une Property (non sauvegardée) à partir de son DTO — réutilisé par
    // addProperties et createResidenceFull
    private Property buildProperty(Residence residence, User currentSyndic, AddPropertyDTO dto) {

        // Vérifie que la référence n'existe pas déjà pour cette résidence
        if (propertyRepository.existsByReferenceAndResidenceId(dto.getReference(), residence.getId())) {
            throw new BadRequestException(
                    "La référence '" + dto.getReference() + "' existe déjà pour cette résidence");
        }

        Property property = new Property();
        property.setReference(dto.getReference());
        property.setBloc(dto.getBloc());
        property.setFloor(dto.getFloor());
        property.setArea(dto.getArea());
        property.setResidence(residence);

        // Calcule le tantième proportionnel à la superficie totale de la résidence
        property.setShare(calculateShare(dto.getArea(), residence.getTotalArea()));

        // Récupère le type de bien — uniquement s'il appartient au syndic connecté
        PropertyType propertyType = propertyTypeRepository.findByIdAndSyndicId(dto.getPropertyTypeId(), currentSyndic.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Type de bien introuvable"));
        property.setTypeBien(propertyType);

        // Associe un propriétaire si fourni et calcule le statut
        if (dto.getOwnerId() != null) {
            User owner = userRepository.findById(dto.getOwnerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Copropriétaire introuvable"));

            // Vérifie que c'est bien un copropriétaire
            if (!owner.getRole().getName().equals(ERole.ROLE_COPROPRIETAIRE)) {
                throw new BadRequestException(
                        "Seul un copropriétaire peut être propriétaire d'un lot — l'utilisateur choisi n'est pas un copropriétaire");
            }

            property.setOwner(owner);
            property.setStatus(PropertyStatus.OCCUPIED);
        } else {
            property.setStatus(PropertyStatus.VACANT);
        }

        // Un lot fraîchement créé n'a encore aucun historique de charges : le displayStatus
        // correspond donc toujours au statut brut (jamais LATE/UNPAID à la création)
        property.setDisplayStatus(property.getStatus() == PropertyStatus.OCCUPIED
                ? PropertyDisplayStatus.OCCUPIED
                : PropertyDisplayStatus.VACANT);

        return property;
    }

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
        String presignedPhotoUrl = res.getPhotoUrl();

        // Récupère le budget le plus récent de la résidence
        BigDecimal annualBudget = budgetRepository.findMostRecentByResidenceId(res.getId())
                .map(Budget::getBudgetTotal)
                .orElse(null);

        return ResidenceDTO.builder()
            .id(res.getId())
            .name(res.getName())
            .description(res.getDescription())
            .photoUrl(presignedPhotoUrl)
            .fullAddress(res.getFullAddress())
            .city(res.getCity())
            .country(res.getCountry())
            .latitude(res.getLatitude())
            .longitude(res.getLongitude())
            .lotsCount(res.getLotsCount())
            .totalArea(res.getTotalArea())
            .constructionDate(res.getConstructionDate())
            .renovationDate(res.getRenovationDate())
            .annualBudget(annualBudget)
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
            .area(p.getArea())
            .share(p.getShare())
            .typeName(p.getTypeBien() != null ? p.getTypeBien().getName() : null)
            .residenceId(p.getResidence().getId())
            .residenceName(p.getResidence().getName())
            .ownerId(p.getOwner() != null ? p.getOwner().getId() : null)
            .ownerName(p.getOwner() != null
                ? p.getOwner().getFirstName() + " " + p.getOwner().getLastName()
                : null)
            .tenantId(p.getTenant() != null ? p.getTenant().getId() : null)
            .tenantName(p.getTenant() != null
                ? p.getTenant().getFirstName() + " " + p.getTenant().getLastName()
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
            .area(p.getArea())
            .share(p.getShare())
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
