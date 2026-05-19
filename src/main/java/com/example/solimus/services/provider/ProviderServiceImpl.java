package com.example.solimus.services.provider;

import com.example.solimus.dtos.intervention.*;
import com.example.solimus.dtos.provider.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.QuoteItemType;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.enums.SubscriptionPlan;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.SubscriptionRepository;
import com.example.solimus.repositories.EstimatedDelayRepository;
import com.example.solimus.repositories.InterventionCommentRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.QuoteRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.repositories.WalletRepository;
import com.example.solimus.repositories.WithdrawalRequestRepository;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.enums.TransactionType;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderServiceImpl implements ProviderService {

    private final InterventionRequestRepository interventionRepository;
    private final UserRepository userRepository;
    private final QuoteRepository quoteRepository;
    private final EstimatedDelayRepository estimatedDelayRepository;
    private final InterventionCommentRepository commentRepository;
    private final MinioService minioService;
    private final WalletRepository walletRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final PaymentRepository paymentRepository;
    private final SubscriptionRepository subscriptionRepository;

    @Value("${solimus.subscription.free.max-quotes-per-month:3}")
    private int maxQuotesPerMonth;

    /**
     * Listing paginé et filtré pour le prestataire.
     * Utilise le summary DTO (plus léger).
     */
    @Override
    public Page<InterventionRequestSummaryDTO> getAvailableRequests(String search, InterventionStatus status,
            Pageable pageable) {
        User currentProvider = getCurrentUser();

        return interventionRepository.findFilteredRequests(currentProvider.getId(), search, status, pageable)
                .map(this::mapToSummaryDTO);
    }

    /**
     * Retourne le nombre total de demandes pour lesquelles le prestataire a été notifié.
     */
    @Override
    public long getTotalRequestsCount() {
        User currentProvider = getCurrentUser();
        return interventionRepository.countRequestsByProvider(currentProvider.getId());
    }

    /**
     * Détails complets d'une demande pour l'affichage de la fiche.
     */
    @Override
    public InterventionRequestDTO getRequestDetails(Long id) {
        User currentProvider = getCurrentUser();
        
        // 1. Récupérer la demande si le prestataire a été notifié
        InterventionRequest request = interventionRepository.findByIdAndNotifiedProvidersContaining(id, currentProvider)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demande introuvable ou vous n'êtes pas autorisé à la consulter"));

        // 2. Sécurité : Si un autre prestataire a été sélectionné pour cette intervention,
        // l'accès est bloqué car le devis du prestataire connecté a été refusé
        if (request.getSelectedProvider() != null && !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Votre devis a été refusé pour cette intervention. Vous ne pouvez plus consulter ses détails.");
        }

        return mapToDTO(request);
    }

    @Override
    public List<InterventionRequestDTO> getMyInterventions() {
        User currentProvider = getCurrentUser();
        return interventionRepository.findAllBySelectedProvider(currentProvider).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createQuote(CreateQuoteDTO dto) {
        User provider = getCurrentUser();

        // --- VERIFICATION ABONNEMENT & QUOTA DE DEVIS ---
        // On récupère l'abonnement rattaché au prestataire connecté
        Subscription subscription = subscriptionRepository
                .findByProviderId(provider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable pour ce prestataire."));

        // Si le prestataire a un abonnement GRATUIT, sa soumission est limitée à 3 devis par mois
        if (subscription.getPlan() == SubscriptionPlan.GRATUIT) {
            // Déterminer le début du mois en cours
            LocalDate debutMois = LocalDate.now().withDayOfMonth(1);
            
            // Compter le nombre de devis créés par ce prestataire depuis le début du mois
            int devisCompteur = quoteRepository.countByProviderIdAndCreatedAtAfter(
                    provider.getId(),
                    debutMois.atStartOfDay() // Début du mois à 00:00:00 pour filtrer les données dès le 1er jour complet
            );

            // Si la limite configurée de devis est atteinte, on refuse la soumission
            if (devisCompteur >= maxQuotesPerMonth) {
                throw new BadRequestException(
                        "Vous avez atteint la limite de " + maxQuotesPerMonth + " devis/mois. " +
                        "Passez en Premium pour soumettre des devis illimités."
                );
            }
        }

        // 1. Vérification du rôle
        if (provider.getRole() == null || !provider.getRole().getName().equals(ERole.ROLE_PRESTATAIRE)) {
            throw new BadRequestException("Seul un prestataire peut créer un devis.");
        }

        // 2. Récupération sécurisée : on ne trouve la demande QUE si le prestataire y
        // est rattaché
        InterventionRequest request = interventionRepository
                .findByIdAndNotifiedProvidersContaining(dto.getInterventionRequestId(), provider)
                .orElseThrow(() -> new BadRequestException(
                        "Demande introuvable ou vous n'êtes pas autorisé à répondre à cette demande."));

        // 3. Vérifier que la demande accepte encore des devis (Statut PENDING ou QUOTE_SENT)
        if (request.getStatus() != InterventionStatus.PENDING && request.getStatus() != InterventionStatus.QUOTE_SENT) {
            throw new BadRequestException("Cette demande n'accepte plus de nouveaux devis.");
        }

        // 4. Vérifier que ce prestataire n'a pas déjà soumis un devis
        if (quoteRepository.existsByInterventionRequestAndProvider(request, provider)) {
            throw new BadRequestException("Vous avez déjà soumis un devis pour cette demande d'intervention.");
        }

        // 5. Récupération de l'option de délai choisie dans le dropdown
        EstimatedDelay delay = estimatedDelayRepository.findById(dto.getEstimatedDelayId())
                .orElseThrow(() -> new ResourceNotFoundException("Délai estimé introuvable"));

        // 6. Initialisation de l'entité Quote
        Quote quote = new Quote();
        quote.setReference("DEV-" + (int)(Math.random() * 900000 + 100000)); // Exemple: DEV-584729
        quote.setProvider(provider);
        quote.setInterventionRequest(request);
        quote.setEstimatedDelay(delay);
        quote.setAdditionalComments(dto.getAdditionalComments());

        // 7. Gestion du statut (Brouillon vs Envoi Officiel)
        if (dto.isDraft()) {
            quote.setStatus(QuoteStatus.DRAFT);
        } else {
            // Si c'est un envoi réel, on change aussi le statut de la demande
            quote.setStatus(QuoteStatus.SENT);
            request.addStatusHistory(InterventionStatus.QUOTE_SENT, provider);
            interventionRepository.save(request);
        }

        // 8. Traitement des lignes du devis (Main d'œuvre et Matériel)
        if (dto.getItems() != null) {
            List<QuoteItem> items = dto.getItems().stream().map(itemDto -> {
                QuoteItem item = new QuoteItem();
                item.setDescription(itemDto.getDescription());
                item.setQuantity(itemDto.getQuantity());
                item.setUnitPrice(itemDto.getUnitPrice());
                item.setType(itemDto.getType()); // Catégorisation pour le calcul automatique des totaux
                item.setQuote(quote);
                return item;
            }).collect(Collectors.toList());
            quote.setItems(items);
        }

        // 9. Sauvegarde finale (Hibernate calculera les totaux via @PrePersist)
        quoteRepository.save(quote);
    }

    /**
     * Permet au prestataire de signaler qu'il a commencé les travaux.
     * Le statut passe de SYNDIC_VALIDATED à STARTED.
     */
    @Override
    @Transactional
    public void startIntervention(Long requestId) {
        User currentProvider = getCurrentUser();

        // Étape 1 : Récupération de la demande
        InterventionRequest request = interventionRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'intervention introuvable"));

        // Étape 2 : Sécurité - Vérifier que c'est bien CE prestataire qui a remporté le devis
        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à démarrer cette intervention. Seul le prestataire sélectionné par le syndic peut le faire.");
        }

        // Étape 3 : Workflow - On ne peut démarrer que si le devis a été validé par le syndic
        if (request.getStatus() != InterventionStatus.SYNDIC_VALIDATED) {
            throw new BadRequestException("Action impossible : Les travaux ne peuvent être démarrés que si le statut est 'Validation syndic'. Statut actuel : " + request.getStatus());
        }

        // Étape 4 : Mise à jour du statut, enregistrement dans l'historique et date de démarrage
        request.addStatusHistory(InterventionStatus.STARTED, currentProvider);
        request.setStartedAt(LocalDateTime.now());
        
        // Étape 5 : Sauvegarde en base de données
        interventionRepository.save(request);
    }

    /**
     * Permet au prestataire d'ajouter une photo (via MinIO) pendant que les travaux sont en cours.
     * Utile pour tracer l'avancement ou documenter l'intervention.
     * 
     * @param requestId L'identifiant de la demande d'intervention
     * @param photo Le fichier image à uploader
     */
    @Override
    @Transactional
    public void ajouterPhotoTravaux(Long requestId, MultipartFile photo) {
        User currentProvider = getCurrentUser();
        InterventionRequest request = interventionRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier cette intervention.");
        }

        if (request.getStatus() != InterventionStatus.STARTED) {
            throw new BadRequestException("Vous ne pouvez ajouter des photos que lorsque les travaux sont en cours.");
        }

        try {
            String photoUrl = minioService.uploadFile(photo, "interventions");
            if (photoUrl != null) {
                request.getWorkPhotoUrls().add(photoUrl);
                interventionRepository.save(request);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'upload de la photo des travaux", e);
            throw new RuntimeException("Erreur lors de l'upload de la photo");
        }
    }

    /**
     * Permet au prestataire d'ajouter un commentaire ou une note sur l'intervention en cours.
     * Ces notes seront visibles dans le détail de la demande.
     * 
     * @param requestId L'identifiant de la demande d'intervention
     * @param commentaire Le contenu texte de la note/commentaire
     */
    @Override
    @Transactional
    public void ajouterCommentaire(Long requestId, String commentaire) {
        User currentProvider = getCurrentUser();
        InterventionRequest request = interventionRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à commenter cette intervention.");
        }

        if (request.getStatus() != InterventionStatus.STARTED) {
            throw new BadRequestException("Vous ne pouvez commenter que lorsque les travaux sont en cours.");
        }

        InterventionComment comment = new InterventionComment();
        comment.setContent(commentaire);
        comment.setAuthor(currentProvider);
        comment.setInterventionRequest(request);

        commentRepository.save(comment);
    }

    /**
     * Permet au prestataire de signaler qu'il a terminé l'intervention.
     * Le statut passe de STARTED à FINISHED et la date de fin est enregistrée.
     * Un événement est également ajouté dans l'historique.
     * 
     * @param requestId L'identifiant de la demande d'intervention terminée
     */
    @Override
    @Transactional
    public void terminerIntervention(Long requestId) {
        User currentProvider = getCurrentUser();
        InterventionRequest request = interventionRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        if (request.getSelectedProvider() == null || !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à terminer cette intervention.");
        }

        if (request.getStatus() != InterventionStatus.STARTED) {
            throw new BadRequestException("L'intervention n'est pas en cours.");
        }

        request.addStatusHistory(InterventionStatus.FINISHED, currentProvider);
        request.setFinishedAt(LocalDateTime.now());
        interventionRepository.save(request);
    }

    // =========================================================================
    // PROFIL PRESTATAIRE
    // =========================================================================
    @Override
    public ProviderProfileDTO getMyProfile() {
        User currentProvider = getCurrentUser();

        return ProviderProfileDTO.builder()
                .companyName(currentProvider.getCompanyName() != null ? currentProvider.getCompanyName() : currentProvider.getFirstName() + " " + currentProvider.getLastName())
                .specialtyName(currentProvider.getSpecialty() != null ? currentProvider.getSpecialty().getName() : "N/A")
                .available(currentProvider.isAvailable())
                .email(currentProvider.getEmail())
                .phone(currentProvider.getPhone())
                .language("Français, Wolof") // Statique comme demandé
                .memberSince(currentProvider.getCreatedAt())
                .profilePhotoUrl(currentProvider.getProfilePhotoUrl())
                .build();
    }

    /**
     * Permet au prestataire de changer son statut (Disponible / Indisponible).
     */
    @Override
    @Transactional
    public void toggleAvailability() {
        User currentProvider = getCurrentUser();
        // On inverse la valeur actuelle (si true devient false, et inversement)
        currentProvider.setAvailable(!currentProvider.isAvailable());
        userRepository.save(currentProvider);
    }

    @Override
    public UpdateProviderProfileDTO getPersonalInformation() {
        User currentProvider = getCurrentUser();
        
        return UpdateProviderProfileDTO.builder()
                .companyName(currentProvider.getCompanyName())
                .firstName(currentProvider.getFirstName())
                .lastName(currentProvider.getLastName())
                .phone(currentProvider.getPhone())
                .email(currentProvider.getEmail())
                .specialtyName(currentProvider.getSpecialty() != null ? currentProvider.getSpecialty().getName() : "N/A")
                .interventionZone(currentProvider.getInterventionZone())
                .latitude(currentProvider.getLatitude())
                .longitude(currentProvider.getLongitude())
                .profilePhotoUrl(currentProvider.getProfilePhotoUrl())
                .build();
    }

    @Override
    @Transactional
    public void updateProfile(UpdateProviderProfileDTO dto, MultipartFile photo) {
        User currentProvider = getCurrentUser();

        // Mettre à jour uniquement les champs modifiables
        if (dto.getCompanyName() != null) currentProvider.setCompanyName(dto.getCompanyName());
        if (dto.getFirstName() != null) currentProvider.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) currentProvider.setLastName(dto.getLastName());
        if (dto.getPhone() != null) currentProvider.setPhone(dto.getPhone());
        if (dto.getEmail() != null) currentProvider.setEmail(dto.getEmail());
        if (dto.getInterventionZone() != null) currentProvider.setInterventionZone(dto.getInterventionZone());
        if (dto.getLatitude() != null) currentProvider.setLatitude(dto.getLatitude());
        if (dto.getLongitude() != null) currentProvider.setLongitude(dto.getLongitude());
        
        // Gestion de la photo de profil (upload vers Minio)
        if (photo != null && !photo.isEmpty()) {
            try {
                String photoUrl = minioService.uploadFile(photo, "profiles");
                if (photoUrl != null) {
                    currentProvider.setProfilePhotoUrl(photoUrl);
                }
            } catch (Exception e) {
                log.error("Erreur lors de l'upload de la photo de profil", e);
                throw new RuntimeException("Erreur lors de l'upload de la photo de profil");
            }
        }

        userRepository.save(currentProvider);
    }

    // =========================================================================
    // MES DEVIS
    // =========================================================================
    @Override
    public ProviderQuoteListDTO getMesDevis(QuoteStatus statut, String search, int page, int size) {
        User currentProvider = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        Page<Quote> quotePage = quoteRepository.findMyQuotes(currentProvider.getId(), statut, search, pageable);

        List<QuoteSummaryDTO> devisList = quotePage.getContent().stream().map(quote ->
            QuoteSummaryDTO.builder()
                .reference(quote.getReference())
                .titre(quote.getInterventionRequest().getTitle())
                .residenceName(quote.getInterventionRequest().getResidence().getName())
                .date(quote.getCreatedAt().toLocalDate())
                .montant(quote.getTotalAmount())
                .statut(quote.getStatus())
                .build()
        ).collect(Collectors.toList());

        Page<QuoteSummaryDTO> devisPage = new PageImpl<>(devisList, pageable, quotePage.getTotalElements());

        BigDecimal totalValide = quoteRepository.sumTotalAmountByProviderAndStatus(currentProvider.getId(), QuoteStatus.ACCEPTED);
        if (totalValide == null) {
            totalValide = BigDecimal.ZERO;
        }

        return ProviderQuoteListDTO.builder()
                .totalMontantValide(totalValide)
                .devis(devisPage)
                .build();
    }

    @Override
    public QuoteDetailDTO getQuoteDetails(Long quoteId) {
        User currentProvider = getCurrentUser();

        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis introuvable"));

        // Vérifier que le devis appartient bien au prestataire
        if (!quote.getProvider().getId().equals(currentProvider.getId())) {
            throw new BadRequestException("Vous n'êtes pas autorisé à voir ce devis.");
        }

        // Séparer les lignes de matériels et de main d'oeuvre
        List<QuoteLineDTO> materiaux = quote.getItems().stream()
                .filter(item -> item.getType() == QuoteItemType.MATERIAL)
                .map(item -> QuoteLineDTO.builder()
                        .description(item.getDescription())
                        .detail(item.getQuantity() + " × " + item.getUnitPrice() + " FCFA")
                        .montant(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        List<QuoteLineDTO> mainOeuvre = quote.getItems().stream()
                .filter(item -> item.getType() == QuoteItemType.LABOR)
                .map(item -> QuoteLineDTO.builder()
                        .description(item.getDescription())
                        .detail(item.getQuantity() + " × " + item.getUnitPrice() + " FCFA")
                        .montant(item.getTotalPrice())
                        .build())
                .collect(Collectors.toList());

        // Récupérer les informations du syndic (client)
        User syndic = quote.getInterventionRequest().getSyndic();
        String address = quote.getInterventionRequest().getResidence() != null ? quote.getInterventionRequest().getResidence().getFullAddress() : "N/A";

        // Déduire la date de validation à partir de l'historique de statut SYNDIC_VALIDATED de la demande
        LocalDate dateValidation = null;
        if (quote.getStatus() == QuoteStatus.ACCEPTED && quote.getInterventionRequest().getHistory() != null) {
            dateValidation = quote.getInterventionRequest().getHistory().stream()
                    .filter(h -> h.getStatus() == InterventionStatus.SYNDIC_VALIDATED)
                    .map(h -> h.getCreatedAt().toLocalDate())
                    .findFirst()
                    .orElse(null);
        }

        return QuoteDetailDTO.builder()
                .reference(quote.getReference())
                .titre(quote.getInterventionRequest().getTitle())
                .statut(quote.getStatus())
                .montantTotal(quote.getTotalAmount())
                .dateEnvoi(quote.getCreatedAt() != null ? quote.getCreatedAt().toLocalDate() : null)
                .dateValidation(dateValidation)
                .clientNom(syndic.getFirstName() + " " + syndic.getLastName())
                .clientTelephone(syndic.getPhone())
                .clientEmail(syndic.getEmail())
                .clientAdresse(address)
                .materiaux(materiaux)
                .sousTotalMateriaux(quote.getMaterialTotalAmount())
                .mainOeuvre(mainOeuvre)
                .sousTotalMainOeuvre(quote.getLaborTotalAmount())
                .totalTTC(quote.getTotalAmount())
                .notes(quote.getAdditionalComments())
                .build();
    }
    // =========================================================================
    // PORTEFEUILLE (WALLET)
    // =========================================================================

    /**
     * Récupère le Wallet du prestataire actuellement connecté,
     * calculant son solde disponible, ses montants en attente,
     * et l'historique complet de ses transactions (paiements + retraits).
     */
    @Override
    @Transactional
    public WalletDTO getMonWallet() {
        User currentProvider = getCurrentUser();

        // Récupérer le wallet en base ou en créer un si non existant (sécurité)
        Wallet wallet = walletRepository.findByProviderId(currentProvider.getId())
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .provider(currentProvider)
                            .availableBalance(BigDecimal.ZERO)
                            .pendingBalance(BigDecimal.ZERO)
                            .totalThisMonth(BigDecimal.ZERO)
                            .build();
                    return walletRepository.save(newWallet);
                });

        // Récupérer l'historique des transactions fusionnées (paiements + retraits)
        List<WalletTransactionDTO> transactions = getTransactions(currentProvider.getId());

        return WalletDTO.builder()
                .soldeDisponible(wallet.getAvailableBalance())
                .soldeEnAttente(wallet.getPendingBalance())
                .totalCeMois(wallet.getTotalThisMonth())
                .transactions(transactions)
                .build();
    }

    // =========================================================================
    // DEMANDE DE VERSEMENT
    // =========================================================================
    @Override
    @Transactional
    public WithdrawalRequestDTO demanderVersement(DemanderVersementDTO dto) {
        User currentProvider = getCurrentUser();

        // Récupérer le wallet du prestataire
        Wallet wallet = walletRepository.findByProviderId(currentProvider.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet introuvable"));

        // 1. Vérifier que le solde est suffisant
        if (dto.getMontant().compareTo(wallet.getAvailableBalance()) > 0) {
            throw new BadRequestException(
                    "Solde insuffisant. Disponible : " + wallet.getAvailableBalance() + " FCFA");
        }

        // 2. Créer la demande de versement (retrait)
        WithdrawalRequest retrait = WithdrawalRequest.builder()
                .reference(genererReference("WIT"))                     // Référence unique (ex: WIT-987654)
                .provider(currentProvider)                              // Prestataire effectuant la demande
                .amount(dto.getMontant())                               // Montant du retrait
                .method(dto.getMethode())                               // Moyen de retrait (WAVE, ORANGE_MONEY)
                .phoneNumber(dto.getNumeroDeTelephone())                // Numéro de téléphone destinataire
                .status(WithdrawalStatus.PENDING)                        // Nouveau retrait toujours PENDING
                .build();

        withdrawalRequestRepository.save(retrait);

        // 3. Déduire immédiatement du solde disponible du Wallet
        wallet.setAvailableBalance(wallet.getAvailableBalance().subtract(dto.getMontant()));
        walletRepository.save(wallet);

        // 4. TODO: Notifier le prestataire
        // notificationService.notifierDemandeVersementRecu(currentProvider, retrait);

        return mapToWithdrawalDTO(retrait);
    }
    // ================================================
    // CRÉDITER LE WALLET — appelé quand un paiement
    // est validé par le syndic
    // ================================================
    @Override
    @Transactional
    public void crediterWallet(Long providerId, BigDecimal montant) {
        // Recherche du wallet du prestataire ou création automatique s'il n'existe pas
        Wallet wallet = walletRepository.findByProviderId(providerId)
                .orElseGet(() -> creerWallet(providerId));

        // Créditer le solde disponible
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(montant));

        // Mettre à jour le total reçu ce mois
        wallet.setTotalThisMonth(wallet.getTotalThisMonth().add(montant));

        walletRepository.save(wallet);
    }


    // --- Mappers ---

    private InterventionRequestSummaryDTO mapToSummaryDTO(InterventionRequest request) {
        User currentProvider = getCurrentUser();
        
        // Déterminer le statut contextuel du point de vue de ce prestataire
        String finalStatus = request.getStatus() != null ? request.getStatus().name() : "PENDING";
        
        // Si un autre prestataire a été sélectionné pour cette intervention, 
        // le statut affiché pour le prestataire connecté devient "REFUSED"
        if (request.getSelectedProvider() != null && !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            finalStatus = "REFUSED";
        }

        return InterventionRequestSummaryDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : "N/A")
                .status(finalStatus)
                .createdAt(request.getCreatedAt())
                .build();
    }

    /**
     * Mappe une entité InterventionRequest vers un DTO détaillé (InterventionRequestDTO).
     * Inclut les informations de contact du résident et l'historique complet des statuts.
     *
     * @param request L'entité InterventionRequest à transformer.
     * @return Le DTO complet prêt à être renvoyé au frontend.
     */
    private InterventionRequestDTO mapToDTO(InterventionRequest request) {
        // Initialisation des contacts avec une valeur par défaut "N/A"
        String residentPhone = "N/A";
        String residentEmail = "N/A";

        // Étape 1 : Récupération des informations du résident (propriétaire du bien)
        // Utile pour que le prestataire puisse contacter la personne sur place
        if (request.getProperty() != null && request.getProperty().getOwner() != null) {
            User owner = request.getProperty().getOwner();
            residentPhone = owner.getPhone();
            residentEmail = owner.getEmail();
        }

        // Étape 2 : Construction de l'historique des statuts
        // On transforme la liste des entités d'historique en une liste de DTO (InterventionStatusHistoryDTO)
        // Cela permet au frontend d'afficher la timeline exacte (qui a fait quoi et quand)
        List<InterventionStatusHistoryDTO> historyDTOs = request.getHistory() != null
                ? request.getHistory().stream().map(h -> InterventionStatusHistoryDTO.builder()
                .id(h.getId())
                .status(h.getStatus())
                .createdAt(h.getCreatedAt())
                .build()).collect(Collectors.toList())
                : new ArrayList<>();

        // Étape 3 : Construction finale du DTO global de la demande d'intervention
        List<InterventionCommentDTO> commentsDTOs = request.getComments() != null
                ? request.getComments().stream().map(c -> InterventionCommentDTO.builder()
                .id(c.getId())
                .content(c.getContent())
                .authorId(c.getAuthor().getId())
                .authorName(c.getAuthor().getFirstName() + " " + c.getAuthor().getLastName())
                .createdAt(c.getCreatedAt())
                .build()).collect(Collectors.toList())
                : new ArrayList<>();

        return InterventionRequestDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                // Si la résidence est nulle (sécurité), on renvoie "N/A"
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : "N/A")
                .residentPhone(residentPhone)
                .residentEmail(residentEmail)
                .photoUrls(request.getPhotoUrls())
                .workPhotoUrls(request.getWorkPhotoUrls())
                .comments(commentsDTOs)
                // Ajout de l'historique (la timeline) dans la réponse
                .history(historyDTOs)
                .workflowSteps(buildWorkflow(request))
                .createdAt(request.getCreatedAt())
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .build();
    }

    private List<WorkflowStepDTO> buildWorkflow(InterventionRequest request) {
        InterventionStatus statut = request.getStatus();

        // Helper pour retrouver la date d'une étape spécifique depuis l'historique
        Function<InterventionStatus, LocalDateTime> findDate = (s) ->
                request.getHistory() != null ? request.getHistory().stream()
                        .filter(h -> h.getStatus() == s)
                        .map(h -> h.getCreatedAt())
                        .findFirst()
                        .orElse(null) : null;

        return List.of(
                WorkflowStepDTO.builder()
                        .label("Demande reçue")
                        .completed(true) // toujours true car la demande existe
                        .date(request.getCreatedAt())
                        .build(),

                WorkflowStepDTO.builder()
                        .label("Devis envoyé")
                        .completed(statut != InterventionStatus.PENDING)
                        .date(findDate.apply(InterventionStatus.QUOTE_SENT))
                        .build(),

                WorkflowStepDTO.builder()
                        .label("Validation syndic")
                        .completed(statut == InterventionStatus.SYNDIC_VALIDATED
                                || statut == InterventionStatus.STARTED
                                || statut == InterventionStatus.FINISHED
                                || statut == InterventionStatus.FINAL_VALIDATION)
                        .date(findDate.apply(InterventionStatus.SYNDIC_VALIDATED))
                        .build(),

                WorkflowStepDTO.builder()
                        .label("Intervention démarrée")
                        .completed(statut == InterventionStatus.STARTED
                                || statut == InterventionStatus.FINISHED
                                || statut == InterventionStatus.FINAL_VALIDATION)
                        .date(findDate.apply(InterventionStatus.STARTED))
                        .build(),

                WorkflowStepDTO.builder()
                        .label("Travail terminé")
                        .completed(statut == InterventionStatus.FINISHED
                                || statut == InterventionStatus.FINAL_VALIDATION)
                        .date(findDate.apply(InterventionStatus.FINISHED))
                        .build(),

                WorkflowStepDTO.builder()
                        .label("Validation finale")
                        .completed(statut == InterventionStatus.FINAL_VALIDATION)
                        .date(findDate.apply(InterventionStatus.FINAL_VALIDATION))
                        .build()
        );
    }

    /**
     * Fusionne les paiements reçus (crédits) et les demandes de retrait (débits),
     * les mappe vers WalletTransactionDTO et les trie par date décroissante.
     */
    private List<WalletTransactionDTO> getTransactions(Long providerId) {
        // 1. Récupérer tous les paiements reçus (acomptes + soldes)
        List<Payment> paiements = paymentRepository.findAllByProviderIdOrderByCreatedAtDesc(providerId);

        // 2. Récupérer tous les retraits
        List<WithdrawalRequest> retraits = withdrawalRequestRepository.findAllByProviderIdOrderByCreatedAtDesc(providerId);

        List<WalletTransactionDTO> transactions = new ArrayList<>();

        // 3. Ajouter les paiements (crédit +)
        if (paiements != null) {
            paiements.forEach(p -> {
                String residenceName = p.getInterventionRequest().getResidence() != null
                        ? p.getInterventionRequest().getResidence().getName()
                        : "Parties communes";
                String specialtyName = p.getInterventionRequest().getSpecialty() != null
                        ? p.getInterventionRequest().getSpecialty().getName()
                        : "Intervention";

                transactions.add(WalletTransactionDTO.builder()
                        .label(residenceName + " - " + specialtyName)
                        .montant(p.getAmount()) // Montant positif (crédit)
                        .type(TransactionType.ENTREE)
                        .statut(p.getStatus() == PaymentStatus.COMPLETED ? "Reçu" : "En attente")
                        .date(p.getCreatedAt().toLocalDate())
                        .build());
            });
        }

        // 4. Ajouter les retraits (débit -)
        if (retraits != null) {
            retraits.forEach(r -> {
                String methodeLabel = r.getMethod() != null ? r.getMethod().name() : "N/A";
                String statutLabel = "En attente";
                if (r.getStatus() == WithdrawalStatus.COMPLETED) {
                    statutLabel = "Effectué";
                } else if (r.getStatus() == WithdrawalStatus.REJECTED) {
                    statutLabel = "Refusé";
                }

                transactions.add(WalletTransactionDTO.builder()
                        .label("Retrait " + methodeLabel)
                        .montant(r.getAmount().negate()) // Montant négatif (débit)
                        .type(TransactionType.SORTIE)
                        .statut(statutLabel)
                        .date(r.getCreatedAt().toLocalDate())
                        .build());
            });
        }

        // 5. Trier par date décroissante
        transactions.sort(Comparator.comparing(WalletTransactionDTO::getDate).reversed());

        return transactions;
    }


    private String genererReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }

    private WithdrawalRequestDTO mapToWithdrawalDTO(WithdrawalRequest retrait) {
        return WithdrawalRequestDTO.builder()
            .id(retrait.getId())
            .reference(retrait.getReference())
            .montant(retrait.getAmount())
            .methode(retrait.getMethod())
            .numeroDeTelephone(retrait.getPhoneNumber())
            .statut(retrait.getStatus())
            .createdAt(retrait.getCreatedAt())
            .build();
    }


    /**
     * Crée un nouveau portefeuille pour un prestataire donné (sécurité).
     */
    private Wallet creerWallet(Long providerId) {
        User provider = userRepository.findById(providerId)
            .orElseThrow(() -> new ResourceNotFoundException("Prestataire non trouvé"));
        return Wallet.builder()
                .provider(provider)
                .availableBalance(BigDecimal.ZERO)
                .pendingBalance(BigDecimal.ZERO)
                .totalThisMonth(BigDecimal.ZERO)
                .build();
    }

    /**
     * Récupère les données consolidées du tableau de bord pour le prestataire connecté.
     * Calcule les compteurs principaux, les montants financiers en cours et les tendances d'évolution mensuelle.
     */
    @Override
    public ProviderDashboardDTO getDashboard() {
        // Étape 1 : Récupérer le prestataire connecté
        User currentProvider = getCurrentUser();
        Long providerId = currentProvider.getId();

        // Étape 2 : Récupérer les informations d'identité
        String companyName = currentProvider.getCompanyName() != null ? currentProvider.getCompanyName() : "Prestataire";
        String role = "Prestataire";

        // Étape 3 : Calculer les KPIs principaux du mois courant
        // - totalRequestsCount : Nombre total de demandes reçues (prestataire notifié)
        int totalRequestsCount = interventionRepository.countByNotifiedProvidersId(providerId);

        // - pendingQuotesCount : Nombre de devis envoyés/en attente de validation (statut PENDING)
        int pendingQuotesCount = interventionRepository.countByNotifiedProvidersIdAndStatus(providerId, InterventionStatus.PENDING);

        // - inProgressCount : Nombre d'interventions actuellement en cours de réalisation (statut STARTED)
        int inProgressCount = interventionRepository.countBySelectedProviderIdAndStatus(providerId, InterventionStatus.STARTED);

        // - validatedCount : Nombre d'interventions entièrement validées et clôturées (statut FINAL_VALIDATION)
        int validatedCount = interventionRepository.countBySelectedProviderIdAndStatus(providerId, InterventionStatus.FINAL_VALIDATION);

        // Étape 4 : Calculer les missions en attente et les encours financiers
        // - pendingMissionsCount : Dévis accepté et travaux non démarrés (statut SYNDIC_VALIDATED)
        int pendingMissionsCount = interventionRepository.countBySelectedProviderIdAndStatus(providerId, InterventionStatus.SYNDIC_VALIDATED);

        // - pendingPaymentsAmount : Tout l'argent que les syndics doivent encore au prestataire pour toutes les interventions acceptées (remainingAmount > 0)
        BigDecimal pendingPaymentsAmount = interventionRepository.sumRemainingAmountByProviderId(providerId);

        // Étape 5 : Calculer les tendances d'évolution (%) par rapport au mois dernier (Modèle Coopachat)
        LocalDate today = LocalDate.now();

        // Définition des bornes temporelles pour le mois en cours
        LocalDateTime monthStart = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime monthEnd = today.atTime(23, 59, 59, 999_999_999);

        // Définition des bornes temporelles pour le mois précédent (M-1)
        LocalDate lastMonthDate = today.minusMonths(1);
        LocalDateTime lastMonthStart = lastMonthDate.withDayOfMonth(1).atStartOfDay();
        LocalDateTime lastMonthEnd = lastMonthDate.atTime(23, 59, 59, 999_999_999);

        // - Tendance des demandes reçues
        int reqCeMois = interventionRepository.countByNotifiedProvidersIdAndCreatedAtBetween(providerId, monthStart, monthEnd);
        int reqMoisDernier = interventionRepository.countByNotifiedProvidersIdAndCreatedAtBetween(providerId, lastMonthStart, lastMonthEnd);
        double requestsVariation = calculateVariation(reqCeMois, reqMoisDernier);

        // - Tendance des devis en attente
        int quotesCeMois = interventionRepository.countByNotifiedProvidersIdAndStatusAndCreatedAtBetween(providerId, InterventionStatus.PENDING, monthStart, monthEnd);
        int quotesMoisDernier = interventionRepository.countByNotifiedProvidersIdAndStatusAndCreatedAtBetween(providerId, InterventionStatus.PENDING, lastMonthStart, lastMonthEnd);
        double pendingQuotesVariation = calculateVariation(quotesCeMois, quotesMoisDernier);

        // - Tendance des interventions en cours
        int progCeMois = interventionRepository.countBySelectedProviderIdAndStatusAndCreatedAtBetween(providerId, InterventionStatus.STARTED, monthStart, monthEnd);
        int progMoisDernier = interventionRepository.countBySelectedProviderIdAndStatusAndCreatedAtBetween(providerId, InterventionStatus.STARTED, lastMonthStart, lastMonthEnd);
        double inProgressVariation = calculateVariation(progCeMois, progMoisDernier);

        // - Tendance des interventions validées
        int valCeMois = interventionRepository.countBySelectedProviderIdAndStatusAndCreatedAtBetween(providerId, InterventionStatus.FINAL_VALIDATION, monthStart, monthEnd);
        int valMoisDernier = interventionRepository.countBySelectedProviderIdAndStatusAndCreatedAtBetween(providerId, InterventionStatus.FINAL_VALIDATION, lastMonthStart, lastMonthEnd);
        double validatedVariation = calculateVariation(valCeMois, valMoisDernier);

        // Étape 6 : Calculer la performance hebdomadaire (7 jours glissants)
        List<DailyRevenueDTO> performanceHebdo = buildPerformanceHebdo(providerId);

        // Étape 7 : Calculer les statistiques globales du portefeuille (Wallet)
        Wallet wallet = walletRepository.findByProviderId(providerId)
                .orElse(null);

        // Solde disponible actuel du prestataire
        BigDecimal totalRevenu = wallet != null ? wallet.getAvailableBalance() : BigDecimal.ZERO;

        // Somme de tous les revenus journaliers récoltés de la liste des 7 derniers jours glissants
        BigDecimal totalSemaine = performanceHebdo.stream()
                // On extrait uniquement le montant de chaque gain quotidien
                .map(DailyRevenueDTO::getMontant)
                // On additionne tous ces montants en partant de zéro (somme cumulée)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Si le total de la semaine est strictement supérieur à zéro
        BigDecimal moyenneParJour = totalSemaine.compareTo(BigDecimal.ZERO) > 0
                // Alors on divise le total de la semaine par 7 jours avec un arrondi mathématique à l'entier le plus proche (HALF_UP)
                ? totalSemaine.divide(BigDecimal.valueOf(7), 0, RoundingMode.HALF_UP)
                // Sinon (aucun gain), la moyenne par jour est fixée à zéro
                : BigDecimal.ZERO;

        // Nombre total d'interventions assignées au prestataire (tous statuts confondus)
        int totalInterventions = interventionRepository.countBySelectedProviderId(providerId);

        // Total des gains récoltés sur les 7 derniers jours glissants (semaine en cours)
        BigDecimal totalCetteSemaine = totalSemaine;

        // Somme des paiements validés reçus sur les 7 jours précédents (semaine précédente, du jour J-14 à J-7)
        // Note : On utilise ici une approche de fenêtre glissante (rolling window) de 7 jours pour la comparaison
        BigDecimal totalSemaineDerniere = paymentRepository.sumByProviderIdBetween(
                providerId,
                LocalDate.now().minusDays(14),
                LocalDate.now().minusDays(7)
        );

        // Calcul de la variation en pourcentage entre cette semaine et la semaine dernière
        int variationHebdo = (int) calculateVariation(totalCetteSemaine.intValue(), totalSemaineDerniere.intValue());

        // Étape 8 : Assemblage et retour du DTO consolidé
        return ProviderDashboardDTO.builder()
                .companyName(companyName)                       // Nom de l'entreprise du prestataire
                .role(role)                                     // Rôle du prestataire connecté ("Prestataire")
                .totalRequestsCount(totalRequestsCount)         // Total des demandes d'intervention reçues
                .pendingQuotesCount(pendingQuotesCount)         // Devis envoyés en attente de validation par le syndic
                .inProgressCount(inProgressCount)               // Interventions actuellement en cours de réalisation
                .validatedCount(validatedCount)                 // Interventions entièrement terminées et clôturées
                .requestsVariation(requestsVariation)           // Tendance mensuelle des demandes reçues (%)
                .pendingQuotesVariation(pendingQuotesVariation) // Tendance mensuelle des devis en attente (%)
                .inProgressVariation(inProgressVariation)       // Tendance mensuelle des interventions en cours (%)
                .validatedVariation(validatedVariation)         // Tendance mensuelle des interventions validées (%)
                .pendingMissionsCount(pendingMissionsCount)     // Devis acceptés par les syndics mais non démarrés
                .pendingPaymentsAmount(pendingPaymentsAmount)   // Total cumulé restant dû par l'ensemble des syndics
                .performanceHebdo(performanceHebdo)             // Historique financier des 7 derniers jours glissants
                .totalRevenu(totalRevenu)                       // Solde actuel disponible dans le portefeuille
                .moyenneParJour(moyenneParJour)                 // Moyenne quotidienne des gains de cette semaine
                .totalInterventions(totalInterventions)         // Total cumulé de toutes ses interventions à vie
                .variationHebdo(variationHebdo)                 // Tendance des revenus par rapport à la semaine d'avant (%)
                .build();
    }

    /**
     * Construit les données de revenus journaliers pour les 7 derniers jours glissants.
     * Cette liste alimente le graphique hebdomadaire de performance sur le tableau de bord mobile.
     */
    private List<DailyRevenueDTO> buildPerformanceHebdo(Long providerId) {
        // Liste ordonnée des labels abrégés des jours de la semaine
        List<String> jours = List.of(
            "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim"
        );

        LocalDate today = LocalDate.now();

        // On génère un flux sur les 7 derniers jours (du plus ancien à aujourd'hui)
        return IntStream.range(0, 7).mapToObj(i -> {
            // Calcul du jour glissant de la semaine
            LocalDate jour = today.minusDays(6 - i);

            // Somme de tous les paiements complétés et validés ce jour-là
            BigDecimal montant = paymentRepository.sumByProviderIdAndDate(providerId, jour);

            return DailyRevenueDTO.builder()
                .jour(jours.get(jour.getDayOfWeek().getValue() - 1))
                .montant(montant != null ? montant : BigDecimal.ZERO)
                .build();
        }).collect(Collectors.toList());
    }

    /**
     * Calcule la variation en pourcentage entre deux périodes avec protection contre la division par zéro.
     * Arrondit le résultat final à une décimale (ex: 8.5 pour +8.5%).
     */
    private double calculateVariation(int currentValue, int previousValue) {
        // Éviter la division par zéro si le mois dernier était à 0
        if (previousValue == 0) {
            // +100% si nouvelle activité ce mois-ci, sinon 0%
            return currentValue > 0 ? 100.0 : 0.0;
        }
        // Formule standard : ((valeurCourante - valeurPrécédente) * 100) / valeurPrécédente
        double rawVariation = ((currentValue - previousValue) * 100.0) / previousValue;
        
        // Arrondi à une décimale (ex: 8.5 pour +8.5%)
        return Math.round(rawVariation * 10.0) / 10.0;
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Prestataire non trouvé"));
    }
}
