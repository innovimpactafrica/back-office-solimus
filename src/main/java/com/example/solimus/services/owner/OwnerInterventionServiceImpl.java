package com.example.solimus.services.owner;

import com.example.solimus.dtos.intervention.*;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Quote;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

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
    private final ReviewRepository reviewRepository;
    private final EmailService emailService;
    private final GeolocationService geolocationService;
    private final MinioService minioService;
    private final ProviderProfileRepository providerProfileRepository;


    // =========================================================================
    // MAPPING HELPERS
    // =========================================================================

    private NearbyProviderDTO mapToNearbyProviderDTO(User provider) {
        com.example.solimus.entities.ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
        return NearbyProviderDTO.builder()
                .id(provider.getId())
                .firstName(provider.getFirstName())
                .lastName(provider.getLastName())
                .companyName(profile != null ? profile.getCompanyName() : null)
                .specialtyName(profile != null && profile.getSpecialty() != null ? profile.getSpecialty().getName() : null)
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
                .typeName(property.getTypeBien() != null ? property.getTypeBien().getName() : null)
                .residenceId(property.getResidence() != null ? property.getResidence().getId() : null)
                .residenceName(property.getResidence() != null ? property.getResidence().getName() : null)
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }



    private Double providerRating(Long providerId) {
        // Placeholder - à implémenter avec le reviewRepository
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

                    Double noteMoyenne = reviewRepository
                            .calculerNoteMoyenne(quote.getProvider().getId());
                    double rating = noteMoyenne != null ? noteMoyenne : 0.0;

                    int reviewCount = (int) reviewRepository
                            .countByProviderId(quote.getProvider().getId());

                    // Score prix 40% + note 40% + délai 20%
                    double scorePrix = 1.0 - (quote.getTotalAmount().doubleValue() / maxAmount);
                    double scoreNote = rating / 5.0;
                    double scoreDelai = quote.getEstimatedDelay() != null
                            ? 1.0 - (quote.getEstimatedDelay().getDays() / 30.0)
                            : 0.0;
                    double scoreFinal = (scorePrix * 0.40) + (scoreNote * 0.40) + (scoreDelai * 0.20);
                    int scoreQualitePrix = (int) Math.round(scoreFinal * 100);

                    com.example.solimus.entities.ProviderProfile profile = providerProfileRepository.findByUser(quote.getProvider()).orElse(null);
                    return CoOwnerQuoteCardDTO.builder()
                            .id(quote.getId())
                            .providerId(quote.getProvider().getId())
                            .providerName(quote.getProvider().getFirstName()
                                    + " " + quote.getProvider().getLastName())
                            .companyName(profile != null ? profile.getCompanyName() : null)
                            .providerPhotoUrl(quote.getProvider().getProfilePhotoUrl())
                            .providerCity(profile != null ? profile.getInterventionZone() : null)
                            .providerRating(rating)
                            .reviewCount(reviewCount)
                            .totalAmount(quote.getTotalAmount())
                            .estimatedDelayLabel(quote.getEstimatedDelay() != null
                                    ? quote.getEstimatedDelay().getLabel() : "N/A")
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

  /**  @Override
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

        Double noteMoyenne = reviewRepository.calculerNoteMoyenne(provider.getId());
        double rating = noteMoyenne != null ? noteMoyenne : 0.0;

        int reviewCount = (int) reviewRepository.countByProviderId(provider.getId());

        Integer satisfactionRate = reviewRepository.calculerTauxSatisfaction(provider.getId());

        Double avgMinutes = reviewRepository.calculerTempsIntervention(provider.getId());
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

        com.example.solimus.entities.ProviderProfile profile = providerProfileRepository.findByUser(provider).orElse(null);
        return CoOwnerQuoteDetailDTO.builder()
                .id(quote.getId())
                .providerId(provider.getId())
                .providerName(provider.getFirstName() + " " + provider.getLastName())
                .companyName(profile != null ? profile.getCompanyName() : null)
                .providerPhotoUrl(provider.getProfilePhotoUrl())
                .providerCity(profile != null ? profile.getInterventionZone() : null)
                .providerPhone(provider.getPhone())
                .providerEmail(provider.getEmail())
                .providerRating(rating)
                .reviewCount(reviewCount)
                .interventionCount(profile != null && profile.getInterventionCount() != null ? profile.getInterventionCount() : 0)
                .satisfactionRate(satisfactionRate != null ? satisfactionRate : 0)
                .avgInterventionTime(avgInterventionTime)
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
**/
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
        request.setQuoteAcceptedAt(java.time.LocalDateTime.now());
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

    private List<String> toPresignedUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return new ArrayList<>();
        return urls.stream()
                .map(url -> minioService.getPresignedDownloadUrl(url, 3600))
                .collect(Collectors.toList());
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }
}

