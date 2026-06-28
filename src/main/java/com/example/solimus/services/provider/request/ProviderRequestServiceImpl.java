package com.example.solimus.services.provider.request;

import com.example.solimus.dtos.admin.EstimatedDelayDTO;
import com.example.solimus.dtos.provider.request.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.ProviderRequestDisplayStatus;
import com.example.solimus.enums.QuoteItemType;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.EstimatedDelayRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.QuoteRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProviderRequestServiceImpl implements  ProviderRequestService{
    private final UserRepository userRepository;
    private final InterventionRequestRepository interventionRequestRepository;
    private final QuoteRepository quoteRepository;
    private final MinioService minioService;
    private final EstimatedDelayRepository estimatedDelayRepository;

    //---------------------------------------------------
    // Demandes de Travaux non assignés
    //----------------------------------------------------
    @Override
    public ProviderRequestsDTO getAvailableRequests(ProviderRequestDisplayStatus filterStatus, String search, Pageable pageable) {

        User currentProvider = getCurrentUser();

        // Normalisation : null si vide, trim sinon
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        // On compte toutes les demandes notifiées à ce prestataire, non assignées à lui
        long totalReceivedRequests = interventionRequestRepository.countAllNotifiedRequests(currentProvider);

        // On choisit la bonne requête, et au passage on retient QUEL statut elle représente
        Page<InterventionRequest> page;
        ProviderRequestDisplayStatus knownStatus = filterStatus;

        if (filterStatus == null) {
            // Cas particulier : pas de filtre, donc le statut N'EST PAS connu à l'avance,
            // on devra le calculer pour CHAQUE ligne individuellement
            page = interventionRequestRepository.findAllNotifiedRequests(currentProvider, normalizedSearch, pageable);
        }
        else {
            page = switch (filterStatus) {
                case REJECTED -> interventionRequestRepository.findRejectedRequests(currentProvider, normalizedSearch, pageable);
                case QUOTE_SENT -> interventionRequestRepository.findQuoteSentRequests(currentProvider, normalizedSearch, pageable);
                case PENDING_QUOTE -> interventionRequestRepository.findPendingQuoteRequests(currentProvider, normalizedSearch, pageable);
            };
        }
        // On transforme chaque InterventionRequest en DTO
        Page<ProviderRequestSummaryDTO> dtoPage = page.map(request -> {
            // Si on connaît déjà le statut (un filtre était actif), on l'utilise directement
            // Sinon (pas de filtre), on doit le calculer pour CETTE ligne précise
            ProviderRequestDisplayStatus status = knownStatus != null
                    ? knownStatus
                    : calculateDisplayStatus(request, currentProvider);
            return ProviderRequestSummaryDTO.builder()
                    .id(request.getId())
                    .title(request.getTitle())
                    .residenceName(request.getResidence() != null ? request.getResidence().getName() : "N/A")
                    .status(status)
                    .statusLabel(status.getLabel())
                    .createdAt(request.getCreatedAt())
                    .build();
        });

        return ProviderRequestsDTO.builder()
                .totalReceivedRequests(totalReceivedRequests)
                .requests(dtoPage)
                .build();
    }

    @Override
    public ProviderRequestDetailDTO getRequestDetails(Long requestId) {

        // 1. Récupérer le prestataire connecté
        User currentProvider = getCurrentUser();

        // 2. Récupérer la demande UNIQUEMENT si ce prestataire est dans la liste des notifiés
        // — un prestataire ne peut pas consulter une demande qui ne lui a pas été diffusée
        InterventionRequest request = interventionRequestRepository
                .findByIdAndNotifiedProvidersContaining(requestId, currentProvider)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Demande introuvable ou vous n'êtes pas autorisé à la consulter"));

        // 3. Sécurité : Si un autre prestataire a été sélectionné pour cette intervention,
        // l'accès est bloqué car ce prestataire a été refusé
        if (request.getSelectedProvider() != null && !request.getSelectedProvider().getId().equals(currentProvider.getId())) {
            throw new ForbiddenException("Un autre prestataire a été sélectionné pour cette intervention. Vous ne pouvez plus consulter ses détails.");
        }

        // 4. Déterminer le contact propriétaire selon le type de demande
        // — appartement → propriétaire, partie commune → syndic
        User contact = request.getProperty() != null
                ? request.getProperty().getOwner()
                : request.getSyndic();

        // 5. Convertir les chemins photos en URLs signées MinIO affichables par le front
        List<String> photoUrls = minioService.toPresignedUrls(request.getPhotoUrls());

        // 6. Calculer le statut affiché pour CE prestataire précis / Déjà en listant, on liste que les demandes où il n'a pas été choisi,
        // donc on sait déjà qu'il n'est pas choisi (même en détail, puisqu'on vient de la liste)
        ProviderRequestDisplayStatus displayStatus = calculateDisplayStatus(request, currentProvider);

        // 7. Assembler et retourner le DTO
        return ProviderRequestDetailDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : "N/A")
                .status(displayStatus)
                .statusLabel(displayStatus.getLabel())
                .description(request.getDescription())
                .createdAt(request.getCreatedAt())
                .photoUrls(photoUrls)
                .contactPhone(contact != null ? contact.getPhone() : "N/A")
                .contactEmail(contact != null ? contact.getEmail() : "N/A")
                .workflowSteps(buildWorkflowSteps(request))
                .build();
    }

    // =========================================================================
    // CRÉATION DE DEVIS
    // =========================================================================

    @Override
    @Transactional
    public void createQuote(CreateQuoteDTO dto) {
        // 1. Récupérer le prestataire connecté
        User provider = getCurrentUser();

        // 2. Récupération sécurisée : on ne trouve la demande QUE si le prestataire y
        // est rattaché
        InterventionRequest request = interventionRequestRepository
                .findByIdAndNotifiedProvidersContaining(dto.getInterventionRequestId(), provider)
                .orElseThrow(() -> new RuntimeException(
                        "Demande introuvable ou vous n'êtes pas autorisé à répondre à cette demande."));

        // 3. Vérifier que la demande accepte encore des devis (Statut PENDING ou QUOTE_SENT)
        if (request.getStatus() != InterventionStatus.PENDING && request.getStatus() != InterventionStatus.QUOTE_SENT) {
            throw new RuntimeException("Cette demande n'accepte plus de nouveaux devis.");
        }

        // 4. Vérifier que ce prestataire n'a pas déjà soumis un devis
        if (quoteRepository.existsByInterventionRequestAndProvider(request, provider)) {
            throw new RuntimeException("Vous avez déjà soumis un devis pour cette demande d'intervention.");
        }

        // 5. Récupération de l'option de délai choisie dans le dropdown
        EstimatedDelay delay = estimatedDelayRepository.findById(dto.getEstimatedDelayId())
                .orElseThrow(() -> new RuntimeException("Délai estimé introuvable"));

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
            quote.setStatus(QuoteStatus.SENT);
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

    // =========================================================================
    // LISTAGE DES DÉLAIS D'ESTIMATION
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<EstimatedDelayDTO> getEstimatedDelays() {
        return estimatedDelayRepository.findAll().stream()
                .map(delay -> EstimatedDelayDTO.builder()
                        .id(delay.getId())
                        .label(delay.getLabel())
                        .days(delay.getDays())
                        .build())
                .collect(Collectors.toList());
    }

    // =========================================================================
    // LISTAGE DES DEVIS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ProviderQuoteListDTO getMyQuotes(QuoteStatus statut, String search, int page, int size) {

        // 1. Récupérer le prestataire connecté
        User currentProvider = getCurrentUser();

        // 2. Configurer la pagination (tri par date de création, le plus récent en premier)
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // 3. Récupérer les devis paginés avec filtres
        Page<Quote> quotePage = quoteRepository.findMyQuotes(currentProvider.getId(), statut, search, pageable);

        // 4. Transformer les devis en DTOs (utilisation de map() pour la pagination automatique)
        Page<QuoteSummaryDTO> devisPage = quotePage.map(quote ->
            QuoteSummaryDTO.builder()
                .id(quote.getId())
                .reference(quote.getReference())
                .titre(quote.getInterventionRequest().getTitle())
                .residenceName(quote.getInterventionRequest().getResidence() != null ? quote.getInterventionRequest().getResidence().getName() : null)
                .appartement(quote.getInterventionRequest().getProperty() != null ? quote.getInterventionRequest().getProperty().getReference() : null)
                .date(quote.getCreatedAt().toLocalDate())
                .montant(quote.getTotalAmount())
                .statut(quote.getStatus())
                .build()
        );

        // 5. Calculer le montant total des devis acceptés
        BigDecimal totalValide = quoteRepository.sumTotalAmountByProviderAndStatus(currentProvider.getId(), QuoteStatus.ACCEPTED);
        if (totalValide == null) {
            totalValide = BigDecimal.ZERO;
        }

        // 6. Retourner le DTO avec le bandeau et la liste paginée
        return ProviderQuoteListDTO.builder()
                .totalValidAmount(totalValide)
                .devis(devisPage)
                .build();
    }

    // =========================================================================
    // DÉTAIL D'UN DEVIS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public QuoteDetailDTO getQuoteDetails(Long quoteId) {

        // 1. Récupérer le prestataire connecté
        User currentProvider = getCurrentUser();

        // 2. Récupérer le devis uniquement s'il appartient à ce prestataire
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new RuntimeException("Devis introuvable"));

        if (!quote.getProvider().getId().equals(currentProvider.getId())) {
            throw new RuntimeException("Vous n'êtes pas autorisé à consulter ce devis");
        }

        // 3. Récupérer la demande d'intervention associée
        InterventionRequest request = quote.getInterventionRequest();

        // 4. Déterminer le client (Usersyndic ou Usercopropriétaire)
        User client = request.getProperty() != null
                ? request.getProperty().getOwner()
                : request.getSyndic();

        // 5. Déterminer l'adresse (toujours l'adresse de la résidence)
        String clientAdresse = null;
        if (request.getResidence() != null) {
            clientAdresse = request.getResidence().getFullAddress();
        }

        // 5. Séparer les items par type (matériel vs main d'œuvre)
        List<QuoteLineDTO> materiaux = new ArrayList<>();
        List<QuoteLineDTO> mainOeuvre = new ArrayList<>();
        BigDecimal sousTotalMateriaux = BigDecimal.ZERO;
        BigDecimal sousTotalMainOeuvre = BigDecimal.ZERO;

        if (quote.getItems() != null) {
            for (QuoteItem item : quote.getItems()) {
                BigDecimal subtotal = item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()));

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
        }

        // 6. Construire et retourner le DTO
        return QuoteDetailDTO.builder()
                .reference(quote.getReference())
                .titre(request.getTitle())
                .statut(quote.getStatus())
                .montantTotal(quote.getTotalAmount())
                .dateEnvoi(quote.getCreatedAt().toLocalDate())
                .dateValidation(request.getQuoteAcceptedAt() != null ? request.getQuoteAcceptedAt().toLocalDate() : null)
                .clientNom(client != null ? client.getFirstName() + " " + client.getLastName() : null)
                .clientTelephone(client != null ? client.getPhone() : null)
                .clientEmail(client != null ? client.getEmail() : null)
                .clientAdresse(clientAdresse)
                .materiaux(materiaux)
                .sousTotalMateriaux(sousTotalMateriaux)
                .mainOeuvre(mainOeuvre)
                .sousTotalMainOeuvre(sousTotalMainOeuvre)
                .totalTTC(quote.getTotalAmount())
                .notes(quote.getAdditionalComments())
                .build();
    }


    //---------------------------------------------------
    // Méthodes utilitaires
    //----------------------------------------------------

    //***** Récupérer l'utilisateur connecté *****//
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Prestataire non trouvé"));

    }

    //***** Applique la règle métier pour déterminer le statut affiché à ce prestataire précis.
    // Précondition : cette méthode suppose que la demande (request) fait déjà partie de la liste des demandes notifiées à ce prestataire (currentProvider), ET qu'elle
    // n'a pas été assignée à lui (selectedProvider != currentProvider) *****//
    private ProviderRequestDisplayStatus calculateDisplayStatus(InterventionRequest request, User currentProvider) {

        // Cas 1 — un AUTRE prestataire a été choisi pour cette demande
        // (on sait que ce n'est pas MOI grâce à la précondition ci-dessus)
        if (request.getSelectedProvider() != null) {
            return ProviderRequestDisplayStatus.REJECTED;
        }

        // Cas 2 — personne n'a encore été choisi, on vérifie si MOI j'ai déjà soumis un devis
        boolean hasAlreadySubmittedQuote = quoteRepository
                .existsByInterventionRequestAndProvider(request, currentProvider);

        if (hasAlreadySubmittedQuote) {
            return ProviderRequestDisplayStatus.QUOTE_SENT;
        }

        // Cas 3 — je n'ai encore rien soumis, et personne n'a été choisi
        return ProviderRequestDisplayStatus.PENDING_QUOTE;
    }

    //***** Construit les 6 étapes du workflow pour une demande d'intervention *****//
    private List<WorkflowStepDTO> buildWorkflowSteps(InterventionRequest request) {
        List<WorkflowStepDTO> steps = new ArrayList<>();

        // Étape 1 : Demande reçue
        steps.add(WorkflowStepDTO.builder()
                .label("Demande reçue")
                .completed(true)
                .date(request.getCreatedAt())
                .build());

        // Étape 2 : Devis envoyé (si le prestataire a soumis un devis)
        User currentProvider = getCurrentUser();
        Optional<Quote> quoteOpt = quoteRepository.findByInterventionRequestAndProvider(request, currentProvider);
        steps.add(WorkflowStepDTO.builder()
                .label("Devis envoyé")
                .completed(quoteOpt.isPresent())
                .date(quoteOpt.map(Quote::getCreatedAt).orElse(null)) //si le Optional contient un Quote, on extrait sa date ; sinon on retourne null
                .build());

        // Étape 3 : Devis accepté (si un prestataire a été sélectionné)
        steps.add(WorkflowStepDTO.builder()
                .label("Devis accepté")
                .completed(request.getSelectedProvider() != null)
                .date(request.getQuoteAcceptedAt())
                .build());

        // Étape 4 : Travaux commencés
        steps.add(WorkflowStepDTO.builder()
                .label("Travaux commencés")
                .completed(request.getStartedAt() != null)
                .date(request.getStartedAt())
                .build());

        // Étape 5 : Travaux terminés
        steps.add(WorkflowStepDTO.builder()
                .label("Travaux terminés")
                .completed(request.getFinishedAt() != null)
                .date(request.getFinishedAt())
                .build());

        // Étape 6 : Validation finale
        steps.add(WorkflowStepDTO.builder()
                .label("Validation finale")
                .completed(request.getValidatedAt() != null)
                .date(request.getValidatedAt())
                .build());

        return steps;
    }

}
