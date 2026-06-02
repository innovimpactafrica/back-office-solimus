package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.intervention.*;
import com.example.solimus.dtos.property.PropertyDTO;
import com.example.solimus.dtos.residence.ResidenceDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final QuoteRepository quoteRepository;
    private final ProviderRatingRepository providerRatingRepository;
    private final EmailService emailService;
    private final GeolocationService geolocationService;

    @Override
    @Transactional(readOnly = true)
    public List<ResidenceDTO> getMyResidences() {
        User currentOwner = getCurrentUser();

        return propertyRepository.findAllByOwnerId(currentOwner.getId()).stream()
                .map(Property::getResidence)
                .filter(residence -> residence != null)
                .collect(Collectors.toMap(
                        Residence::getId,
                        residence -> residence,
                        (existing, duplicate) -> existing
                ))
                .values()
                .stream()
                .sorted(Comparator.comparing(Residence::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::mapToResidenceDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PropertyDTO> getMyPropertiesByResidence(Long residenceId) {
        User currentOwner = getCurrentUser();

        return propertyRepository.findAllByOwnerId(currentOwner.getId()).stream()
                .filter(property -> property.getResidence() != null && property.getResidence().getId().equals(residenceId))
                .map(this::mapToPropertyDTO)
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
            // PARTIE_COMMUNE : pas de bien, gestion forcée par syndic
            if (dto.getPropertyId() != null) {
                throw new BadRequestException("Pour un incident de type PARTIE_COMMUNE, aucun bien ne doit être spécifié");
            }
            request.setProperty(null);
            request.setManagementMode(InterventionManagementMode.SYNDIC);
            request.setSyndic(residence.getSyndic());
        } else if (dto.getLocationType() == APPARTEMENT) {
            // APPARTEMENT : bien obligatoire
            if (dto.getPropertyId() == null) {
                throw new BadRequestException("Pour un incident de type APPARTEMENT, un bien doit être spécifié");
            }

            // Vérifier que le bien appartient au copropriétaire et à la résidence
            Property property = ownerPropertiesInResidence.stream()
                    .filter(p -> p.getId().equals(dto.getPropertyId()))
                    .findFirst()
                    .orElseThrow(() -> new ForbiddenException("Ce bien ne vous appartient pas ou n'est pas dans cette résidence"));

            request.setProperty(property);

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
                emailService.sendInterventionNotification(
                        request.getSyndic().getEmail(),
                        request.getSyndic().getFirstName(),
                        request.getTitle(),
                        residence.getName()
                );
                log.info("Syndic notifié pour l'intervention {} créée par le copropriétaire {}", request.getTitle(), currentOwner.getEmail());
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
                        emailService.sendInterventionNotification(
                                provider.getEmail(),
                                provider.getFirstName(),
                                request.getTitle(),
                                residence.getName()
                        );
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
    public List<OwnerInterventionSummaryDTO> getMyInterventions() {
        User currentOwner = getCurrentUser();
        
        List<InterventionRequest> interventions = interventionRepository.findAllByOwner(currentOwner);
        
        return interventions.stream()
                .map(this::mapToSummaryDTO)
                .collect(Collectors.toList());
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
                .floorCount(residence.getFloorCount())
                .apartmentCount(residence.getApartmentCount())
                .syndicId(residence.getSyndic() != null ? residence.getSyndic().getId() : null)
                .syndicName(residence.getSyndic() != null ? residence.getSyndic().getFirstName() + " " + residence.getSyndic().getLastName() : null)
                .createdAt(residence.getCreatedAt())
                .build();
    }

    private PropertyDTO mapToPropertyDTO(Property property) {
        return PropertyDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .floor(property.getFloor())
                .area(property.getArea())
                .type(property.getType())
                .residenceId(property.getResidence() != null ? property.getResidence().getId() : null)
                .residenceName(property.getResidence() != null ? property.getResidence().getName() : null)
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }

    private OwnerInterventionSummaryDTO mapToSummaryDTO(InterventionRequest request) {
        return OwnerInterventionSummaryDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .residenceName(request.getResidence().getName())
                .propertyReference(request.getProperty().getReference())
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .status(request.getStatus())
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

        return OwnerInterventionDetailDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .residenceName(request.getResidence().getName())
                .propertyReference(request.getProperty().getReference())
                .status(request.getStatus())
                .specialtyName(request.getSpecialty() != null ? request.getSpecialty().getName() : null)
                .photoUrls(request.getPhotoUrls())
                .selectedProvider(selectedProviderInfo)
                .timeline(buildTimeline(request))
                .createdAt(request.getCreatedAt())
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

    @Override
    @Transactional(readOnly = true)
    public List<SyndicQuoteDTO> getQuotesByIntervention(Long interventionId) {
        // Vérifier que l'intervention appartient au copropriétaire
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));
        
        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }
        
        List<Quote> quotes = quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request);
        return quotes.stream()
                .map(this::mapToSyndicQuoteDTO)
                .collect(Collectors.toList());
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

    private SyndicQuoteDTO mapToSyndicQuoteDTO(Quote quote) {
        Double noteMoyenne = providerRatingRepository.calculerNoteMoyenne(quote.getProvider().getId());
        double rating = noteMoyenne != null ? noteMoyenne : 0.0;

        return SyndicQuoteDTO.builder()
                .id(quote.getId())
                .providerId(quote.getProvider().getId())
                .providerName(quote.getProvider().getFirstName() + " " + quote.getProvider().getLastName())
                .companyName(quote.getProvider().getCompanyName())
                .laborTotalAmount(quote.getLaborTotalAmount())
                .materialTotalAmount(quote.getMaterialTotalAmount())
                .totalAmount(quote.getTotalAmount())
                .estimatedDelayLabel(quote.getEstimatedDelay() != null ? quote.getEstimatedDelay().getLabel() : "N/A")
                .additionalComments(quote.getAdditionalComments())
                .status(quote.getStatus())
                .providerRating(rating)
                .build();
    }
}
