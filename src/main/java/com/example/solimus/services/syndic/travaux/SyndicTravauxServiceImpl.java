package com.example.solimus.services.syndic.travaux;

import com.example.solimus.dtos.syndic.residence.CommonFacilityDTO;
import com.example.solimus.dtos.syndic.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.settings.SpecialtyDTO;
import com.example.solimus.dtos.syndic.travaux.CreateInterventionRequestDTO;
import com.example.solimus.dtos.syndic.travaux.CreateReviewDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicResidenceDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicDepositSummaryDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicPayDepositDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicBalancePaymentSummaryDTO;
import com.example.solimus.dtos.syndic.travaux.SyndicPaymentResultDTO;
import com.example.solimus.dtos.syndic.travaux.UpdateInterventionRequestDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ActivityType;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicTravauxServiceImpl implements SyndicTravauxService {

    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final CommonFacilityRepository commonFacilityRepository;
    private final InterventionRequestRepository interventionRepository;
    private final ProviderProfileRepository providerProfileRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final GeolocationService geolocationService;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ReviewRepository reviewRepository;
    private final ActivityLogRepository activityLogRepository;
    private final QuoteRepository quoteRepository;
    private final SyndicWalletRepository syndicWalletRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final ProviderWalletRepository providerWalletRepository;
    private final PaymentRepository paymentRepository;

    @Value("${solimus.geolocation.search-radius-km:30.0}")
    private double searchRadiusKm;//Rayon de recherche des prestataires
    @Value("${provider.gps.freshness-minutes:60}")
    private int gpsFreshnessMinutes;// Durée de validité d'une localisation

    // =========================================================================
    // LISTER LES RÉSIDENCES DU SYNDIC
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<SyndicResidenceDTO> getMesResidences(Integer page, Integer size) {
        // Récupérer le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupérer toutes les résidences qui appartiennent à ce syndic avec pagination
        Pageable pageable = PageRequest.of(page, size);
        Page<Residence> residencePage = residenceRepository.findAllBySyndicId(currentSyndic.getId(), pageable);

        List<SyndicResidenceDTO> dtos = residencePage.getContent().stream()
                // Pour chaque résidence, on retourne l'id, le nom et la photo
                .map(r -> {
                    String photoUrl = r.getPhotoUrl();
                    return SyndicResidenceDTO.builder()
                            .id(r.getId())
                            .name(r.getName())
                            .photoUrl(photoUrl)
                            .build();
                })
                .toList();

        return new PageImpl<>(dtos, pageable, residencePage.getTotalElements());
    }

    // =========================================================================
    // LISTER LES LOTS D'UNE RÉSIDENCE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<PropertyDTO> getPropertiesByResidence(Long residenceId, Integer page, Integer size) {
        // Récupérer le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // Récupérer tous les lots de cette résidence avec pagination
        Pageable pageable = PageRequest.of(page, size);
        Page<Property> propertyPage = propertyRepository.findByResidenceId(residenceId, pageable);

        List<PropertyDTO> dtos = propertyPage.getContent().stream()
                .map(this::mapToPropertyDTO)
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, propertyPage.getTotalElements());
    }

    // =========================================================================
    // LISTER LES BIENS COMMUNS D'UNE RÉSIDENCE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<CommonFacilityDTO> getCommonFacilitiesByResidence(Long residenceId, Integer page, Integer size) {
        // Récupérer le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // Récupérer tous les biens communs de cette résidence avec pagination
        Pageable pageable = PageRequest.of(page, size);
        Page<CommonFacility> facilityPage = commonFacilityRepository.findByResidenceId(residenceId, pageable);

        List<CommonFacilityDTO> dtos = facilityPage.getContent().stream()
                .map(cf -> CommonFacilityDTO.builder()
                        .id(cf.getId())
                        .label(cf.getFacilityType().getName())
                        .build())
                .toList();

        return new PageImpl<>(dtos, pageable, facilityPage.getTotalElements());
    }

    // =========================================================================
    // LISTER LES SPÉCIALITÉS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Page<SpecialtyDTO> getAllSpecialties(Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Specialty> specialtyPage = specialtyRepository.findAll(pageable);

        List<SpecialtyDTO> dtos = specialtyPage.getContent().stream()
                .map(specialty -> SpecialtyDTO.builder()
                        .id(specialty.getId())
                        .name(specialty.getName())
                        .description(specialty.getDescription())
                        .icon(specialty.getIcon())
                        .build())
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, specialtyPage.getTotalElements());
    }

    // =========================================================================
    // CRÉATION D'INTERVENTION
    // =========================================================================

    /**
     * Crée une demande d'intervention et notifie les prestataires.
     */
    @Override
    @Transactional
    public void createInterventionRequest(CreateInterventionRequestDTO dto) {
        User currentSyndic = getCurrentUser(); //Récupérer le syndic actuel

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à créer une intervention pour cette résidence");
        }

        // Récupérer la spécialité
        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        // Créer la demande d'intervention
        InterventionRequest request = new InterventionRequest();
        request.setReference(genererReference()); //Générer et assigner une référence unique (ex: TRV-001) à la demande
        request.setTitle(dto.getTitle()); // Définir le titre
        request.setDescription(dto.getDescription()); // Définir la description
        request.addStatusHistory(InterventionStatus.PENDING, currentSyndic); // Ajouter l'historique de statut
        request.setInitiatedBy(InitiatedBy.SYNDIC); // Initié par le syndic
        request.setSyndic(currentSyndic); // Définir le syndic
        request.setResidence(residence); // Définir la résidence
        request.setSpecialty(specialty); // Définir la spécialité
        request.setPhotoUrls(dto.getPhotoUrls()); // Définir les URLs des photos
        request.setUrgencyLevel(dto.getUrgencyLevel()); // Définir le niveau d'urgence

        // Le syndic peut créer des demandes pour les parties communes OU pour les appartements (au nom du propriétaire)
        if (dto.getPropertyId() != null && dto.getCommonFacilityId() != null) {
            throw new BadRequestException("Vous ne pouvez spécifier qu'un seul : un appartement OU un équipement commun");
        }
        if (dto.getPropertyId() == null && dto.getCommonFacilityId() == null) {
            throw new BadRequestException("Vous devez spécifier un appartement OU un équipement commun");
        }

        if (dto.getPropertyId() != null) {
            // Demande pour un appartement (partie privée)
            Property property = propertyRepository.findById(dto.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Appartement introuvable"));

            // Vérifier que l'appartement appartient à la résidence
            if (property.getResidence() == null || !property.getResidence().getId().equals(dto.getResidenceId())) {
                throw new BadRequestException("Cet appartement n'appartient pas à cette résidence");
            }

            request.setProperty(property);
            request.setCommonFacility(null);
            request.setLocationType(IncidentLocationType.APPARTEMENT);
            request.setManagementMode(InterventionManagementMode.SYNDIC);
            request.setOwner(property.getOwner());
        } else {
            // Demande pour un équipement commun (partie commune)
            CommonFacility commonFacility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));

            // Vérifier que l'équipement appartient à la résidence
            if (commonFacility.getResidence() == null || !commonFacility.getResidence().getId().equals(dto.getResidenceId())) {
                throw new BadRequestException("Cet équipement commun n'appartient pas à cette résidence");
            }

            request.setProperty(null);
            request.setCommonFacility(commonFacility);
            request.setLocationType(IncidentLocationType.PARTIE_COMMUNE);
            request.setManagementMode(InterventionManagementMode.SYNDIC);
        }

        // Sauvegarder la demande pour obtenir un ID valide avant le log d'activité
        interventionRepository.save(request);

        // Diffusion automatique aux prestataires proches
        notifyNearbyProviders(request, residence, specialty);

        // Enregistrer l'événement dans le journal d'activité de la résidence
        ActivityLog log = new ActivityLog();
        log.setResidence(residence);
        log.setType(ActivityType.INTERVENTION_REPORTED);
        log.setRelatedEntityType("INTERVENTION");
        log.setRelatedEntityId(request.getId());
        log.setActor(currentSyndic);
        log.setMessage("Nouveau signalement");
        log.setDetail(request.getTitle());
        activityLogRepository.save(log);
    }

    // =========================================================================
    // ENVOI AUX PRESTATAIRES
    // =========================================================================

    @Override
    @Transactional
    public void sendToProviders(Long interventionId) {
        User currentSyndic = getCurrentUser();

        // Récupérer la demande
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        // Vérifier que c'est une demande de partie commune gérée par le syndic
        if (request.getLocationType() != IncidentLocationType.PARTIE_COMMUNE
                || request.getManagementMode() != InterventionManagementMode.SYNDIC) {
            throw new BadRequestException("Cette demande n'est pas une demande de partie commune gérée par le syndic");
        }

        // Vérifier que la résidence appartient au syndic
        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à gérer cette demande");
        }

        // Vérifier que la demande n'a pas déjà été envoyée aux prestataires
        if (!request.getNotifiedProviders().isEmpty()) {
            throw new BadRequestException("Cette demande a déjà été envoyée aux prestataires");
        }

        // Vérifier que la demande est encore en statut PENDING
        if (request.getStatus() != InterventionStatus.PENDING) {
            throw new BadRequestException("Cette demande n'est plus en attente de prestataires");
        }

        // Diffuser la demande aux prestataires proches
        notifyNearbyProviders(request, request.getResidence(), request.getSpecialty());
    }

    // =========================================================================
    // CRÉATION D'AVIS
    // =========================================================================

    @Override
    @Transactional
    public void createReview(Long interventionId, CreateReviewDTO dto) {
        User currentSyndic = getCurrentUser();

        // 1. Récupérer l'intervention
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // 2. Vérifier que l'intervention appartient au syndic
        if (request.getSyndic() == null || !request.getSyndic().getId().equals(currentSyndic.getId())) {
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
        review.setReviewer(currentSyndic);
        review.setProvider(request.getSelectedProvider());
        review.setRating(dto.getRating());
        review.setComment(dto.getComment());

        reviewRepository.save(review);

        // 7. Mettre à jour le rating et reviewCount du prestataire
        updateProviderRating(request.getSelectedProvider());
    }

    // =========================================================================
    // VALIDER UN DEVIS
    // =========================================================================

    @Override
    @Transactional
    public void validateQuote(Long interventionId, Long quoteId) {

        // Récupère le syndic connecté
        User currentSyndic = getCurrentUser();

        // Récupère l'intervention, erreur si introuvable
        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // Vérifie que la résidence appartient bien au syndic connecté
        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à gérer cette intervention");
        }

        // Vérifie que c'est bien une intervention de partie commune gérée par le syndic
        if (request.getManagementMode() != InterventionManagementMode.SYNDIC) {
            throw new BadRequestException("Cette intervention n'est pas gérée par le syndic");
        }

        // Vérifie qu'aucun prestataire n'est déjà sélectionné
        if (request.getSelectedProvider() != null) {
            throw new BadRequestException("Un prestataire est déjà sélectionné pour cette intervention");
        }

        // Récupère le devis à valider
        Quote acceptedQuote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis introuvable"));

        // Vérifie que ce devis appartient bien à cette intervention
        if (!acceptedQuote.getInterventionRequest().getId().equals(interventionId)) {
            throw new BadRequestException("Ce devis n'appartient pas à cette intervention");
        }

        // Vérifie que le devis est bien en attente
        if (acceptedQuote.getStatus() != QuoteStatus.SENT) {
            throw new BadRequestException("Seul un devis envoyé et en attente peut être validé");
        }

        // Accepte ce devis
        acceptedQuote.setStatus(QuoteStatus.ACCEPTED);

        // Rejette automatiquement tous les autres devis concurrents en attente
        List<Quote> otherQuotes = quoteRepository.findAllByInterventionRequestOrderByTotalAmountAsc(request);
        otherQuotes.stream()
                .filter(q -> !q.getId().equals(quoteId))
                .filter(q -> q.getStatus() == QuoteStatus.SENT)
                .forEach(q -> q.setStatus(QuoteStatus.REJECTED));

        // Finalise l'intervention : assigne le prestataire, le montant, change le statut
        request.setSelectedProvider(acceptedQuote.getProvider());
        request.setQuoteAcceptedAt(LocalDateTime.now());
        request.setTotalAmount(acceptedQuote.getTotalAmount());
        request.addStatusHistory(InterventionStatus.QUOTE_VALIDATED, currentSyndic);

        interventionRepository.save(request);
    }

    // =========================================================================
    // RÉSUMÉ POUR LE MODAL ACOMPTE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicDepositSummaryDTO getDepositSummary(Long interventionId) {

        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette intervention");
        }

        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun devis n'a encore été validé pour cette intervention");
        }

        ProviderProfile providerProfile = providerProfileRepository.findByUser(request.getSelectedProvider())
                .orElse(null);

        // Récupère le solde disponible du wallet syndic
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        BigDecimal soldeDisponible = wallet != null
                ? syndicWalletTransactionRepository.sumTransactionsUpTo(wallet.getId(), LocalDateTime.now())
                : BigDecimal.ZERO;

        return SyndicDepositSummaryDTO.builder()
                .providerName(request.getSelectedProvider().getFirstName() + " " + request.getSelectedProvider().getLastName())
                .companyName(providerProfile != null ? providerProfile.getCompanyName() : null)
                .totalAmount(request.getTotalAmount())
                .emisLe(request.getQuoteAcceptedAt())
                .walletBalanceAvailable(soldeDisponible)
                .build();
    }

    // =========================================================================
    // PAYER L'ACOMPTE
    // =========================================================================

    @Override
    @Transactional
    public SyndicPaymentResultDTO payDeposit(Long interventionId, SyndicPayDepositDTO dto) {

        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à effectuer ce paiement");
        }

        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun prestataire n'est sélectionné pour cette intervention");
        }

        // Vérifie qu'aucun acompte n'a déjà été versé
        if (request.getDepositAmount() != null && request.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BadRequestException("Un acompte a déjà été versé pour cette intervention");
        }

        // Wallet SOLIMUS → débit interne synchrone + crédit direct du wallet prestataire
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseThrow(() -> new BadRequestException("Aucun wallet trouvé pour ce syndic"));

        // Vérifie que le solde est suffisant
        BigDecimal soldeDisponible = syndicWalletTransactionRepository.sumTransactionsUpTo(wallet.getId(), LocalDateTime.now());
        if (soldeDisponible.compareTo(dto.getMontant()) < 0) {
            throw new BadRequestException("Solde du wallet insuffisant pour verser cet acompte");
        }

        // Débite le wallet syndic (transaction négative, catégorie TRAVAUX)
        SyndicWalletTransaction transaction = new SyndicWalletTransaction();
        transaction.setWallet(wallet);
        transaction.setResidence(request.getResidence());
        transaction.setCategory(WalletTransactionCategory.TRAVAUX);
        transaction.setAmount(dto.getMontant().negate());
        transaction.setLabel("Acompte — " + request.getTitle());
        transaction.setTransactionDate(LocalDateTime.now());
        syndicWalletTransactionRepository.save(transaction);

        // Crédite le wallet du prestataire avec ce même montant
        ProviderWallet providerWallet = providerWalletRepository.findByProviderId(request.getSelectedProvider().getId())
                .orElseThrow(() -> new BadRequestException("Le prestataire n'a pas de wallet"));

        providerWallet.setAvailableBalance(providerWallet.getAvailableBalance().add(dto.getMontant()));
        providerWallet.setTotalThisMonth(providerWallet.getTotalThisMonth().add(dto.getMontant()));
        providerWalletRepository.save(providerWallet);

        // Met à jour l'intervention (depositAmount, remainingAmount recalculé automatiquement via @PrePersist/@PreUpdate)
        request.setDepositAmount(dto.getMontant());
        interventionRepository.save(request);

        // Tracer l'activité de paiement wallet
        ActivityLog activityLog = new ActivityLog();
        activityLog.setResidence(request.getResidence());
        activityLog.setType(ActivityType.PAYMENT_RECEIVED);
        activityLog.setRelatedEntityType("INTERVENTION_PAYMENT");
        activityLog.setRelatedEntityId(request.getId());
        activityLog.setActor(currentSyndic);
        activityLog.setMessage("Paiement intervention wallet");
        activityLog.setDetail("Acompte — " + request.getTitle() + " — " + dto.getMontant() + " FCFA");
        activityLogRepository.save(activityLog);

        BigDecimal nouveauSolde = soldeDisponible.subtract(dto.getMontant());

        return SyndicPaymentResultDTO.builder()
                .success(true)
                .message("Acompte versé avec succès")
                .montantPaye(dto.getMontant())
                .nouveauSoldeWallet(nouveauSolde)
                .build();
    }
    // =========================================================================
    // RÉSUMÉ POUR LE MODAL PAIEMENT FINAL
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public SyndicBalancePaymentSummaryDTO getBalanceSummary(Long interventionId) {

        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette intervention");
        }

        if (request.getStatus() != InterventionStatus.FINISHED) {
            throw new BadRequestException("Les travaux ne sont pas encore terminés");
        }

        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId()).orElse(null);
        BigDecimal soldeDisponible = wallet != null
                ? syndicWalletTransactionRepository.sumTransactionsUpTo(wallet.getId(), LocalDateTime.now())
                : BigDecimal.ZERO;

        return SyndicBalancePaymentSummaryDTO.builder()
                .montantDevis(request.getTotalAmount())
                .acompteVerse(request.getDepositAmount() != null ? request.getDepositAmount() : BigDecimal.ZERO)
                .soldeRestant(request.getRemainingAmount() != null ? request.getRemainingAmount() : BigDecimal.ZERO)
                .walletBalanceAvailable(soldeDisponible)
                .build();
    }

    // =========================================================================
    // PAYER LE SOLDE ET CLÔTURER
    // =========================================================================

    @Override
    @Transactional
    public SyndicPaymentResultDTO payBalanceAndClose(Long interventionId, SyndicPayDepositDTO dto) {

        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à effectuer ce paiement");
        }

        if (request.getStatus() != InterventionStatus.FINISHED) {
            throw new BadRequestException("Les travaux ne sont pas encore terminés");
        }

        BigDecimal solde = request.getRemainingAmount() != null ? request.getRemainingAmount() : BigDecimal.ZERO;

        if (solde.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Aucun solde restant à payer pour cette intervention");
        }

        // Récupère le wallet syndic
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseThrow(() -> new BadRequestException("Aucun wallet trouvé pour ce syndic"));

        // Vérifie que le solde du wallet est suffisant
        BigDecimal soldeDisponible = syndicWalletTransactionRepository.sumTransactionsUpTo(wallet.getId(), LocalDateTime.now());
        if (soldeDisponible.compareTo(solde) < 0) {
            throw new BadRequestException("Solde du wallet insuffisant pour payer le solde restant");
        }

        // Débite le wallet syndic
        SyndicWalletTransaction transaction = new SyndicWalletTransaction();
        transaction.setWallet(wallet);
        transaction.setResidence(request.getResidence());
        transaction.setCategory(WalletTransactionCategory.TRAVAUX);
        transaction.setAmount(solde.negate());
        transaction.setLabel("Solde final — " + request.getTitle());
        transaction.setTransactionDate(LocalDateTime.now());
        syndicWalletTransactionRepository.save(transaction);

        // ⬇️ AJOUT — Crédite le wallet du prestataire avec le solde final
        ProviderWallet providerWallet = providerWalletRepository.findByProviderId(request.getSelectedProvider().getId())
                .orElseThrow(() -> new BadRequestException("Le prestataire n'a pas de wallet"));

        providerWallet.setAvailableBalance(providerWallet.getAvailableBalance().add(solde));
        providerWallet.setTotalThisMonth(providerWallet.getTotalThisMonth().add(solde));
        providerWalletRepository.save(providerWallet);
        // ⬆️ FIN DE L'AJOUT

        // Met à jour le montant payé et clôture l'intervention
        request.setDepositAmount(request.getTotalAmount()); // tout est désormais payé
        request.addStatusHistory(InterventionStatus.FINAL_VALIDATION, currentSyndic);
        request.setValidatedAt(LocalDateTime.now());
        interventionRepository.save(request);

        // Tracer l'activité de paiement wallet
        ActivityLog activityLog = new ActivityLog();
        activityLog.setResidence(request.getResidence());
        activityLog.setType(ActivityType.PAYMENT_RECEIVED);
        activityLog.setRelatedEntityType("INTERVENTION_PAYMENT");
        activityLog.setRelatedEntityId(request.getId());
        activityLog.setActor(currentSyndic);
        activityLog.setMessage("Paiement intervention wallet");
        activityLog.setDetail("Solde — " + request.getTitle() + " — " + solde + " FCFA");
        activityLogRepository.save(activityLog);

        BigDecimal nouveauSolde = soldeDisponible.subtract(solde);

        // ⬇️ AJOUT — Trace la clôture dans le journal d'activité
        ActivityLog log = new ActivityLog();
        log.setResidence(request.getResidence());
        log.setType(ActivityType.INTERVENTION_RESOLVED);
        log.setRelatedEntityType("INTERVENTION");
        log.setRelatedEntityId(request.getId());
        log.setActor(currentSyndic);
        log.setMessage("Intervention clôturée après paiement — " + request.getTitle());
        activityLogRepository.save(log);

        return SyndicPaymentResultDTO.builder()
                .success(true)
                .message("Paiement effectué, intervention clôturée")
                .montantPaye(solde)
                .nouveauSoldeWallet(nouveauSolde)
                .build();
    }
    // =========================================================================
    // MISE À JOUR ET SUPPRESSION D'INTERVENTION
    // =========================================================================

    @Override
    @Transactional
    public void updateIntervention(Long interventionId, UpdateInterventionRequestDTO dto) {
        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // Vérifier que l'intervention appartient au syndic
        if (!request.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier cette intervention");
        }

        // Vérifier que l'intervention est en attente de devis
        if (request.getStatus() != InterventionStatus.PENDING) {
            throw new BadRequestException("Impossible de modifier une intervention qui n'est plus en attente de devis");
        }

        // Mise à jour partielle des champs
        if (dto.getTitle() != null) {
            request.setTitle(dto.getTitle());
        }
        if (dto.getDescription() != null) {
            request.setDescription(dto.getDescription());
        }
        if (dto.getResidenceId() != null) {
            Residence residence = residenceRepository.findById(dto.getResidenceId())
                    .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));
            request.setResidence(residence);
        }
        if (dto.getPropertyId() != null) {
            Property property = propertyRepository.findById(dto.getPropertyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));
            request.setProperty(property);
        }
        if (dto.getCommonFacilityId() != null) {
            CommonFacility facility = commonFacilityRepository.findById(dto.getCommonFacilityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Équipement commun introuvable"));
            request.setCommonFacility(facility);
        }
        if (dto.getSpecialtyId() != null) {
            Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                    .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));
            request.setSpecialty(specialty);
        }
        if (dto.getLocationType() != null) {
            request.setLocationType(dto.getLocationType());
        }
        if (dto.getManagementMode() != null) {
            request.setManagementMode(dto.getManagementMode());
        }
        if (dto.getUrgencyLevel() != null) {
            request.setUrgencyLevel(dto.getUrgencyLevel());
        }
        if (dto.getPhotoUrls() != null) {
            request.setPhotoUrls(dto.getPhotoUrls());
        }

        interventionRepository.save(request);
    }

    @Override
    @Transactional
    public void deleteIntervention(Long interventionId) {
        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // Vérifier que l'intervention appartient au syndic
        if (!request.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à supprimer cette intervention");
        }

        // Vérifier que l'intervention est en attente de devis
        if (request.getStatus() != InterventionStatus.PENDING) {
            throw new BadRequestException("Impossible de supprimer une intervention qui n'est plus en attente de devis");
        }

        interventionRepository.delete(request);
    }

    @Override
    @Transactional
    public void addPhotosToIntervention(Long interventionId, List<String> photoUrls) {
        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository.findById(interventionId)
                .orElseThrow(() -> new ResourceNotFoundException("Intervention introuvable"));

        // Vérifier que l'intervention appartient au syndic via la résidence
        if (!request.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier cette intervention");
        }

        // Vérifier que l'intervention est toujours en attente de devis
        if (request.getStatus() != InterventionStatus.PENDING) {
            throw new BadRequestException("Impossible d'ajouter des photos : l'intervention n'est plus en attente de devis");
        }

        // Ajouter les nouvelles photos aux existantes
        if (photoUrls != null && !photoUrls.isEmpty()) {
            if (request.getPhotoUrls() == null) {
                request.setPhotoUrls(new ArrayList<>());
            }
            request.getPhotoUrls().addAll(photoUrls);
            interventionRepository.save(request);
        }
    }

    /**
     * Diffuse une demande d'intervention aux prestataires proches.
     * Réutilisé pour les demandes créées par le syndic et celles créées par le owner.
     */
    private void notifyNearbyProviders(InterventionRequest request, Residence residence, Specialty specialty) {
        // Vérifier que la résidence a des coordonnées GPS
        if (residence.getLatitude() == null || residence.getLongitude() == null) {
            throw new BadRequestException("La résidence n'a pas de coordonnées GPS, impossible de trouver des prestataires proches");
        }

        // Récupérer les prestataires proches
        List<ProviderProfile> candidates = providerProfileRepository
                .findActiveProvidersBySpecialty(specialty.getId());

        // Filtrer les prestataires avec abonnement actif
        List<ProviderProfile> abonnesActifs = candidates.stream()
                .filter(profile -> subscriptionRepository
                        .findFirstByProviderIdOrderByEndDateDesc(profile.getUser().getId())
                        .map(Subscription::isCurrentlyActive)
                        .orElse(false))
                .toList();

        // Calculer la distance et garder ceux dans le rayon autorisé
        List<ProviderProfile> candidatsProches = new ArrayList<>();
        // Calculer le seuil de fraîcheur GPS : position valide si mise à jour il y a moins de X minutes
        LocalDateTime seuilFraicheur = LocalDateTime.now().minusMinutes(gpsFreshnessMinutes);

        for (ProviderProfile profile : abonnesActifs) {
            // Vérifier si le prestataire a une position GPS récente et valide
            boolean gpsValide = profile.getGpsLatitude() != null
                    && profile.getGpsLongitude() != null
                    && profile.getGpsUpdatedAt() != null
                    && profile.getGpsUpdatedAt().isAfter(seuilFraicheur);

            // Utiliser la position GPS récente si valide, sinon la position de référence saisie à l'inscription
            double providerLat = gpsValide
                    ? profile.getGpsLatitude().doubleValue()
                    : profile.getLatitude().doubleValue();

            double providerLon = gpsValide
                    ? profile.getGpsLongitude().doubleValue()
                    : profile.getLongitude().doubleValue();

            // Calculer la distance entre la résidence et le prestataire
            double distance = geolocationService.calculateDistance(
                    residence.getLatitude().doubleValue(),
                    residence.getLongitude().doubleValue(),
                    providerLat,
                    providerLon
            );

            // Garder uniquement les prestataires dans le rayon autorisé (30km par défaut)
            if (distance <= searchRadiusKm) {
                candidatsProches.add(profile);
            }
        }

        // Notifier chaque prestataire trouvé
        for (ProviderProfile profil : candidatsProches) {
            User user = profil.getUser();

            if (user.isNotificationsEnabled()) {
                notificationService.sendPush(
                        user.getId(),
                        "Nouvelle demande de travaux",
                        "Une nouvelle demande correspond à votre spécialité : " + request.getTitle()
                );

                emailService.sendInterventionNotification(
                        user.getEmail(),
                        user.getFirstName(),
                        request.getTitle(),
                        residence.getName()
                );
            }

            request.getNotifiedProviders().add(user);
        }
    }
    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    /**
     * Met à jour le rating et le reviewCount du prestataire après création d'un avis.
     */
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


    /**
     * Génère une référence unique de type TRV-001 etc
     * On compte le nombre total de demandes en base, on ajoute 1, puis on formate.
     */
    private String genererReference() {
        // On compte le nombre total de demandes déjà existantes en base
        long totalExistant = interventionRepository.count();
        // On ajoute 1 pour obtenir le numéro de la prochaine demande
        long prochainNumero = totalExistant + 1;
        // On formate en "TRV-" suivi d'au minimum 3 chiffres (ex: TRV-001, TRV-010, TRV-1000)
        return String.format("TRV-%03d", prochainNumero);
    }

    /**
     * Récupère l'utilisateur actuellement authentifié via le SecurityContext.
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    /**
     * Mappe une entité Property vers un PropertyDTO.
     */
    private PropertyDTO mapToPropertyDTO(Property property) {
        return PropertyDTO.builder()
                .id(property.getId())
                .reference(property.getReference())
                .superficie(property.getSuperficie())
                .typeName(property.getTypeBien() != null ? property.getTypeBien().getName() : null)
                .residenceId(property.getResidence().getId())
                .residenceName(property.getResidence().getName())
                .ownerId(property.getOwner() != null ? property.getOwner().getId() : null)
                .ownerName(property.getOwner() != null ? property.getOwner().getFirstName() + " " + property.getOwner().getLastName() : null)
                .build();
    }

}





