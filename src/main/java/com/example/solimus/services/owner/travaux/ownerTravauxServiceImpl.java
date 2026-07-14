package com.example.solimus.services.owner.travaux;

import com.example.solimus.dtos.intervention.CoOwnerQuoteCardDTO;
import com.example.solimus.dtos.syndic.travaux.PayDepositDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.owner.travaux.BalanceSummaryDTO;
import com.example.solimus.dtos.owner.travaux.CoOwnerQuoteDetailDTO;
import com.example.solimus.dtos.owner.travaux.CreateOwnerInterventionRequestDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDetailDTO;
import com.example.solimus.dtos.owner.travaux.OwnerTimelineStepDTO;
import com.example.solimus.dtos.owner.travaux.ProviderInfoDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionDTO;
import com.example.solimus.dtos.owner.travaux.OwnerInterventionSummaryDTO;
import com.example.solimus.dtos.provider.profile.QuoteLineDTO;
import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.residence.ResidenceDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
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
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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
    private final PaymentRepository paymentRepository;
    private final GeolocationService geolocationService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final MinioService minioService;
    private final ReviewRepository reviewRepository;
    private final SyndicCoOwnerRelationRepository coOwnerRelationRepository;

    @Value("${solimus.geolocation.search-radius-km:30.0}")
    private double searchRadiusKm; // Rayon de recherche des prestataires (30km par défaut)

    @Value("${provider.gps.freshness-minutes:60}")
    private int gpsFreshnessMinutes; // Durée de validité d'une localisation GPS (60 minutes par défaut)

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

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

        // 7. Mettre à jour le rating et reviewCount du prestataire
        updateProviderRating(request.getSelectedProvider());
    }

    // =========================================================================
    // GESTION DES DEVIS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<CoOwnerQuoteCardDTO> getQuotesByIntervention(Long interventionId, int page, int size) {

        // 1. Vérifier que l'intervention existe et appartient au copropriétaire
        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        User currentOwner = getCurrentUser();
        // Vérifier via owner direct, propriété, ou copropriétaire dans la résidence
        boolean isOwner = false;
        if (request.getOwner() != null && request.getOwner().getId().equals(currentOwner.getId())) {
            isOwner = true;
        } else if (request.getProperty() != null && request.getProperty().getOwner() != null
                && request.getProperty().getOwner().getId().equals(currentOwner.getId())) {
            isOwner = true;
        } else if (request.getResidence() != null) {
            // Vérifier si le copropriétaire a une propriété dans cette résidence
            isOwner = coOwnerRelationRepository.existsByResidenceIdAndCoOwnerId(
                    request.getResidence().getId(), currentOwner.getId());
        }

        // Si managementMode == SYNDIC, le syndic peut voir les devis via la résidence
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC) {
            if (request.getResidence() != null && request.getResidence().getSyndic() != null
                    && request.getResidence().getSyndic().getId().equals(currentOwner.getId())) {
                isOwner = true;
            }
        }

        if (!isOwner) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // 2. Récupérer uniquement les devis envoyés (pas les brouillons)
        List<Quote> quotesSend = quoteRepository
                .findByInterventionRequestAndStatus(request, QuoteStatus.SENT);

        if (quotesSend.isEmpty()) {
            return Page.empty(PageRequest.of(page, size));
        }

        // 3. Prix max pour normaliser le score prix
        // Objectif : récupérer le prix le plus élevé parmi tous les devis.
        // Ce montant servira plus tard à calculer un score relatif (ex: plus le devis est cher, plus le score est bas).
        double maxAmount = quotesSend.stream()                               // ① On parcourt la liste des devis
                .mapToDouble(q -> q.getTotalAmount().doubleValue())      // ② Pour chaque devis, on prend son total TTC en double (ex: 150000.0)
                .max()                                                   // ③ On cherche le maximum de tous ces montants → retourne un OptionalDouble
                .orElse(1.0);                                            // ④ Si la liste est vide (pas de devis), on met 1.0 par défaut pour éviter une division par zéro plus tard.


        // 4. Mapper chaque devis en carte avec son score calculé
        List<CoOwnerQuoteCardDTO> cards = quotesSend.stream()
                .map(quote -> mapToQuoteCardDTO(quote, maxAmount))
                .toList();

        // 5. Badge RECOMMANDÉ → celui avec le meilleur score final (on effectue une comparaison)
        cards.stream()
                .max(Comparator.comparingDouble(CoOwnerQuoteCardDTO::getScoreFinal))
                .ifPresent(cardDTO -> cardDTO.setBestOffer(true));

        // 6. Trier par score décroissant → le meilleur en premier
        cards.sort(Comparator.comparingDouble(CoOwnerQuoteCardDTO::getScoreFinal).reversed());

        // 7. Retourner la page avec les données triées
        Pageable pageable = PageRequest.of(page, size);
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), cards.size());
        List<CoOwnerQuoteCardDTO> pagedCards = cards.subList(start, end);

        // pagedCards -> liste des données à afficher dans cette page
        // pageable -> infos pagination (numéro page, taille page, tri)
        // cards.size() -> nombre total d'éléments (pour calculer nombre total de pages)
        return new PageImpl<>(pagedCards, pageable, cards.size());
    }

    @Override
    @Transactional(readOnly = true)
    public CoOwnerQuoteDetailDTO getQuoteDetail(Long interventionId, Long quoteId) {

        // 1. Vérifier que l'intervention appartient au owner connecté
        User currentOwner = getCurrentUser();
        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // 1.Bloquer si l'intervention est gérée par le syndic (partie commune)
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC) {
            throw new ForbiddenException("Les devis de cette intervention sont gérés par le syndic.");
        }

        // 2. Récupérer le devis et vérifier qu'il appartient bien à cette intervention
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis introuvable"));

        if (!quote.getInterventionRequest().getId().equals(interventionId)) {
            throw new BadRequestException("Ce devis n'appartient pas à cette intervention");
        }

        // 3. Récupérer le profil prestataire
        ProviderProfile profil = providerProfileRepository
                .findByUserId(quote.getProvider().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        //on récupére Id du user (prestataire)
        Long providerId = quote.getProvider().getId();

        // 4. Calculer la satisfaction directement en base
        // Exemple : 55 avis >= 4(notes) sur 56 total → 98%
        long totalAvis = reviewRepository.countByProviderId(providerId);
        long avisPositifs = reviewRepository.countByProviderIdAndRatingGreaterThanEqual(providerId, 4);
        int satisfaction = totalAvis > 0 //s'il a au moins un avis
                ? (int) Math.round((avisPositifs * 100.0) / totalAvis)
                : 0;

        // 5. Calculer le temps moyen en minutes
        // Exemple : interventions de 30min, 90min, 60min → moyenne = 60 min
        List<InterventionRequest> interventionsTerminees = interventionRequestRepository
                .findBySelectedProviderIdAndStatus(providerId, InterventionStatus.FINISHED);

        double averageTimeMinutes = interventionsTerminees.stream()
                // Ignorer les interventions sans dates de début ou de fin
                .filter(i -> i.getStartedAt() != null && i.getFinishedAt() != null)
                // Convertir chaque durée en minutes (ex: 2h30 → 150)
                .mapToLong(i -> Duration.between(i.getStartedAt(), i.getFinishedAt()).toMinutes())
                // Calculer la moyenne (ex: [120, 180, 90 /3] → 130)
                .average()
                // Si aucune donnée, retourner 0.0 (évite une erreur)
                .orElse(0.0);

        // 6. Séparer les items par type (MATERIAL vs LABOR)
        List<QuoteLineDTO> materiaux = new ArrayList<>();
        List<QuoteLineDTO> mainOeuvre = new ArrayList<>();
        BigDecimal sousTotalMateriaux = BigDecimal.ZERO;
        BigDecimal sousTotalMainOeuvre = BigDecimal.ZERO;

        for (QuoteItem item : quote.getItems()) {
            // getTotalPrice() est déjà calculé dans l'entité QuoteItem
            BigDecimal subtotal = item.getTotalPrice();

            QuoteLineDTO lineDTO = QuoteLineDTO.builder()
                    .description(item.getDescription())
                    .quantity(item.getQuantity())
                    .unitPrice(item.getUnitPrice())
                    .subtotal(subtotal)
                    .build();

            if (item.getType() == QuoteItemType.MATERIAL) {
                materiaux.add(lineDTO);
                sousTotalMateriaux = sousTotalMateriaux.add(subtotal);
            } else {
                mainOeuvre.add(lineDTO);
                sousTotalMainOeuvre = sousTotalMainOeuvre.add(subtotal);
            }
        }

        // 7. Construire et retourner le DTO
        return CoOwnerQuoteDetailDTO.builder()
                .quoteStatus(quote.getStatus())
                .totalAmount(quote.getTotalAmount())
                .createdAt(quote.getCreatedAt())
                .providerName(quote.getProvider().getFirstName() + " " + quote.getProvider().getLastName())
                .companyName(profil.getCompanyName())
                .providerPhotoUrl(quote.getProvider().getProfilePhotoUrl())
                .providerPhone(quote.getProvider().getPhone())
                .providerEmail(quote.getProvider().getEmail())
                .providerCity(profil.getInterventionZone())
                .rating(profil.getRating())
                .reviewCount(profil.getReviewCount() != null ? profil.getReviewCount().intValue() : 0)
                .interventionCount(profil.getInterventionCount())
                .satisfaction(satisfaction)
                .averageTimeHours(averageTimeMinutes / 60.0)
                .materiaux(materiaux)
                .sousTotalMateriaux(sousTotalMateriaux)
                .mainOeuvre(mainOeuvre)
                .sousTotalMainOeuvre(sousTotalMainOeuvre)
                .totalTTC(quote.getTotalAmount())
                .build();
    }

    @Override
    @Transactional
    public void acceptQuote(Long interventionId, Long quoteId) {

        // 1. Récupérer la demande
        InterventionRequest request = interventionRequestRepository.findByIdForUpdate(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // 2. Vérifier que c'est le propriétaire qui a créé cette demande
        User currentOwner = getCurrentUser();
        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accepter un devis sur cette demande");
        }

        // 2.Bloquer si l'intervention est gérée par le syndic (partie commune)
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC) {
            throw new ForbiddenException("Les devis de cette intervention sont gérés par le syndic.");
        }

        // 3. Vérifier qu'aucun prestataire n'est déjà sélectionné
        if (request.getSelectedProvider() != null) {
            throw new BadRequestException("Cette intervention a déjà un prestataire assigné");
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
        request.setQuoteAcceptedAt(LocalDateTime.now());
        request.setTotalAmount(acceptedQuote.getTotalAmount());
        request.addStatusHistory(InterventionStatus.QUOTE_VALIDATED, currentOwner);
    }

    @Override
    @Transactional
    public PaymentResponseDTO payDeposit(Long interventionId, PayDepositDTO dto) {
        User currentOwner = getCurrentUser();

        // 1. Vérifier que l'intervention existe et appartient au copropriétaire
        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // 2. Bloquer le paiement si géré par le syndic
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC
                || request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            throw new ForbiddenException("Cette intervention est gérée par le syndic. Le paiement doit être effectué par le syndic.");
        }

        // 3. Vérifier si un acompte existe déjà pour cette intervention
        Optional<PaymentProvider> existingPayment = paymentRepository
                .findByInterventionRequestIdAndType(interventionId, PaymentType.ACOMPTE);

        if (existingPayment.isPresent()) {
            PaymentProvider payment = existingPayment.get();
            // Si le paiement est déjà COMPLETED, on bloque
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                throw new BadRequestException("Un acompte a déjà été versé pour cette intervention");
            }
            // Si le paiement est PENDING, on bloque (déjà en cours)
            if (payment.getStatus() == PaymentStatus.PENDING) {
                throw new BadRequestException("Un paiement est déjà en cours pour cette intervention");
            }
            // Si le paiement est FAILED, on permet de réinitier (réinitialisation plus bas)
        }

        // 4. Vérifier qu'un prestataire a été sélectionné
        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun prestataire n'est sélectionné pour cette demande.");
        }

        // 5. Générer une référence unique de transaction
        String transactionRef = genererReference("PAY");

        // 6. Créer ou réinitialiser le paiement
        PaymentProvider payment;
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.FAILED) {
            // Paiement FAILED → réinitialiser pour une nouvelle tentative
            payment = existingPayment.get();
            payment.setReference(transactionRef);
            payment.setMethod(dto.getMethode());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setPaidAt(null); //le paiement n'est pas encore complété
        } else {
            // Nouveau paiement
            payment = PaymentProvider.builder()
                    .reference(transactionRef)
                    .interventionRequest(request)
                    .provider(request.getSelectedProvider())
                    .paymentInitiator(currentOwner)
                    .amount(dto.getMontant())
                    .type(PaymentType.ACOMPTE)
                    .method(dto.getMethode())
                    .status(PaymentStatus.PENDING)
                    .build();
        }

        paymentRepository.save(payment);

        // 7. Construire l'URL de redirection TouchPay
        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        return PaymentResponseDTO.builder()
                .success(true)
                .message("Paiement initié. Veuillez compléter via TouchPay.")
                .transactionReference(transactionRef)
                .amountToPay(dto.getMontant())
                .paymentUrl(bridgeUrl)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public BalanceSummaryDTO getBalanceSummary(Long interventionId) {
        User currentOwner = getCurrentUser();

        // 1. Vérifier que l'intervention existe et appartient au copropriétaire
        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // 2. Bloquer si l'intervention est gérée par le syndic (partie commune)
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC
                || request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            throw new ForbiddenException("Cette intervention est gérée par le syndic. Le paiement doit être effectué par le syndic.");
        }

        // 3. Retourner le récapitulatif financier
        return BalanceSummaryDTO.builder()
                .interventionId(request.getId())
                .montantDevis(request.getTotalAmount() != null ? request.getTotalAmount() : BigDecimal.ZERO)
                .acompteVerse(request.getDepositAmount() != null ? request.getDepositAmount() : BigDecimal.ZERO)
                .soldeRestant(request.getRemainingAmount() != null ? request.getRemainingAmount() : BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional
    public PaymentResponseDTO validateAndPayBalance(Long interventionId, ValiderTravauxDTO dto) {
        User currentOwner = getCurrentUser();

        // 1. Vérifier que l'intervention existe et appartient au copropriétaire
        InterventionRequest request = interventionRequestRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé à cette intervention");
        }

        // 2. Bloquer le paiement si géré par le syndic
        if (request.getManagementMode() == InterventionManagementMode.SYNDIC
                || request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE) {
            throw new ForbiddenException("Cette intervention est gérée par le syndic. Le paiement doit être effectué par le syndic.");
        }

        // 3. Vérifier que les travaux sont terminés
        if (request.getStatus() != InterventionStatus.FINISHED) {
            throw new BadRequestException("Les travaux ne sont pas encore terminés");
        }

        // 4. Récupérer le solde restant à payer
        // remainingAmount = totalAmount - depositAmount (calculé auto par @PreUpdate).
        // - Si un acompte a été versé : solde = montant restant (ex: 75 000 - 35 000 = 40 000)
        // - Si AUCUN acompte n'a été versé : depositAmount = 0, donc solde = montant total du devis / ex: (remainingAmount = 75 000 (totalAmount)  - 0 (depositAmount) = 75000 le total)
        BigDecimal solde = request.getRemainingAmount() != null
                ? request.getRemainingAmount()
                : BigDecimal.ZERO;

        // 5. Vérifier si un solde existe déjà pour cette intervention
        Optional<PaymentProvider> existingPayment = paymentRepository
                .findByInterventionRequestIdAndType(interventionId, PaymentType.SOLDE);

        if (existingPayment.isPresent()) {
            PaymentProvider payment = existingPayment.get();
            // Si le paiement est déjà COMPLETED, on bloque
            if (payment.getStatus() == PaymentStatus.COMPLETED) {
                throw new BadRequestException("Le solde a déjà été versé pour cette intervention");
            }
            // Si le paiement est PENDING, on bloque (déjà en cours)
            if (payment.getStatus() == PaymentStatus.PENDING) {
                throw new BadRequestException("Un paiement est déjà en cours pour cette intervention");
            }
            // Si le paiement est FAILED, on permet de réinitier (réinitialisation plus bas)
        }

        // 6. Générer une référence unique de transaction
        String transactionRef = genererReference("SOL");

        // 7. Créer ou réinitialiser le paiement
        PaymentProvider payment;
        if (existingPayment.isPresent() && existingPayment.get().getStatus() == PaymentStatus.FAILED) {
            // Paiement FAILED → réinitialiser pour une nouvelle tentative
            payment = existingPayment.get();
            payment.setReference(transactionRef);
            payment.setMethod(dto.getMethode());
            payment.setStatus(PaymentStatus.PENDING);
            payment.setPaidAt(null);
        } else {
            // Nouveau paiement
            payment = PaymentProvider.builder()
                    .reference(transactionRef)
                    .interventionRequest(request)
                    .provider(request.getSelectedProvider())
                    .paymentInitiator(currentOwner)
                    .amount(solde)
                    .type(PaymentType.SOLDE)
                    .method(dto.getMethode())
                    .status(PaymentStatus.PENDING)
                    .build();
        }

        paymentRepository.save(payment);

        // 7. Construire l'URL de redirection TouchPay
        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        return PaymentResponseDTO.builder()
                .success(true)
                .message("Paiement du solde initié. Veuillez compléter via TouchPay.")
                .transactionReference(transactionRef)
                .amountToPay(solde)
                .paymentUrl(bridgeUrl)
                .build();
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

    //Génération Référence pour le paiement
    private String genererReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }


    // Met à jour le rating et le reviewCount du prestataire après création d'un avis.
    private void updateProviderRating(User provider) {
        ProviderProfile profile = providerProfileRepository.findByUser(provider)
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // Calculer la nouvelle moyenne
        Double averageRating = reviewRepository.calculateAverageRating(provider.getId());
        double newRating = averageRating != null ? averageRating : 0.0;

        // Compter le nombre d'avis
        long count = reviewRepository.countByProviderId(provider.getId());

        // Mettre à jour le profil
        profile.setRating(newRating);
        profile.setReviewCount(count);

        providerProfileRepository.save(profile);
    }

    // =========================================================================
    // MAPPER — Quote → CoOwnerQuoteCardDTO

    // =========================================================================
    private CoOwnerQuoteCardDTO mapToQuoteCardDTO(Quote quote, double maxAmount) {

        // Récupérer le profil prestataire pour avoir rating et interventionZone
        ProviderProfile profil = providerProfileRepository
                .findByUser(quote.getProvider())
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // Utiliser le rating et reviewCount stockés sur le profil
        double ratingValue = profil.getRating() != null ? profil.getRating() : 0.0;
        long reviewCount = profil.getReviewCount() != null ? profil.getReviewCount() : 0;

        // Vérifier si le prestataire a un abonnement actif
        boolean isVerified = subscriptionRepository
                .findFirstByProviderIdOrderByEndDateDesc(quote.getProvider().getId())
                .map(Subscription::isCurrentlyActive)
                .orElse(false);

        // --- Calcul du score qualité/prix ---

        // Score prix (40%) : plus le devis (prix) est bas par rapport au max, meilleur est le score
        // Exemple : 75 000 / 120 000 = 0.625 → scorePrix = 1 - 0.625 = 0.375
        double scorePrix = 1.0 - (quote.getTotalAmount().doubleValue() / maxAmount);

        // Score note (40%) : 4.8 étoiles / 5 = 0.96 (moyenne des étoiles du prestatire /nombre total d'étoiles)
        double scoreNote = ratingValue / 5.0;

        // Score délai (20%) : délai de 2 jours (nombre délai)/ 30 (delai max 30 jours) = 0.067 → scoreDelai = 1 - 0.067 = 0.933
        // Plus le délai est court, meilleur est le score
        double scoreDelai = quote.getEstimatedDelay() != null
                ? 1.0 - (quote.getEstimatedDelay().getDays() / 30.0)
                : 0.0;

        // Score final pondéré
        double scoreFinal = (scorePrix * 0.40) + (scoreNote * 0.40) + (scoreDelai * 0.20);

        // Score affiché en % (ex: 0.74 → 74%)
        int scoreQualitePrix = (int) Math.round(scoreFinal * 100);

        return CoOwnerQuoteCardDTO.builder()
                .id(quote.getId())
                .providerId(quote.getProvider().getId())
                .providerName(quote.getProvider().getFirstName() + " " + quote.getProvider().getLastName())
                .companyName(profil.getCompanyName())
                .providerPhotoUrl(quote.getProvider().getProfilePhotoUrl())
                .providerCity(profil.getInterventionZone())
                .providerRating(ratingValue)
                .reviewCount(reviewCount)
                .totalAmount(quote.getTotalAmount())
                .estimatedDelayLabel(quote.getEstimatedDelay() != null ? quote.getEstimatedDelay().getLabel() : null)
                .scoreFinal(scoreFinal)
                .scoreQualitePrix(scoreQualitePrix)
                .isVerified(isVerified)
                .isBestOffer(false) // sera mis à true après dans le stream
                .build();

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

        // Convertir les chemins photos en URLs publiques directes
        List<String> photoUrls = request.getPhotoUrls() != null
                ? new ArrayList<>(request.getPhotoUrls())
                : new ArrayList<>();

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

        // Étape 2 : Envoyé au syndic (complété si c'est une partie commune gérée par le syndic)
        boolean isSyndicManaged = request.getLocationType() == IncidentLocationType.PARTIE_COMMUNE
                && request.getManagementMode() == InterventionManagementMode.SYNDIC;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Envoyé au syndic")
                .date(isSyndicManaged ? request.getCreatedAt() : null)
                .completed(isSyndicManaged)
                .build());

        // Étape 3 : Devis reçu (complété si au moins un devis existe)
        Optional<Quote> firstQuote = quoteRepository.findFirstByInterventionRequestOrderByCreatedAtAsc(request);
        boolean hasQuote = firstQuote.isPresent();
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Devis reçu")
                .date(hasQuote ? firstQuote.get().getCreatedAt() : null)
                .completed(hasQuote)
                .build());

        // Étape 4 : Validation devis (complété si un prestataire est sélectionné)
        boolean hasSelectedProvider = request.getSelectedProvider() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Validation devis")
                .date(hasSelectedProvider ? request.getQuoteAcceptedAt() : null)
                .completed(hasSelectedProvider)
                .build());

        // Étape 5 : Intervention démarrée (complété si startedAt est renseigné)
        boolean started = request.getStartedAt() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Intervention démarrée")
                .date(request.getStartedAt())
                .completed(started)
                .build());

        // Étape 6 : Travail terminé (complété si finishedAt est renseigné)
        boolean finished = request.getFinishedAt() != null;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Travail terminé")
                .date(request.getFinishedAt())
                .completed(finished)
                .build());

        // Étape 7 : Validation finale (complété si statut est FINAL_VALIDATION)
        boolean completed = request.getStatus() == InterventionStatus. FINAL_VALIDATION;
        timeline.add(OwnerTimelineStepDTO.builder()
                .label("Validation finale")
                .date(completed ? request.getFinishedAt() : null)
                .completed(completed)
                .build());

        return timeline;
    }


}
