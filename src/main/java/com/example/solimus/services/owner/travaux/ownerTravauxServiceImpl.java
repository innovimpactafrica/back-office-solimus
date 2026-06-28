package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerTimelineStepDTO;
import com.example.solimus.dtos.owner.travaux.ProviderInfoDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionSummaryDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


@Service
@RequiredArgsConstructor
public class ownerTravauxServiceImpl implements  ownerTraveauxService{

    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final ResidenceRepository residenceRepository;
    private final SpecialtyRepository specialtyRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final QuoteRepository quoteRepository;
    private final GeolocationService geolocationService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final MinioService minioService;
    private final ReviewRepository reviewRepository;

    @Value("${solimus.geolocation.search-radius-km:30.0}")
    private double searchRadiusKm; // Rayon de recherche des prestataires (30km par défaut)

    @Value("${provider.gps.freshness-minutes:60}")
    private int gpsFreshnessMinutes; // Durée de validité d'une localisation GPS (60 minutes par défaut)

    // =========================================================================
    // Recupére les résidences du copropriétaire connecté
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ResidenceDTO> getMyResidences() {

        User currentOwner = getCurrentUser();

        return propertyRepository.findAllByOwnerId(currentOwner.getId()).stream()
                // on remonte de chaque bien vers sa résidence
                .map(Property::getResidence)
                // on exclut les biens sans résidence (cas anormal mais défensif)
                .filter(residence -> residence != null)
                // un copropriétaire peut avoir plusieurs biens dans la même résidence — on déduplique
                .distinct()
                // on convertit chaque résidence en DTO
                .map(this::mapToResidenceDTO)
                .toList();
    }

    // =========================================================================
    // Lister les parties communes d'une résidence où le owner a au moins un bien
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId) {
        // Récupérer le copropriétaire connecté
        User currentOwner = getCurrentUser();

        // Vérifier que le copropriétaire a au moins un bien dans cette résidence
        // — sécurité : empêcher d'accéder aux équipements communes d'une résidence qui ne lui appartient pas
        boolean hasPropertyInResidence = propertyRepository.existsByOwnerIdAndResidenceId(currentOwner.getId(), residenceId);

        if (!hasPropertyInResidence) {
            throw new ForbiddenException("Vous n'avez pas de bien dans cette résidence");
        }

        // Retourner les équipements communes de la résidence
        return commonFacilityRepository.findByResidenceId(residenceId).stream()
                .map(cf -> CommonFacilityDTO.builder()
                        .id(cf.getId())
                        .label(cf.getFacilityType().getName())
                        .build())
                .toList();
    }

    // =========================================================================
    // Lister mes biens dans une résidence donnée
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<PropertyDTO> getMyPropertiesByResidence(Long residenceId) {
        // Récupérer le copropriétaire connecté
        User currentOwner = getCurrentUser();

        // Récupérer directement les biens du propriétaire dans cette résidence
        return propertyRepository.findByOwnerIdAndResidenceId(currentOwner.getId(), residenceId)
                .stream()
                .map(this::mapToPropertyDTO)
                .toList();
    }

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================

    @Override
    @Transactional
    public void createIntervention(CreateOwnerInterventionRequestDTO dto) {
        // Récupérer le copropriétaire connecté
        User currentOwner = getCurrentUser();

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le copropriétaire a au moins un bien dans cette résidence
        if (!propertyRepository.existsByOwnerIdAndResidenceId(currentOwner.getId(), dto.getResidenceId())) {
            throw new ForbiddenException("Vous n'avez pas de bien dans cette résidence");
        }

        // Récupérer la spécialité
        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        // Créer la demande d'intervention
        InterventionRequest request = new InterventionRequest();
        request.setReference(genererReference());
        request.setTitle(dto.getTitle());
        request.setDescription(dto.getDescription());
        request.addStatusHistory(InterventionStatus.PENDING, currentOwner);
        request.setInitiatedBy(InitiatedBy.OWNER);
        request.setOwner(currentOwner);
        request.setResidence(residence);
        request.setSpecialty(specialty);
        request.setPhotoUrls(dto.getPhotoUrls());
        request.setUrgencyLevel(dto.getUrgencyLevel());
        request.setLocationType(dto.getLocationType());

        // Règles selon le type de demande de travaux
        if (dto.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            // PARTIE_COMMUNE : pas de bien, équipement commun obligatoire
            if (dto.getPropertyId() != null) {
                throw new BadRequestException("Pour une demande de travaux de type PARTIE_COMMUNE, aucun bien ne doit être spécifié");
            }
            if (dto.getCommonFacilityId() == null) {
                throw new BadRequestException("Pour une demande de travaux de type PARTIE_COMMUNE, l'équipement commun concerné doit être spécifié");
            }

            // Récupérer et vérifier l'équipement commun
            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));

            // Vérifier que l'équipement appartient à la résidence
            if (commonFacility.getResidence() == null || !commonFacility.getResidence().getId().equals(dto.getResidenceId())) {
                throw new BadRequestException("Cet équipement commun n'appartient pas à cette résidence");
            }

            // Pour les parties communes, c'est géré par le syndic
            request.setManagementMode(InterventionManagementMode.SYNDIC);
            request.setProperty(null);
            request.setCommonFacility(commonFacility);

            // Notifier le syndic de la résidence de cette demande de partie commune
            notifySyndicForCommonFacilityRequest(request, residence, currentOwner);
        } else if (dto.getLocationType() == IncidentLocationType.APPARTEMENT) {
            // APPARTEMENT : bien obligatoire, pas d'équipement commun
            if (dto.getPropertyId() == null) {
                throw new BadRequestException("Pour une demande de travaux de type APPARTEMENT, un bien doit être spécifié");
            }
            if (dto.getCommonFacilityId() != null) {
                throw new BadRequestException("Pour une demande de travaux de type APPARTEMENT, aucun équipement commun ne doit être spécifié");
            }

            // Récupérer le bien
            Property property = propertyRepository.findById(dto.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

            // Vérifier que ce bien précis appartient au copropriétaire dans cette résidence (owner + résidence + id du bien en une seule requête)
            if (!propertyRepository.existsByIdAndOwnerIdAndResidenceId(dto.getPropertyId(), currentOwner.getId(), dto.getResidenceId())) {
                throw new ForbiddenException("Ce bien ne vous appartient pas ou n'appartient pas à cette résidence");
            }

            // Pour les appartements, c'est géré par le copropriétaire
            request.setManagementMode(InterventionManagementMode.OWNER);
            request.setProperty(property);
            request.setCommonFacility(null);

            // Diffuser la demande aux prestataires proches (géré par le copropriétaire)
            notifyNearbyProvidersForApartmentRequest(request, residence, specialty);
        }

        // Sauvegarder la demande d'intervention
        interventionRequestRepository.save(request);
    }

    // =========================================================================
    // LISTER MES DEMANDES DE TRAVAUX (recherche + filtres + pagination)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public OwnerInterventionDTO getMyInterventions(String search, InterventionStatus status, Long residenceId, int page, int size) {

        // Récupérer le copropriétaire connecté
        User currentOwner = getCurrentUser();

        // Compteurs pour l'en-tête (tiennent compte du filtre par résidence si fourni)
        long totalIncidents = interventionRequestRepository.countByOwner(currentOwner, residenceId);
        long enCoursCount = interventionRequestRepository.countByOwnerAndStatus(currentOwner, InterventionStatus.STARTED, residenceId);

        // Normaliser la recherche : une chaîne vide est traitée comme "pas de filtre"
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        // Pagination + tri par date de création décroissante (plus récent en premier)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Récupérer la page filtrée (search, status, residenceId sont tous optionnels)
        Page<InterventionRequest> interventions = interventionRequestRepository
                .findByOwnerWithFiltersAndResidence(currentOwner, normalizedSearch, status, residenceId, pageable);

        // Convertir chaque entité en DTO de résumé (carte)
        Page<OwnerInterventionSummaryDTO> interventionsPage = interventions.map(this::mapToSummaryDTO);

        return OwnerInterventionDTO.builder()
                .totalIncidents(totalIncidents)
                .enCoursCount(enCoursCount)
                .interventions(interventionsPage)
                .build();
    }

    // =========================================================================
    // DÉTAIL D'INTERVENTION
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public OwnerInterventionDetailDTO getInterventionDetail(Long interventionId) {

        // Récupérer le copropriétaire connecté
        User currentOwner = getCurrentUser();

        // Récupérer l'intervention
        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // Vérifier que l'intervention appartient au copropriétaire connecté
        if (request.getOwner() == null || !request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à voir cette intervention");
        }

        return mapToDetailDTO(request);
    }

    // =========================================================================
    // CRÉATION D'AVIS
    // =========================================================================

    @Override
    @Transactional
    public void createReview(Long interventionId, CreateReviewDTO dto) {
        User currentOwner = getCurrentUser();

        // 1. Récupérer l'intervention
        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // 2. Vérifier que l'intervention appartient au copropriétaire
        if (request.getOwner() == null || !request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à laisser un avis sur cette intervention");
        }

        // 3. Vérifier que l'intervention est terminée
        if (request.getStatus() != InterventionStatus.FINISHED && request.getStatus() != InterventionStatus.FINAL_VALIDATION) {
            throw new BadRequestException("L'intervention doit être terminée pour laisser un avis");
        }

        // 4. Vérifier qu'un prestataire a été sélectionné
        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun prestataire sélectionné pour cette intervention");
        }

        // 5. Vérifier qu'un avis n'existe pas déjà
        if (reviewRepository.existsByInterventionRequestId(interventionId)) {
            throw new BadRequestException("Un avis a déjà été laissé pour cette intervention");
        }

        // 6. Créer l'avis
        Review review = new Review();
        review.setInterventionRequest(request);
        review.setReviewer(currentOwner);
        review.setProvider(request.getSelectedProvider());
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        reviewRepository.save(review);
    }

    // =========================================================================
    // Méthodes utilitaires
    // =========================================================================

    // Récupère l'utilisateur actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    /**
     * Génère une référence unique pour la demande d'intervention.
     * Format : TRV-XXX (ex: TRV-001, TRV-010, TRV-1000)
     */
    private String genererReference() {
        // On compte le nombre total de demandes déjà existantes en base
        long totalExistant = interventionRequestRepository.count();
        // On ajoute 1 pour obtenir le numéro de la prochaine demande
        long prochainNumero = totalExistant + 1;
        // On formate en "TRV-" suivi d'au minimum 3 chiffres (ex: TRV-001, TRV-010, TRV-1000)
        return String.format("TRV-%03d", prochainNumero);
    }

    private ResidenceDTO mapToResidenceDTO(Residence residence) {
        return ResidenceDTO.builder()
                .id(residence.getId())
                .name(residence.getName())
                .fullAddress(residence.getFullAddress())
                .latitude(residence.getLatitude())
                .longitude(residence.getLongitude())
                .lotsCount(residence.getLotsCount())
                // si la résidence n'a pas de syndic assigné, on retourne null
                .syndicId(residence.getSyndic() != null ? residence.getSyndic().getId() : null)
                // on concatène prénom + nom du syndic, null si absent
                .syndicName(residence.getSyndic() != null ? residence.getSyndic().getFirstName() + " " + residence.getSyndic().getLastName() : null)
                .createdAt(residence.getCreatedAt())
                .build();
    }

    private PropertyDTO mapToPropertyDTO(Property property) {
        return PropertyDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .superficie(property.getSuperficie())
                .typeName(property.getTypeBien() != null ? property.getTypeBien().getName() : null)
                .residenceId(property.getResidence() != null ? property.getResidence().getId() : null)
                .residenceName(property.getResidence() != null ? property.getResidence().getName() : null)
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }

    /**
     * Notifie le syndic de la résidence d'une demande de travaux sur partie commune créée par un copropriétaire.
     * Envoie une notification push et un email au syndic si les notifications sont activées.
     */
    private void notifySyndicForCommonFacilityRequest(InterventionRequest request, Residence residence, User owner) {
        // Vérifier que la résidence a un syndic assigné
        if (residence.getSyndic() == null) {
            return; // Pas de syndic à notifier
        }

        User syndic = residence.getSyndic();
        String ownerName = owner.getFirstName() + " " + owner.getLastName();

        // Envoyer notification push et email si le syndic a activé les notifications
        if (syndic.isNotificationsEnabled()) {
            notificationService.sendPush(
                    syndic.getId(),
                    "Nouvelle demande de travaux - Partie commune",
                    "Un copropriétaire a signalé un problème : " + request.getTitle()
            );

            emailService.sendSyndicInterventionNotification(
                    syndic.getEmail(),
                    syndic.getFirstName(),
                    request.getTitle(),
                    residence.getName(),
                    ownerName
            );
        }
    }

    /**
     * Diffuse une demande de travaux sur appartement aux prestataires proches.
     * Suit la même logique que le syndic : filtre par spécialité, abonnement actif, et distance GPS.
     */
    private void notifyNearbyProvidersForApartmentRequest(InterventionRequest request, Residence residence, Specialty specialty) {
        // Vérifier que la résidence a des coordonnées GPS pour calculer les distances
        if (residence.getLatitude() == null || residence.getLongitude() == null) {
            throw new BadRequestException("La résidence n'a pas de coordonnées GPS, impossible de trouver des prestataires proches");
        }

        // Étape 1 : récupérer les prestataires actifs pour cette spécialité
        List<ProviderProfile> candidates = providerProfileRepository
                .findActiveProvidersBySpecialty(specialty.getId());

        // Étape 2 : ne garder que les prestataires avec un abonnement actuellement actif
        List<ProviderProfile> activeSubscribers = candidates.stream()
                .filter(profile -> subscriptionRepository
                        .findFirstByProviderIdOrderByEndDateDesc(profile.getUser().getId())
                        .map(Subscription::isCurrentlyActive)
                        .orElse(false))
                .toList();

        // Étape 3 : calculer la distance et ne garder que les prestataires dans le rayon autorisé
        List<ProviderProfile> nearbyProviders = new ArrayList<>();
        LocalDateTime gpsFreshnessThreshold = LocalDateTime.now().minusMinutes(gpsFreshnessMinutes);

        for (ProviderProfile profile : activeSubscribers) {
            // Vérifier si le GPS du prestataire est récent et valide
            boolean gpsValid = profile.getGpsLatitude() != null
                    && profile.getGpsLongitude() != null
                    && profile.getGpsUpdatedAt() != null
                    && profile.getGpsUpdatedAt().isAfter(gpsFreshnessThreshold);

            // Utiliser le GPS récent si valide, sinon la zone de référence d'inscription
            double providerLat = gpsValid
                    ? profile.getGpsLatitude().doubleValue()
                    : profile.getLatitude().doubleValue();

            double providerLon = gpsValid
                    ? profile.getGpsLongitude().doubleValue()
                    : profile.getLongitude().doubleValue();

            // Calculer la distance entre la résidence et le prestataire
            double distance = geolocationService.calculateDistance(
                    residence.getLatitude().doubleValue(),
                    residence.getLongitude().doubleValue(),
                    providerLat,
                    providerLon
            );

            // Ne garder que les prestataires dans le rayon de recherche
            if (distance <= searchRadiusKm) {
                nearbyProviders.add(profile);
            }
        }

        // Étape 4 : notifier chaque prestataire trouvé et l'ajouter à la liste des notifiés
        for (ProviderProfile profile : nearbyProviders) {
            User provider = profile.getUser();

            // Envoyer notification push et email si activés
            if (provider.isNotificationsEnabled()) {
                notificationService.sendPush(
                        provider.getId(),
                        "Nouvelle demande de travaux",
                        "Une nouvelle demande correspond à votre spécialité : " + request.getTitle()
                );

                emailService.sendInterventionNotification(
                        provider.getEmail(),
                        provider.getFirstName(),
                        request.getTitle(),
                        residence.getName()
                );
            }

            // Ajouter le prestataire à la liste des notifiés pour cette demande
            request.getNotifiedProviders().add(provider);
        }
    }

    private OwnerInterventionSummaryDTO mapToSummaryDTO(InterventionRequest intervention) {
        return OwnerInterventionSummaryDTO.builder()
                .id(intervention.getId())
                .title(intervention.getTitle())
                .residenceName(intervention.getResidence() != null ? intervention.getResidence().getName() : null)
                .propertyReference(intervention.getProperty() != null ? intervention.getProperty().getReference() : null)
                .commonFacilityName(intervention.getCommonFacility() != null ? intervention.getCommonFacility().getFacilityType().getName() : null)
                .specialtyName(intervention.getSpecialty() != null ? intervention.getSpecialty().getName() : null)
                .specialtyIcon(intervention.getSpecialty() != null ? intervention.getSpecialty().getIcon() : null)
                .statusLabel(intervention.getStatus() != null ? intervention.getStatus().getLabel() : null)
                .status(intervention.getStatus())
                .urgencyLabel(intervention.getUrgencyLevel() != null ? intervention.getUrgencyLevel().getLabel() : null)
                .urgencyLevel(intervention.getUrgencyLevel())
                .createdAt(intervention.getCreatedAt())
                .build();
    }

    private OwnerInterventionDetailDTO mapToDetailDTO(InterventionRequest request) {
        // Récupérer les infos du prestataire sélectionné si présent
        ProviderInfoDTO selectedProviderInfo = null;
        if (request.getSelectedProvider() != null) {
            ProviderProfile profile = providerProfileRepository.findByUser(request.getSelectedProvider()).orElse(null);
            selectedProviderInfo = ProviderInfoDTO.builder()
                    .id(request.getSelectedProvider().getId())
                    .firstName(request.getSelectedProvider().getFirstName())
                    .lastName(request.getSelectedProvider().getLastName())
                    .companyName(profile != null ? profile.getCompanyName() : null)
                    .build();
        }

        // Déterminer si la demande concerne un appartement ou une partie commune
        boolean isCommonArea = request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE;

        // CAS APPARTEMENT → référence du bien
        // CAS PARTIE_COMMUNE → null
        String propertyReference = null;
        if (!isCommonArea && request.getProperty() != null) {
            propertyReference = request.getProperty().getReference();
        }

        // CAS PARTIE_COMMUNE → nom de la partie commune
        // CAS APPARTEMENT → null
        String commonFacilityName = null;
        if (isCommonArea && request.getCommonFacility() != null) {
            commonFacilityName = request.getCommonFacility().getFacilityType().getName();
        }

        // Convertir les chemins photos en URLs signées MinIO
        List<String> photoUrls = minioService.toPresignedUrls(request.getPhotoUrls());

        return OwnerInterventionDetailDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : null)
                .propertyReference(propertyReference)
                .commonFacilityName(commonFacilityName)
                .createdAt(request.getCreatedAt())
                .statusLabel(request.getStatus() != null ? request.getStatus().getLabel() : null)
                .status(request.getStatus())
                .urgencyLabel(request.getUrgencyLevel() != null ? request.getUrgencyLevel().getLabel() : null)
                .urgencyLevel(request.getUrgencyLevel())
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .specialtyIcon(request.getSpecialty() != null ? request.getSpecialty().getIcon() : null)
                .photoUrls(photoUrls)
                .selectedProvider(selectedProviderInfo)
                .timeline(buildTimeline(request))
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .build();
    }

    private List<OwnerTimelineStepDTO> buildTimeline(InterventionRequest request) {

        List<OwnerTimelineStepDTO> timeline = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // Étape 1 : Incident envoyé (toujours complété)
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Incident envoyé")
                .date(request.getCreatedAt())
                .completed(true)
                .build());

        // Étape 2 : Devis reçu (complété si au moins un devis existe)
        Optional<Quote> firstQuote = quoteRepository.findFirstByInterventionRequestOrderByCreatedAtAsc(request);
        boolean hasQuote = firstQuote.isPresent();
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Devis reçu")
                .date(hasQuote ? firstQuote.get().getCreatedAt() : null)
                .completed(hasQuote)
                .build());

        // Étape 3 : Validation devis (complété si un prestataire est sélectionné)
        boolean hasSelectedProvider = request.getSelectedProvider() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Validation devis")
                .date(hasSelectedProvider ? request.getQuoteAcceptedAt() : null)
                .completed(hasSelectedProvider)
                .build());

        // Étape 4 : Intervention démarrée (complété si startedAt est renseigné)
        boolean started = request.getStartedAt() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Intervention démarrée")
                .date(request.getStartedAt())
                .completed(started)
                .build());

        // Étape 5 : Travail terminé (complété si finishedAt est renseigné)
        boolean finished = request.getFinishedAt() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Travail terminé")
                .date(request.getFinishedAt())
                .completed(finished)
                .build());

        // Étape 6 : Validation finale (complété si statut est FINAL_VALIDATION)
        boolean completed = request.getStatus() == InterventionStatus. FINAL_VALIDATION;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Validation finale")
                .date(completed ? request.getFinishedAt() : null)
                .completed(completed)
                .build());

        return timeline;
    }


}
