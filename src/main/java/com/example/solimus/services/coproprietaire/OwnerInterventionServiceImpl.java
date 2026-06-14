package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.admin.SpecialtyDTO;
import com.example.solimus.dtos.intervention.*;
import com.example.solimus.dtos.provider.QuoteLineDTO;
import com.example.solimus.dtos.residence.CommonFacilityDTO;
import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.entities.CommonFacility;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Quote;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.Specialty;
import com.example.solimus.entities.User;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.CommonFacilityRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ProviderRatingRepository;
import com.example.solimus.repositories.QuoteRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.SpecialtyRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.example.solimus.enums.IncidentLocationType.APPARTEMENT;

@Service
@RequiredArgsConstructor
@Slf4j
public class OwnerInterventionServiceImpl implements OwnerInterventionService {

    private final UserRepository userRepository;
    private final InterventionRequestRepository interventionRepository;
    private final PropertyRepository propertyRepository;
    private final ResidenceRepository residenceRepository;
    private final SpecialtyRepository specialtyRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final QuoteRepository quoteRepository;
    private final ProviderRatingRepository providerRatingRepository;
    private final EmailService emailService;
    private final GeolocationService geolocationService;

    @Override
    @Transactional(readOnly = true)
    public List<SpecialtyDTO> getAllSpecialties() {
        return specialtyRepository.findAll().stream()
                .map(s -> new SpecialtyDTO(s.getId(), s.getName(), null, s.getIcon()))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ResidenceDTO> getMyResidences() {
        User currentOwner = getCurrentUser();

        return propertyRepository.findAllByOwnerId(currentOwner.getId()).stream()
                .map(Property::getResidence)
                .filter(residence -> residence != null)
                .distinct()
                .map(this::mapToResidenceDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId) {
        return commonFacilityRepository.findByResidenceId(residenceId).stream()
                .map(cf -> CommonFacilityDTO.builder()
                        .id(cf.getId())
                        .label(cf.getFacilityType().getLabel())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NearbyProviderDTO> findNearbyProviders(Long specialtyId) {
        User currentOwner = getCurrentUser();
        
        // Récupérer la propriété du copropriétaire
        List<Property> properties = propertyRepository.findAllByOwnerId(currentOwner.getId());
        if (properties.isEmpty()) {
            throw new ResourceNotFoundException("Aucune propriété trouvée pour ce copropriétaire");
        }
        Property property = properties.get(0);
        
        Residence residence = property.getResidence();
        
        // Réutiliser la logique Haversine existante
        List<User> nearbyProviders = userRepository.findNearbyProviders(
                residence.getLatitude().doubleValue(),
                residence.getLongitude().doubleValue(),
                specialtyId,
                30.0 // rayon par défaut
        );
        
        return nearbyProviders.stream()
                .map(this::mapToNearbyProviderDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public OwnerInterventionDetailDTO createIntervention(CreateOwnerInterventionRequestDTO dto, List<String> photoUrls) {
        User currentOwner = getCurrentUser();

        // Avertissement si aucune photo n'est fournie
        if (photoUrls == null || photoUrls.isEmpty()) {
            log.warn("Intervention créée sans photo par le copropriétaire {} : {}", currentOwner.getEmail(), dto.getTitle());
        }

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que le copropriétaire a au moins un bien dans cette résidence
        List<Property> ownerPropertiesInResidence = propertyRepository.findAllByOwnerId(currentOwner.getId()).stream()
                .filter(p -> p.getResidence() != null && p.getResidence().getId().equals(dto.getResidenceId()))
                .toList();

        if (ownerPropertiesInResidence.isEmpty()) {
            throw new ForbiddenException("Vous n'avez pas de bien dans cette résidence");
        }

        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        InterventionRequest request = new InterventionRequest();
        request.setTitle(dto.getTitle());
        request.setDescription(dto.getDescription());
        request.addStatusHistory(InterventionStatus.PENDING, currentOwner);
        request.setInitiatedBy(InitiatedBy.COPROPRIETAIRE);
        request.setOwner(currentOwner);
        request.setResidence(residence);
        request.setSpecialty(specialty);
        request.setLocationType(dto.getLocationType());
        request.setUrgencyLevel(dto.getUrgencyLevel());
        request.setPhotoUrls(photoUrls != null ? photoUrls : new ArrayList<>());

        // Règles selon le type d'incident
        if (dto.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            // PARTIE_COMMUNE : pas de bien, gestion forcée par syndic, partie commune obligatoire
            if (dto.getPropertyId() != null) {
                throw new BadRequestException("Pour un incident de type PARTIE_COMMUNE, aucun bien ne doit être spécifié");
            }
            if (dto.getCommonFacilityId() == null) {
                throw new BadRequestException("Pour un incident de type PARTIE_COMMUNE, la partie commune concernée doit être spécifiée");
            }
            if (dto.getManagementMode() == InterventionManagementMode.OWNER) {
                throw new BadRequestException("Le copropriétaire ne peut pas gérer les parties communes. Le mode de gestion doit être SYNDIC.");
            }

            // Récupérer et vérifier la partie commune
            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Partie commune introuvable"));

            // Vérifier que la partie commune appartient à la résidence
            if (commonFacility.getResidence() == null || !commonFacility.getResidence().getId().equals(dto.getResidenceId())) {
                throw new BadRequestException("Cette partie commune n'appartient pas à cette résidence");
            }

            request.setProperty(null);
            request.setCommonFacility(commonFacility);
            request.setManagementMode(InterventionManagementMode.SYNDIC);
            request.setSyndic(residence.getSyndic());
        } else if (dto.getLocationType() == APPARTEMENT) {
            // APPARTEMENT : bien obligatoire, pas de partie commune
            if (dto.getPropertyId() == null) {
                throw new BadRequestException("Pour un incident de type APPARTEMENT, un bien doit être spécifié");
            }
            if (dto.getCommonFacilityId() != null) {
                throw new BadRequestException("Pour un incident de type APPARTEMENT, aucune partie commune ne doit être spécifiée");
            }

            // Vérifier que le bien appartient au copropriétaire et à la résidence
            Property property = ownerPropertiesInResidence.stream()
                    .filter(p -> p.getId().equals(dto.getPropertyId()))
                    .findFirst()
                    .orElseThrow(() -> new ForbiddenException("Ce bien ne vous appartient pas ou n'est pas dans cette résidence"));

            request.setProperty(property);
            request.setCommonFacility(null);

            // Gestion : OWNER ou SYNDIC
            if (dto.getManagementMode() == null) {
                throw new BadRequestException("Le mode de gestion est obligatoire pour un incident APPARTEMENT");
            }

            request.setManagementMode(dto.getManagementMode());

            if (dto.getManagementMode() == InterventionManagementMode.SYNDIC) {
                request.setSyndic(residence.getSyndic());
            } else {
                // Gestion personnelle : syndic null ou renseigné pour info
                request.setSyndic(null);
            }
        } else {
            throw new BadRequestException("Type d'incident non reconnu");
        }

        // Notification du syndic si affecté
        if (request.getSyndic() != null) {
            try {
                if (request.getSyndic().isNotificationsEnabled()) {
                    emailService.sendInterventionNotification(
                            request.getSyndic().getEmail(),
                            request.getSyndic().getFirstName(),
                            request.getTitle(),
                            residence.getName()
                    );
                    log.info("Syndic notifié pour l'intervention {} créée par le copropriétaire {}", request.getTitle(), currentOwner.getEmail());
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'envoi de l'email au syndic {}", request.getSyndic().getEmail(), e);
            }
        }

        // Diffusion automatique aux prestataires proches
        // Seulement si PAS affecté au syndic (le syndic diffusera après validation)
        if (request.getSyndic() == null) {
            if (residence.getLatitude() == null || residence.getLongitude() == null) {
                throw new BadRequestException("La résidence n'a pas de coordonnées GPS, impossible de trouver des prestataires proches");
            }

            List<User> nearbyProviders = userRepository.findNearbyProviders(
                    residence.getLatitude().doubleValue(),
                    residence.getLongitude().doubleValue(),
                    specialty.getId(),
                    30.0 // rayon de 30 km
            );

            if (nearbyProviders.isEmpty()) {
                log.warn("Aucun prestataire trouvé dans un rayon de 30 km pour la résidence {} et la spécialité {}", residence.getName(), specialty.getName());
            } else {
                request.setNotifiedProviders(nearbyProviders);

                nearbyProviders.forEach(provider -> {
                    try {
                        if (provider.isNotificationsEnabled()) {
                            emailService.sendInterventionNotification(
                                    provider.getEmail(),
                                    provider.getFirstName(),
                                    request.getTitle(),
                                    residence.getName()
                            );
                        }
                    } catch (Exception e) {
                        log.error("Erreur lors de l'envoi de l'email au prestataire {}", provider.getEmail(), e);
                    }
                });

                log.info("Diffusion automatique : {} prestataires notifiés pour l'intervention {}", nearbyProviders.size(), request.getTitle());
            }
        } else {
            log.info("Demande affectée au syndic, diffusion aux prestataires différée après validation syndic");
        }
        
        InterventionRequest saved = interventionRepository.save(request);
        log.info("Intervention créée par le copropriétaire {} : {}", currentOwner.getEmail(), saved.getTitle());
        
        return mapToDetailDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerInterventionPageDTO getMyInterventions(
            String search,
            InterventionStatus status,
            int page,
            int size) {
        User currentOwner = getCurrentUser();

        // Calculer les compteurs
        int totalIncidents = (int) interventionRepository.countByOwner(currentOwner);
        int enCoursCount = (int) interventionRepository.countByOwnerAndStatus(currentOwner, InterventionStatus.STARTED);

        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
       Pageable pageable =
               PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAt").descending());

       Page<InterventionRequest> interventions =
                interventionRepository.findByOwnerWithFilters(currentOwner, normalizedSearch, status, pageable);

        Page<OwnerInterventionSummaryDTO> interventionsPage = interventions.map(this::mapToSummaryDTO);

        return com.example.solimus.dtos.intervention.OwnerInterventionPageDTO.builder()
                .totalIncidents(totalIncidents)
                .enCoursCount(enCoursCount)
                .interventions(interventionsPage)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public OwnerInterventionDetailDTO getInterventionDetail(Long interventionId) {
        User currentOwner = getCurrentUser();
        
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));
        
        // Vérifier que l'intervention appartient au owner connecté
        if (request.getOwner() == null || !request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à voir cette intervention");
        }
        
        return mapToDetailDTO(request);
    }

    // =========================================================================
    // MAPPING HELPERS
    // =========================================================================

    private NearbyProviderDTO mapToNearbyProviderDTO(User provider) {
        return NearbyProviderDTO.builder()
                .id(provider.getId())
                .firstName(provider.getFirstName())
                .lastName(provider.getLastName())
                .companyName(provider.getCompanyName())
                .specialtyName(provider.getSpecialty() != null ? provider.getSpecialty().getName() : null)
                .rating(providerRating(provider.getId()))
                .build();
    }

    private ResidenceDTO mapToResidenceDTO(Residence residence) {
        return ResidenceDTO.builder()
                .id(residence.getId())
                .name(residence.getName())
                .fullAddress(residence.getFullAddress())
                .latitude(residence.getLatitude())
                .longitude(residence.getLongitude())
                .lotsCount(residence.getLotsCount())
                .syndicId(residence.getSyndic() != null ? residence.getSyndic().getId() : null)
                .syndicName(residence.getSyndic() != null ? residence.getSyndic().getFirstName() + " " + residence.getSyndic().getLastName() : null)
                .createdAt(residence.getCreatedAt())
                .build();
    }

    private PropertyDTO mapToPropertyDTO(Property property) {
        return PropertyDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .superficie(property.getSuperficie())
                .type(property.getTypeBien())
                .residenceId(property.getResidence() != null ? property.getResidence().getId() : null)
                .residenceName(property.getResidence() != null ? property.getResidence().getName() : null)
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }

    private OwnerInterventionSummaryDTO mapToSummaryDTO(InterventionRequest request) {

        // Déterminer si c'est un incident appartement ou partie commune
        boolean isPartieCommune = request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE;

        return OwnerInterventionSummaryDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .residenceName(request.getResidence().getName())

                // CAS APPARTEMENT → type de bien
                .typeBien(!isPartieCommune && request.getProperty() != null
                        ? request.getProperty().getTypeBien().getLabel()
                        : null)

                // CAS PARTIE COMMUNE → nom de la partie commune
                .commonFacilityName(isPartieCommune && request.getCommonFacility() != null
                        ? request.getCommonFacility().getFacilityType().getLabel()
                        : null)

                // Spécialité
                .specialtyName(request.getSpecialty() != null
                        ? request.getSpecialty().getName()
                        : null)
                .specialtyIcon(request.getSpecialty() != null
                        ? request.getSpecialty().getIcon()
                        : null)

                // Statut avec label lisible
                .statusLabel(request.getStatus().getLabel())
                .status(request.getStatus())

                // Urgence avec label lisible
                .urgencyLabel(request.getUrgencyLevel() != null
                        ? request.getUrgencyLevel().getLabel()
                        : null)
                .urgencyLevel(request.getUrgencyLevel())

                .createdAt(request.getCreatedAt())
                .build();
    }

    private OwnerInterventionDetailDTO mapToDetailDTO(InterventionRequest request) {
        ProviderInfoDTO selectedProviderInfo = null;
        if (request.getSelectedProvider() != null) {
            selectedProviderInfo = ProviderInfoDTO.builder()
                    .id(request.getSelectedProvider().getId())
                    .firstName(request.getSelectedProvider().getFirstName())
                    .lastName(request.getSelectedProvider().getLastName())
                    .companyName(request.getSelectedProvider().getCompanyName())
                    .interventionStatusLabel(null)
                    .build();
        }

        // Déterminer si c'est un incident appartement ou partie commune
        boolean isPartieCommune = request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE;

        // CAS APPARTEMENT → type de bien
        // CAS PARTIE_COMMUNE → null
        String typeBien = null;
        if (!isPartieCommune && request.getProperty() != null && request.getProperty().getTypeBien() != null) {
            typeBien = request.getProperty().getTypeBien().getLabel();
        }

        // CAS PARTIE_COMMUNE → nom de la partie commune
        // CAS APPARTEMENT → null
        String commonFacilityName = null;
        if (isPartieCommune && request.getCommonFacility() != null) {
            commonFacilityName = request.getCommonFacility().getFacilityType().getLabel();
        }

        return OwnerInterventionDetailDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .residenceName(request.getResidence().getName())
                .typeBien(typeBien)
                .commonFacilityName(commonFacilityName)
                .createdAt(request.getCreatedAt())
                .statusLabel(request.getStatus() != null ? request.getStatus().getLabel() : null)
                .status(request.getStatus())
                .urgencyLabel(request.getUrgencyLevel() != null ? request.getUrgencyLevel().getLabel() : null)
                .urgencyLevel(request.getUrgencyLevel())
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .specialtyIcon(request.getSpecialty() != null ? request.getSpecialty().getIcon() : null)
                .photoUrls(request.getPhotoUrls() != null ? new ArrayList<>(request.getPhotoUrls()) : new ArrayList<>())
                .selectedProvider(selectedProviderInfo)
                .timeline(buildTimeline(request))
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .build();
    }

    private List<OwnerTimelineStepDTO> buildTimeline(InterventionRequest request) {
        List<OwnerTimelineStepDTO> timeline = new ArrayList<>();
        
        // PENDING - Incident signalé
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Incident signalé")
                .date(request.getCreatedAt())
                .completed(true)
                .build());
        
        // QUOTE_SENT - Devis reçu
        boolean quoteSent = request.getStatus().ordinal() >= InterventionStatus.QUOTE_SENT.ordinal();
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Devis reçu")
                .date(quoteSent ? request.getValidatedAt() : null)
                .completed(quoteSent)
                .build());
        
        // SYNDIC_VALIDATED - Prestataire assigné
        boolean validated = request.getStatus().ordinal() >= InterventionStatus.SYNDIC_VALIDATED.ordinal();
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Prestataire assigné")
                .date(validated ? request.getValidatedAt() : null)
                .completed(validated)
                .build());
        
        // STARTED - Intervention en cours
        boolean started = request.getStatus().ordinal() >= InterventionStatus.STARTED.ordinal();
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Intervention en cours")
                .date(request.getStartedAt())
                .completed(started)
                .build());
        
        // FINISHED - Incident résolu
        boolean finished = request.getStatus() == InterventionStatus.FINISHED;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Incident résolu")
                .date(request.getFinishedAt())
                .completed(finished)
                .build());
        
        return timeline;
    }

    private Double providerRating(Long providerId) {
        // Placeholder - à implémenter avec le providerRatingRepository
        return 0.0;
    }

    // =========================================================================
    // GESTION DES DEVIS — CÔTÉ COPROPRIÉTAIRE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerQuoteCardDTO> getQuotesByIntervention(Long interventionId, int page, int size) {

        // 1. Vérifier que l'intervention existe et appartient au copropriétaire
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // 2. Récupérer uniquement les devis envoyés (pas les brouillons)
        List<Quote> quotes = quoteRepository
                .findAllByInterventionRequestOrderByTotalAmountAsc(request)
                .stream()
                .filter(q -> q.getStatus() != QuoteStatus.DRAFT)
                .collect(Collectors.toList());

        if (quotes.isEmpty()) {
            return new PageImpl<>(List.of(), PageRequest.of(page, size), 0);
        }

        // 3. Prix max pour normaliser le score prix
        double maxAmount = quotes.stream()
                .mapToDouble(q -> q.getTotalAmount().doubleValue())
                .max()
                .orElse(1.0);

        // 4. Mapper chaque devis avec son score
        List<CoOwnerQuoteCardDTO> cards = quotes.stream()
                .map(quote -> {

                    Double noteMoyenne = providerRatingRepository
                            .calculerNoteMoyenne(quote.getProvider().getId());
                    double rating = noteMoyenne != null ? noteMoyenne : 0.0;

                    int reviewCount = (int) providerRatingRepository
                            .countByProviderId(quote.getProvider().getId());

                    // Score prix 40% + note 40% + délai 20%
                    double scorePrix = 1.0 - (quote.getTotalAmount().doubleValue() / maxAmount);
                    double scoreNote = rating / 5.0;
                    double scoreDelai = quote.getEstimatedDelay() != null
                            ? 1.0 - (quote.getEstimatedDelay().getDays() / 30.0)
                            : 0.0;
                    double scoreFinal = (scorePrix * 0.40) + (scoreNote * 0.40) + (scoreDelai * 0.20);
                    int scoreQualitePrix = (int) Math.round(scoreFinal * 100);

                    return CoOwnerQuoteCardDTO.builder()
                            .id(quote.getId())
                            .providerId(quote.getProvider().getId())
                            .providerName(quote.getProvider().getFirstName()
                                    + " " + quote.getProvider().getLastName())
                            .companyName(quote.getProvider().getCompanyName())
                            .providerPhotoUrl(quote.getProvider().getProfilePhotoUrl())
                            .providerCity(quote.getProvider().getInterventionZone())
                            .providerRating(rating)
                            .reviewCount(reviewCount)
                            .totalAmount(quote.getTotalAmount())
                            .estimatedDelayLabel(quote.getEstimatedDelay() != null
                                    ? quote.getEstimatedDelay().getLabel() : "N/A")
                            .isVerified(quote.getProvider().isVerified())
                            .isBestOffer(false) // défini après
                            .scoreQualitePrix(scoreQualitePrix)
                            .scoreFinal(scoreFinal) // temporaire pour tri
                            .status(quote.getStatus())
                            .build();
                })
                .collect(Collectors.toList());

        // 5. Badge RECOMMANDÉ → meilleur score
        cards.stream()
                .max(Comparator.comparingDouble(CoOwnerQuoteCardDTO::getScoreFinal))
                .ifPresent(best -> best.setBestOffer(true));

        // 6. Trier par score décroissant
        cards.sort(Comparator.comparingDouble(CoOwnerQuoteCardDTO::getScoreFinal).reversed());

        // 7. Appliquer la pagination
        int start = (int) PageRequest.of(page, size).getOffset();
        int end = Math.min(start + size, cards.size());
        List<CoOwnerQuoteCardDTO> pagedCards = cards.subList(start, end);

        return new PageImpl<>(pagedCards, PageRequest.of(page, size), cards.size());
    }

    @Override
    @Transactional(readOnly = true)
    public CoOwnerQuoteDetailDTO getQuoteDetail(Long interventionId, Long quoteId) {

        // 1. Vérifier l'intervention et l'accès
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // 2. Récupérer le devis
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis introuvable"));

        // 3. Vérifier que le devis appartient bien à cette intervention
        if (!quote.getInterventionRequest().getId().equals(interventionId)) {
            throw new BadRequestException("Ce devis n'appartient pas à cette intervention");
        }

        User provider = quote.getProvider();

        Double noteMoyenne = providerRatingRepository.calculerNoteMoyenne(provider.getId());
        double rating = noteMoyenne != null ? noteMoyenne : 0.0;

        int reviewCount = (int) providerRatingRepository.countByProviderId(provider.getId());

        Integer satisfactionRate = providerRatingRepository.calculerTauxSatisfaction(provider.getId());

        Double avgMinutes = providerRatingRepository.calculerTempsIntervention(provider.getId());
        String avgInterventionTime;
        if (avgMinutes == null) {
            avgInterventionTime = "N/A";
        } else if (avgMinutes < 60) {
            avgInterventionTime = Math.round(avgMinutes) + " min";
        } else {
            long hours = Math.round(avgMinutes) / 60;
            long minutes = Math.round(avgMinutes) % 60;
            avgInterventionTime = minutes > 0 ? hours + "h" + minutes : hours + "h";
        }

        // 4. Lignes matériaux
        List<QuoteLineDTO> materialLines = quote.getItems() != null
                ? quote.getItems().stream()
                        .filter(item -> item.getType() == com.example.solimus.enums.QuoteItemType.MATERIAL)
                        .map(line -> QuoteLineDTO.builder()
                                .description(line.getDescription())
                                .detail(line.getQuantity() + " × " + line.getUnitPrice())
                                .montant(line.getUnitPrice()
                                        .multiply(BigDecimal.valueOf(line.getQuantity())))
                                .build())
                        .collect(Collectors.toList())
                : List.of();

        // 5. Lignes main d'œuvre
        List<QuoteLineDTO> laborLines = quote.getItems() != null
                ? quote.getItems().stream()
                        .filter(item -> item.getType() == com.example.solimus.enums.QuoteItemType.LABOR)
                        .map(line -> QuoteLineDTO.builder()
                                .description(line.getDescription())
                                .detail(line.getQuantity() + " × " + line.getUnitPrice())
                                .montant(line.getUnitPrice()
                                        .multiply(BigDecimal.valueOf(line.getQuantity())))
                                .build())
                        .collect(Collectors.toList())
                : List.of();

        return CoOwnerQuoteDetailDTO.builder()
                .id(quote.getId())
                .providerId(provider.getId())
                .providerName(provider.getFirstName() + " " + provider.getLastName())
                .companyName(provider.getCompanyName())
                .providerPhotoUrl(provider.getProfilePhotoUrl())
                .providerCity(provider.getInterventionZone())
                .providerPhone(provider.getPhone())
                .providerEmail(provider.getEmail())
                .providerRating(rating)
                .reviewCount(reviewCount)
                .interventionCount(provider.getInterventionCount() != null ? provider.getInterventionCount() : 0)
                .satisfactionRate(satisfactionRate != null ? satisfactionRate : 0)
                .avgInterventionTime(avgInterventionTime)
                .isVerified(provider.isVerified())
                .isBestOffer(false) // recalculé si nécessaire
                .materialLines(materialLines)
                .laborLines(laborLines)
                .laborTotalAmount(quote.getLaborTotalAmount())
                .materialTotalAmount(quote.getMaterialTotalAmount())
                .totalAmount(quote.getTotalAmount())
                .estimatedDelayLabel(quote.getEstimatedDelay() != null
                        ? quote.getEstimatedDelay().getLabel() : "N/A")
                .additionalComments(quote.getAdditionalComments())
                .status(quote.getStatus())
                .createdAt(quote.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public int getQuotesCount(Long interventionId) {

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        return (int) quoteRepository
                .findAllByInterventionRequestOrderByTotalAmountAsc(request)
                .stream()
                .filter(q -> q.getStatus() != QuoteStatus.DRAFT)
                .count();
    }

    @Override
    @Transactional
    public void acceptQuote(Long interventionId, Long quoteId) {
        // 1. Récupérer la demande avec verrou
        InterventionRequest request = interventionRepository.findByIdForUpdate(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));
        
        // 2. Vérifier que c'est le propriétaire qui a créé cette demande
        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accepter un devis sur cette demande");
        }
        
        // 3. Vérifier que la demande est encore en attente de décision
        if (request.getStatus() != InterventionStatus.PENDING && request.getStatus() != InterventionStatus.QUOTE_SENT) {
            throw new BadRequestException("Cette demande ne peut plus accepter de devis");
        }
        
        // 4. Récupérer le devis à accepter
        Quote acceptedQuote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis introuvable"));
        
        if (acceptedQuote.getInterventionRequest() == null
                || !acceptedQuote.getInterventionRequest().getId().equals(request.getId())) {
            throw new BadRequestException("Ce devis n'appartient pas à cette demande d'intervention.");
        }
        
        if (acceptedQuote.getStatus() != QuoteStatus.SENT) {
            throw new BadRequestException("Seul un devis envoyé et en attente peut être accepté.");
        }
        
        // 5. Accepter ce devis
        acceptedQuote.setStatus(QuoteStatus.ACCEPTED);
        
        // 6. Refuser automatiquement tous les autres devis concurrents
        List<Quote> otherQuotes = quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request);
        otherQuotes.stream()
                .filter(q -> !q.getId().equals(quoteId))
                .filter(q -> q.getStatus() == QuoteStatus.SENT)
                .forEach(q -> q.setStatus(QuoteStatus.REJECTED));
        
        // 7. Finaliser la demande : assignation du prestataire, montant et changement de statut
        request.setSelectedProvider(acceptedQuote.getProvider());
        request.setTotalAmount(acceptedQuote.getTotalAmount());
        request.addStatusHistory(InterventionStatus.SYNDIC_VALIDATED, currentOwner);
        
        log.info("Devis {} accepté pour l'intervention {} par le propriétaire {}", quoteId, interventionId, currentOwner.getEmail());
    }

    @Override
    @Transactional
    public PaymentResponseDTO payerAcompte(Long interventionId, PayerAcompteDTO dto) {
        // Vérifier que l'intervention appartient au copropriétaire
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // Bloquer le paiement si géré par le syndic
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC
                || request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            throw new ForbiddenException("Cette intervention est gérée par le syndic. Le paiement doit être effectué par le syndic.");
        }

        // Placeholder - implémentation du paiement d'acompte
        return PaymentResponseDTO.builder()
                .success(true)
                .message("Acompte payé avec succès")
                .transactionReference("TX-" + System.currentTimeMillis())
                .build();
    }

    @Override
    @Transactional
    public PaymentResponseDTO validerEtPayerSolde(Long interventionId, ValiderTravauxDTO dto) {
        // Vérifier que l'intervention appartient au copropriétaire
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // Bloquer le paiement si géré par le syndic
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC
                || request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            throw new ForbiddenException("Cette intervention est gérée par le syndic. Le paiement doit être effectué par le syndic.");
        }

        // Placeholder - implémentation de la validation et paiement du solde
        return PaymentResponseDTO.builder()
                .success(true)
                .message("Solde payé avec succès")
                .transactionReference("TX-" + System.currentTimeMillis())
                .build();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}
