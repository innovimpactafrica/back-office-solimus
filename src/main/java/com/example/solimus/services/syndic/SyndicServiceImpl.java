package com.example.solimus.services.syndic;

import com.example.solimus.dtos.charge.ChargeLineDTO;
import com.example.solimus.dtos.charge.ChargeResponseDTO;
import com.example.solimus.dtos.charge.ChargeDocumentDTO;
import com.example.solimus.dtos.charge.CreateChargeDTO;
import com.example.solimus.dtos.intervention.CreateInterventionRequestDTO;
import com.example.solimus.dtos.intervention.InterventionRequestDTO;
import com.example.solimus.dtos.intervention.InterventionStatusHistoryDTO;
import com.example.solimus.dtos.intervention.WorkflowStepDTO;
import com.example.solimus.dtos.intervention.NearbyProviderDTO;
import com.example.solimus.dtos.intervention.SyndicQuoteDTO;
import com.example.solimus.dtos.residence.PropertyDTO;
import com.example.solimus.dtos.syndic.CreateCoOwnerDTO;
import com.example.solimus.dtos.provider.WithdrawalRequestDTO;
import com.example.solimus.entities.Charge;
import com.example.solimus.entities.ChargeAllocation;
import com.example.solimus.entities.ChargeDocument;
import com.example.solimus.entities.ChargeLine;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Quote;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.Role;
import com.example.solimus.entities.Specialty;
import com.example.solimus.entities.User;
import com.example.solimus.entities.Wallet;
import com.example.solimus.entities.WithdrawalRequest;
import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.enums.ERole;
import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.QuoteStatus;
import com.example.solimus.enums.UserStatus;
import com.example.solimus.enums.SubscriptionPlan;
import com.example.solimus.enums.SubscriptionStatus;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.ActivationCodeService;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.geolocation.GeolocationService;
import com.example.solimus.services.provider.ProviderService;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.entities.Payment;
import com.example.solimus.enums.PaymentType;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.dtos.syndic.PayerAcompteDTO;
import com.example.solimus.dtos.syndic.PaymentDTO;
import com.example.solimus.dtos.syndic.PaymentResponseDTO;
import com.example.solimus.dtos.syndic.ValiderTravauxDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
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

/**
 * Implémentation du service destiné aux actions du Syndic.
 * Gère les résidences, les biens, les copropriétaires et le cycle de vie des interventions.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SyndicServiceImpl implements SyndicService {

    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final UserRepository userRepository;
    private final SpecialtyRepository specialtyRepository;
    private final InterventionRequestRepository interventionRepository;
    private final QuoteRepository quoteRepository;
    private final ProviderRatingRepository providerRatingRepository;
    private final RoleRepository roleRepository;
    private final ActivationCodeService activationCodeService;
    private final EmailService emailService;
    private final GeolocationService geolocationService;
    private final PaymentRepository paymentRepository;
    private final ProviderService providerService;
    private final SubscriptionRepository subscriptionRepository;
    private final ChargeRepository chargeRepository;
    private final ChargeLineRepository chargeLineRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final WalletRepository walletRepository;
    private final ChargeAllocationRepository chargeAllocationRepository;

    @Value("${solimus.geolocation.search-radius-km:30.0}")
    private double searchRadiusKm;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    // =========================================================================
    // GESTION DES BIENS (PROPERTIES)
    // =========================================================================

    // =========================================================================
    // CRÉER UNE CHARGE
    // =========================================================================
    @Override
    @Transactional
    public String createCharge(CreateChargeDTO dto) {

        User currentSyndic = getCurrentUser();

        // 1. Vérifier que la résidence existe et appartient à ce syndic
        Residence residence = residenceRepository.findById(dto.getResidenceId())
            .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à créer une charge pour cette résidence");
        }

        // 2. Générer la référence globale : CHG-XXXXXX (préfixe + nombre aléatoire)
        String reference = "CHG-" + (int)(Math.random() * 900000 + 100000);

        // 3. Créer la charge globale avec les infos du DTO
        Charge charge = new Charge();
        charge.setReference(reference);
        charge.setTitle(dto.getTitle());
        charge.setDescription(dto.getDescription());
        charge.setType(dto.getType());
        charge.setTotalAmount(dto.getTotalAmount());
        charge.setPeriod(dto.getPeriod());
        charge.setDueDate(dto.getDueDate());
        charge.setResidence(residence);
        charge.setSyndic(currentSyndic);

        Charge savedCharge = chargeRepository.save(charge);

        if (dto.getDocuments() != null && !dto.getDocuments().isEmpty()) {
            dto.getDocuments().forEach(documentDTO -> {
                ChargeDocument document = new ChargeDocument();
                document.setCharge(savedCharge);
                document.setFileName(documentDTO.getFileName());
                document.setOriginalFileName(documentDTO.getOriginalFileName());
                document.setFileUrl(documentDTO.getFileUrl());
                document.setFileSizeKb(documentDTO.getFileSizeKb());
                document.setContentType(documentDTO.getContentType());
                savedCharge.getDocuments().add(document);
            });
            chargeRepository.save(savedCharge);
        }

        // 4. Ajouter les lignes de répartition des frais (ex: entretien, électricité...)
        if (dto.getLines() != null && !dto.getLines().isEmpty()) {
            log.info("Ajout de {} lignes de répartition pour la charge {}", dto.getLines().size(), reference);
            dto.getLines().forEach(lineDTO -> {
                ChargeLine line = new ChargeLine();
                line.setLabel(lineDTO.getLabel());
                line.setAmount(lineDTO.getAmount());
                line.setCharge(savedCharge);
                chargeLineRepository.save(line);
            });
        } else {
            log.warn("Aucune ligne de répartition fournie pour la charge {}", reference);
        }

        // 5. Récupérer tous les biens avec propriétaire dans cette résidence
        List<Property> properties = propertyRepository.findByResidenceIdAndOwnerIsNotNull(dto.getResidenceId());

        if (properties.isEmpty()) {
            throw new BadRequestException("Aucun bien avec copropriétaire dans cette résidence");
        }

        // 6. Calculer le montant par copropriétaire (répartition égale)
        BigDecimal montantParCopro = dto.getTotalAmount()
            .divide(BigDecimal.valueOf(properties.size()), 0, RoundingMode.HALF_UP);

        // 7. Créer une allocation pour chaque copropriétaire
        // Référence individuelle : CHG-XXXXXX-PROP-YYYYYY
        properties.forEach(property -> {
            ChargeAllocation allocation = new ChargeAllocation();
            allocation.setReference(reference + "-" + property.getReference());
            allocation.setCharge(savedCharge);
            allocation.setProperty(property);
            allocation.setOwner(property.getOwner());
            allocation.setAmount(montantParCopro);
            allocation.setStatus(ChargeStatus.EN_ATTENTE);
            chargeAllocationRepository.save(allocation);
        });

        log.info("Charge {} créée — {} allocations générées", reference, properties.size());

        return "Charge créée avec succès";
    }

    // =========================================================================
    // LISTER LES CHARGES D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<ChargeResponseDTO> getChargesByResidence(Long residenceId) {

        User currentSyndic = getCurrentUser();

        // Vérifier que la résidence appartient à ce syndic
        Residence residence = residenceRepository.findById(residenceId)
            .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Accès non autorisé");
        }

        return chargeRepository
            .findByResidenceIdOrderByCreatedAtDesc(residenceId)
            .stream()
            .map(this::toChargeDTO)
            .collect(Collectors.toList());
    }

    // =========================================================================
    // SUPPRIMER UNE CHARGE
    // =========================================================================
    @Override
    @Transactional
    public void deleteCharge(Long chargeId) {

        User currentSyndic = getCurrentUser();

        Charge charge = chargeRepository.findById(chargeId)
            .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));

        // Vérifier que la résidence appartient à ce syndic
        if (!charge.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Accès non autorisé");
        }

        // Supprimer les allocations associées
        chargeAllocationRepository.deleteByCharge(charge);

        // Supprimer la charge
        chargeRepository.delete(charge);
    }

    // =========================================================================
    // LISTER LES BIENS D'UNE RÉSIDENCE
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<PropertyDTO> getPropertiesByResidence(Long residenceId) {

        User currentSyndic = getCurrentUser();

        // Vérifier que la résidence appartient à ce syndic
        Residence residence = residenceRepository.findById(residenceId)
            .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Accès non autorisé");
        }

        return propertyRepository.findByResidenceId(residenceId).stream()
            .map(this::mapToPropertyDTO)
            .collect(Collectors.toList());
    }

    /**
     * Assigne un copropriétaire à un bien existant.
     * Vérifie que le compte est actif avant l'assignation.
     */
    @Override
    @Transactional
    public PropertyDTO addOwner(Long propertyId, Long userId) {
        User currentSyndic = getCurrentUser();
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));

        // Sécurité : Vérifier que le syndic gère bien la résidence du bien
        if (!property.getResidence().getSyndic().equals(currentSyndic)) {
            throw new BadRequestException("Vous n'êtes pas autorisé à modifier ce bien.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        if (user.getRole() == null || !user.getRole().getName().equals(ERole.ROLE_COPROPRIETAIRE)) {
            throw new BadRequestException("Seul un utilisateur avec le rôle COPROPRIETAIRE peut posséder un bien.");
        }

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BadRequestException("Le compte du copropriétaire doit être actif pour posséder un bien.");
        }

        property.setOwner(user);
        return mapToPropertyDTO(propertyRepository.save(property));
    }

    // =========================================================================
    // GESTION DES INTERVENTIONS
    // =========================================================================

    /**
     * Recherche les prestataires situés à proximité d'une résidence pour une spécialité donnée.
     * Utilise le calcul de distance Haversine en SQL.
     */
    @Override
    public List<NearbyProviderDTO> findNearbyProviders(Long residenceId, Long specialtyId) {
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Appel SQL natif pour filtrer les prestataires par distance (30km par défaut)
        List<User> providers = userRepository.findNearbyProviders(
                residence.getLatitude().doubleValue(),
                residence.getLongitude().doubleValue(),
                specialtyId,
                searchRadiusKm
        );

        // Transformation en DTO avec calcul fin de la distance pour l'affichage
        return providers.stream().map(provider -> {
            double distance = geolocationService.calculateDistance(
                    residence.getLatitude().doubleValue(),
                    residence.getLongitude().doubleValue(),
                    provider.getLatitude().doubleValue(),
                    provider.getLongitude().doubleValue()
            );

            boolean isPremium = subscriptionRepository.findByProviderId(provider.getId())
                    .map(sub -> sub.getPlan() == SubscriptionPlan.PREMIUM && sub.getStatus() == SubscriptionStatus.ACTIVE)
                    .orElse(false);

            return NearbyProviderDTO.builder()
                    .id(provider.getId())
                    .firstName(provider.getFirstName())
                    .lastName(provider.getLastName())
                    .companyName(provider.getCompanyName())
                    .specialtyName(provider.getSpecialty() != null ? provider.getSpecialty().getName() : "N/A")
                    .distanceKm(Math.round(distance * 10.0) / 10.0) // Arrondi à 1 décimale
                    .premium(isPremium)
                    .build();
        }).collect(Collectors.toList());
    }

    /**
     * Crée une demande d'intervention et notifie les prestataires sélectionnés par email.
     */
    @Override
    @Transactional
    public InterventionRequestDTO createInterventionRequest(CreateInterventionRequestDTO dto) {
        User currentSyndic = getCurrentUser();
        
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));
        Property property = propertyRepository.findById(dto.getPropertyId())
                .orElseThrow(() -> new ResourceNotFoundException("Bien introuvable"));
        Specialty specialty = specialtyRepository.findById(dto.getSpecialtyId())
                .orElseThrow(() -> new ResourceNotFoundException("Spécialité introuvable"));

        InterventionRequest request = new InterventionRequest();
        request.setTitle(dto.getTitle());
        request.setDescription(dto.getDescription());
        request.addStatusHistory(InterventionStatus.PENDING, currentSyndic);
        request.setInitiatedBy(InitiatedBy.SYNDIC);
        request.setSyndic(currentSyndic);
        request.setResidence(residence);
        request.setProperty(property);
        request.setSpecialty(specialty);
        request.setPhotoUrls(dto.getPhotoUrls());

        // Diffusion automatique aux prestataires proches
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
                    log.error("Erreur lors de l'envoi de notification email à {}", provider.getEmail());
                }
            });

            log.info("Diffusion automatique : {} prestataires notifiés pour l'intervention {}", nearbyProviders.size(), request.getTitle());
        }

        return mapToInterventionDTO(interventionRepository.save(request));
    }

    /**
     * Accepte un devis et rejette automatiquement tous les autres devis en attente pour la même demande.
     */
    @Override
    @Transactional
    public void acceptQuote(Long requestId, Long quoteId) {
        // 1. Récupérer la demande avec un verrou d'écriture.
        // findByIdForUpdate bloque temporairement cette demande pendant la transaction
        // pour éviter qu'une autre requête l'accepte ou la modifie en même temps.
        // À la fin de la méthode, @Transactional fait le COMMIT et MySQL libère le verrou.
        InterventionRequest request = interventionRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        // 2. Vérifier que c'est bien LE syndic ou le propriétaire qui a créé cette demande
        User currentUser = getCurrentUser();
        boolean isSyndicOwner = request.getSyndic() != null
            && request.getSyndic().getId().equals(currentUser.getId());
        boolean isPropertyOwner = request.getOwner() != null
            && request.getOwner().getId().equals(currentUser.getId());
        if (!isSyndicOwner && !isPropertyOwner) {
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
        request.addStatusHistory(InterventionStatus.SYNDIC_VALIDATED, getCurrentUser());
    }

    /**
     * Récupère toutes les demandes d'intervention créées par le syndic connecté.
     */
    @Override
    @Transactional(readOnly = true)
    public List<InterventionRequestDTO> getMyInterventionRequests() {
        return interventionRepository.findAllByResidenceSyndic(getCurrentUser()).stream()
                .map(this::mapToInterventionDTO)
                .collect(Collectors.toList());
    }

    /**
     * Marque une intervention comme prise en charge par le syndic et diffuse aux prestataires proches.
     */
    @Override
    @Transactional
    public InterventionRequestDTO assignIntervention(Long requestId) {
        InterventionRequest request = interventionRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande d'intervention introuvable"));

        // Vérifier que le syndic est autorisé à gérer cette demande
        if (request.getSyndic() == null || !request.getSyndic().getId().equals(getCurrentUser().getId())) {
            throw new BadRequestException("Vous n'êtes pas autorisé à gérer cette demande");
        }

        // Vérifier que la demande est en attente
        if (request.getStatus() != InterventionStatus.PENDING) {
            throw new BadRequestException("Cette demande n'est pas en attente de prise en charge");
        }

        // Changer le statut à SYNDIC_ASSIGNED
        request.setStatus(InterventionStatus.SYNDIC_ASSIGNED);
        request.addStatusHistory(InterventionStatus.SYNDIC_ASSIGNED, getCurrentUser());

        // Diffusion automatique aux prestataires proches
        if (request.getResidence().getLatitude() == null || request.getResidence().getLongitude() == null) {
            throw new BadRequestException("La résidence n'a pas de coordonnées GPS, impossible de trouver des prestataires proches");
        }

        List<User> nearbyProviders = userRepository.findNearbyProviders(
                request.getResidence().getLatitude().doubleValue(),
                request.getResidence().getLongitude().doubleValue(),
                request.getSpecialty().getId(),
                30.0 // rayon de 30 km
        );

        if (nearbyProviders.isEmpty()) {
            log.warn("Aucun prestataire trouvé dans un rayon de 30 km pour la résidence {} et la spécialité {}",
                    request.getResidence().getName(), request.getSpecialty().getName());
        } else {
            request.setNotifiedProviders(nearbyProviders);

            nearbyProviders.forEach(provider -> {
                try {
                    if (provider.isNotificationsEnabled()) {
                        emailService.sendInterventionNotification(
                                provider.getEmail(),
                                provider.getFirstName(),
                                request.getTitle(),
                                request.getResidence().getName()
                        );
                    }
                } catch (Exception e) {
                    log.error("Erreur lors de l'envoi de l'email au prestataire {}", provider.getEmail(), e);
                }
            });

            log.info("Diffusion automatique : {} prestataires notifiés pour l'intervention {}", nearbyProviders.size(), request.getTitle());
        }

        InterventionRequest saved = interventionRepository.save(request);
        log.info("Intervention {} prise en charge par le syndic {}", request.getTitle(), getCurrentUser().getEmail());

        return mapToInterventionDTO(saved);
    }
    /**
     * Récupère la liste des devis reçus pour une demande d'intervention spécifique.
     * Les devis sont triés par prix croissant (du moins cher au plus cher).
     */
    @Override
    @Transactional(readOnly = true)
    public List<SyndicQuoteDTO> getQuotesByInterventionRequest(Long requestId) {

        // Vérifier que la demande existe
        InterventionRequest request = interventionRepository.findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Demande d'intervention introuvable"));

        // 1. Récupérer les devis triés du moins cher au plus cher
        List<Quote> quotes = quoteRepository
            .findAllByInterventionRequestOrderByTotalAmountAsc(request)
            .stream()
            .filter(quote -> quote.getStatus() != QuoteStatus.DRAFT)
            .collect(Collectors.toList());

        // Si aucun devis reçu on retourne une liste vide
        if (quotes.isEmpty()) return List.of();

        // 2. Récupérer le prix le plus élevé pour normaliser les scores
        double maxAmount = quotes.stream()
            .mapToDouble(q -> q.getTotalAmount().doubleValue())
            .max()
            .orElse(1.0);

        // 3. Mapper chaque devis avec son score qualité/prix
        List<SyndicQuoteDTO> dtos = quotes.stream()
            .map(quote -> {

                // Note moyenne réelle du prestataire
                Double noteMoyenne = providerRatingRepository
                    .calculerNoteMoyenne(quote.getProvider().getId());

                // Si le prestataire n'a jamais été noté → 0.0 par défaut
                double rating = noteMoyenne != null ? noteMoyenne : 0.0;

                // Score prix : moins c'est cher mieux c'est
                // Ex: 135 000 / 160 000 = 0.84 → 1 - 0.84 = 0.16
                double scorePrix = 1.0 - (quote.getTotalAmount()
                    .doubleValue() / maxAmount);

                // Score note : plus c'est élevé mieux c'est
                // Ex: 4.4 / 5 = 0.88
                double scoreNote = rating / 5.0;

                // Score délai : moins c'est long mieux c'est
                // Ex: 1 jour → 1 - (1/30) = 0.97
                double scoreDelai = quote.getEstimatedDelay() != null
                    ? 1.0 - (quote.getEstimatedDelay().getDays() / 30.0)
                    : 0.0;

                // Score final : prix 40% + note 40% + délai 20%
                double scoreFinal = (scorePrix * 0.40)
                                  + (scoreNote * 0.40)
                                  + (scoreDelai * 0.20);

                // Convertir en pourcentage lisible pour le front
                // Ex: 0.74 → 74%
                int scoreQualitePrix = (int) Math.round(scoreFinal * 100);

                return SyndicQuoteDTO.builder()
                    .id(quote.getId())
                    .providerId(quote.getProvider().getId())
                    .providerName(quote.getProvider().getFirstName()
                        + " " + quote.getProvider().getLastName())
                    .companyName(quote.getProvider().getCompanyName())
                    .laborTotalAmount(quote.getLaborTotalAmount())
                    .materialTotalAmount(quote.getMaterialTotalAmount())
                    .totalAmount(quote.getTotalAmount())
                    .estimatedDelayLabel(quote.getEstimatedDelay() != null
                        ? quote.getEstimatedDelay().getLabel() : "N/A")
                    .additionalComments(quote.getAdditionalComments())
                    .status(quote.getStatus())
                    .providerRating(rating)
                    .scoreFinal(scoreFinal)
                    .scoreQualitePrix(scoreQualitePrix)
                    .isBestOffer(false) // on définit après
                    .createdAt(quote.getCreatedAt())
                    .build();
            })
            .collect(Collectors.toList());

        // 4. Trouver le devis avec le meilleur score
        // et lui mettre isBestOffer = true
        dtos.stream()
            .max(Comparator.comparingDouble(SyndicQuoteDTO::getScoreFinal))
            .ifPresent(best -> best.setBestOffer(true));

        return dtos;
    }

    // =========================================================================
    // GESTION DES COPROPRIÉTAIRES
    // =========================================================================

    /**
     * Ajoute un nouveau copropriétaire et lui envoie un code d'activation par email.
     */
    @Override
    @Transactional
    public void addCoOwner(CreateCoOwnerDTO dto) {
        if (userRepository.existsByEmail(dto.getEmail())) throw new BadRequestException("Email déjà utilisé.");
        
        Role role = roleRepository.findByName(ERole.ROLE_COPROPRIETAIRE).get();
        User user = new User();
        user.setFirstName(dto.getFirstName());
        user.setLastName(dto.getLastName());
        user.setEmail(dto.getEmail());
        user.setPhone(dto.getPhone());
        user.setRole(role);
        user.setStatus(UserStatus.PENDING);
        
        User saved = userRepository.save(user);
        
        // Génération du code d'activation mobile et envoi email
        String code = activationCodeService.generateAndStoreCodeMobile(saved);
        emailService.sendActivationCode(saved.getEmail(), code, saved.getFirstName());
    }

     // ================================================
    // ACOMPTE — versé avant ou au début des travaux
    // ================================================
    @Override
    @Transactional
    public PaymentResponseDTO payerAcompte(Long requestId, PayerAcompteDTO dto) {
        User currentSyndic = getCurrentUser();

        // 1. Vérifier si la demande d'intervention existe
        InterventionRequest request = interventionRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        // 2. Vérifier que c'est bien le syndic assigné à cette demande qui initie le paiement
        if (!request.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new BadRequestException("Vous n'êtes pas autorisé à payer pour cette demande.");
        }

        // 3. Vérifier qu'aucun acompte n'a déjà été versé pour cette intervention
        boolean acompteDejaVerse = paymentRepository
            .existsByInterventionRequestIdAndType(requestId, PaymentType.ACOMPTE);

        if (acompteDejaVerse) {
            throw new BadRequestException("Un acompte a déjà été versé pour cette intervention");
        }

        // 4. Vérifier qu'un prestataire a été sélectionné pour cette demande
        if (request.getSelectedProvider() == null) {
            throw new BadRequestException("Aucun prestataire n'est sélectionné pour cette demande.");
        }

        // 5. Générer une référence unique de transaction
        String transactionRef = genererReference("PAY");

        // 6. Enregistrer le paiement en statut PENDING (en attente du retour de TouchPay)
        Payment payment = Payment.builder()
            .reference(transactionRef)                       // Référence unique générée pour InTouch/TouchPay
            .interventionRequest(request)                     // Demande d'intervention liée à ce paiement
            .provider(request.getSelectedProvider())          // Prestataire qui doit recevoir l'argent
            .syndic(currentSyndic)                            // Syndic qui effectue le paiement
            .amount(dto.getMontant())                         // Montant de l'acompte à payer
            .type(PaymentType.ACOMPTE)                        // Spécifie qu'il s'agit d'un acompte
            .method(dto.getMethode())                         // Méthode choisie (ex: WAVE, ORANGE_MONEY, etc.)
            .status(PaymentStatus.PENDING)                    // Statut temporaire en attente du callback TouchPay
            .build();

        paymentRepository.save(payment);                      // Sauvegarde du paiement temporaire en base

        // 7. Construire l'URL de redirection de la WebView TouchPay
        // Le template contient des paramètres pour le bridge TouchPay avec la référence
        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, transactionRef);

        // 8. Retourner les détails au frontend pour charger la WebView
        return PaymentResponseDTO.builder()
            .success(true)                                    // Succès de l'initiation
            .message("Paiement initié. Veuillez compléter via TouchPay.")
            .transactionReference(transactionRef)             // Référence de transaction à suivre
            .amountToPay(dto.getMontant())                     // Montant exact à payer
            .paymentUrl(bridgeUrl)                            // URL de redirection vers le bridge TouchPay
            .build();
    }

    private String genererReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }

    // ================================================
    // VALIDATION + PAIEMENT SOLDE — après les travaux
    // ================================================
    @Override
    @Transactional
    public PaymentResponseDTO validerEtPayerSolde(Long requestId, ValiderTravauxDTO dto) {
        User currentSyndic = getCurrentUser();

        InterventionRequest request = interventionRepository
            .findById(requestId)
            .orElseThrow(() -> new ResourceNotFoundException("Demande introuvable"));

        if (request.getStatus() != InterventionStatus.FINISHED) {
            throw new BadRequestException("Les travaux ne sont pas encore terminés");
        }

        if (!request.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Non autorisé");
        }

        BigDecimal solde = request.getRemainingAmount() != null
            ? request.getRemainingAmount()
            : BigDecimal.ZERO;

        String transactionRef = genererReference("SOL");

        Payment payment = Payment.builder()
            .reference(transactionRef)
            .interventionRequest(request)
            .provider(request.getSelectedProvider())
            .syndic(currentSyndic)
            .amount(solde)
            .type(PaymentType.SOLDE)
            .method(dto.getMethode())
            .status(PaymentStatus.PENDING)
            .build();

        paymentRepository.save(payment);

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
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    /**
     * Récupère l'utilisateur actuellement authentifié via le SecurityContext.
     */
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email).orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    /**
 x
    /**
     * Mappe une entité Property vers son DTO.
     */
    private PropertyDTO mapToPropertyDTO(Property p) {
        return PropertyDTO.builder()
                .id(p.getId())
                .reference(p.getReference())
                .superficie(p.getSuperficie())
                .type(p.getTypeBien())
                .residenceId(p.getResidence() != null ? p.getResidence().getId() : null)
                .residenceName(p.getResidence() != null ? p.getResidence().getName() : null)
                .ownerId(p.getOwner() != null ? p.getOwner().getId() : null)
                .ownerName(p.getOwner() != null ? p.getOwner().getFirstName() + " " + p.getOwner().getLastName() : null)
                .build();
    }

    /**
     * Mappe une entité InterventionRequest vers le DTO de détail simplifié.
     */
    private InterventionRequestDTO mapToInterventionDTO(InterventionRequest request) {
        String residentPhone = "N/A";
        String residentEmail = "N/A";
        
        // Extraction des contacts du résident via le bien concerné
        if (request.getProperty() != null && request.getProperty().getOwner() != null) {
            User owner = request.getProperty().getOwner();
            residentPhone = owner.getPhone();
            residentEmail = owner.getEmail();
        }
        
        List<InterventionStatusHistoryDTO> historyDTOs = request.getHistory() != null
            ? request.getHistory().stream().map(h -> InterventionStatusHistoryDTO.builder()
                .id(h.getId())
                .status(h.getStatus())
                .statusLabel(h.getStatus() != null ? h.getStatus().getLabel() : null)
                .createdAt(h.getCreatedAt())
                .build()).collect(Collectors.toList())
            : new ArrayList<>();

        List<String> photoUrls = request.getPhotoUrls() != null
            ? new ArrayList<>(request.getPhotoUrls())
            : new ArrayList<>();

        return InterventionRequestDTO.builder()
                .id(request.getId())
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus())
                .statusLabel(request.getStatus() != null ? request.getStatus().getLabel() : null)
                .initiatedBy(request.getInitiatedBy())
                .locationType(request.getLocationType())
                .managementMode(request.getManagementMode())
                .urgencyLevel(request.getUrgencyLevel())
                .residenceName(request.getResidence() != null ? request.getResidence().getName() : "N/A")
                .residentPhone(residentPhone)
                .residentEmail(residentEmail)
                .photoUrls(photoUrls)
                .history(historyDTOs)
                .workflowSteps(buildWorkflow(request))
                .createdAt(request.getCreatedAt())
                .startedAt(request.getStartedAt())
                .finishedAt(request.getFinishedAt())
                .build();
    }

    private List<WorkflowStepDTO> buildWorkflow(InterventionRequest request) {
        InterventionStatus statut = request.getStatus();

        Function<InterventionStatus, LocalDateTime> findDate = (s) ->
            request.getHistory() != null ? request.getHistory().stream()
                   .filter(h -> h.getStatus() == s)
                   .map(h -> h.getCreatedAt())
                   .findFirst()
                   .orElse(null) : null;

        return List.of(
            WorkflowStepDTO.builder()
                .label("Demande reçue")
                .completed(true)
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
     * Mappe une entité Quote vers le DTO SyndicQuoteDTO (comparaison).
     */
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
                .createdAt(quote.getCreatedAt())
                .build();
    }

   

    private PaymentDTO toDTO(Payment payment) {
        return PaymentDTO.builder()
                .id(payment.getId())
                .reference(payment.getReference())
                .amount(payment.getAmount())
                .type(payment.getType())
                .method(payment.getMethod())
                .status(payment.getStatus())
                .createdAt(payment.getCreatedAt())
                .paidAt(payment.getPaidAt())
                .build();
    }

    private ChargeResponseDTO toChargeDTO(Charge charge) {
        List<ChargeLineDTO> lineDTOs = charge.getLines().stream()
            .map(line -> ChargeLineDTO.builder()
                .label(line.getLabel())
                .amount(line.getAmount())
                .build())
            .collect(Collectors.toList());

        List<ChargeDocumentDTO> documentDTOs = charge.getDocuments().stream()
            .map(document -> ChargeDocumentDTO.builder()
                .id(document.getId())
                .fileName(document.getFileName())
                .originalFileName(document.getOriginalFileName())
                .fileUrl(document.getFileUrl())
                .fileSizeKb(document.getFileSizeKb())
                .contentType(document.getContentType())
                .build())
            .collect(Collectors.toList());

        return ChargeResponseDTO.builder()
            .id(charge.getId())
            .reference(charge.getReference())
            .title(charge.getTitle())
            .type(charge.getType())
            .totalAmount(charge.getTotalAmount())
            .period(charge.getPeriod())
            .dueDate(charge.getDueDate())
            .residenceName(charge.getResidence().getName())
            .nombreAllocations(charge.getAllocations().size())
            .lines(lineDTOs)
            .documents(documentDTOs)
            .createdAt(charge.getCreatedAt())
            .build();
    }

    /**
     * Tâche planifiée exécutée chaque jour à 8h.
     * Marque automatiquement EN_RETARD les charges dont la date d'échéance est passée.
     */
    @Scheduled(cron = "0 0 8 * * *") // chaque jour à 8h
    @Transactional
    public void marquerChargesEnRetard() {

        List<ChargeAllocation> enRetard = chargeAllocationRepository
            .findAllByStatusAndChargeDueDateBefore(
                ChargeStatus.EN_ATTENTE, LocalDate.now());

        enRetard.forEach(a -> {
            a.setStatus(ChargeStatus.EN_RETARD);
            chargeAllocationRepository.save(a);
        });

        log.info("{} charge(s) passées EN_RETARD", enRetard.size());
    }

    // =========================================================================
    // GESTION DES RETRAITS PRESTATAIRES
    // =========================================================================

    @Override
    public List<WithdrawalRequestDTO> getWithdrawalRequests(WithdrawalStatus status) {
        return withdrawalRequestRepository.findAll().stream()
                .filter(w -> status == null || w.getStatus() == status)
                .map(this::mapToWithdrawalRequestDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public WithdrawalRequestDTO confirmWithdrawal(Long withdrawalId) {
        WithdrawalRequest retrait = getPendingWithdrawal(withdrawalId);
        Wallet wallet = getWithdrawalWallet(retrait);

        wallet.setPendingBalance(safeSubtract(wallet.getPendingBalance(), retrait.getAmount()));
        retrait.setStatus(WithdrawalStatus.COMPLETED);
        retrait.setProcessedAt(LocalDateTime.now());

        walletRepository.save(wallet);
        WithdrawalRequest saved = withdrawalRequestRepository.save(retrait);
        return mapToWithdrawalRequestDTO(saved);
    }

    @Override
    @Transactional
    public WithdrawalRequestDTO rejectWithdrawal(Long withdrawalId, String motifRefus) {
        WithdrawalRequest retrait = getPendingWithdrawal(withdrawalId);
        Wallet wallet = getWithdrawalWallet(retrait);

        wallet.setPendingBalance(safeSubtract(wallet.getPendingBalance(), retrait.getAmount()));
        wallet.setAvailableBalance(wallet.getAvailableBalance().add(retrait.getAmount()));
        retrait.setStatus(WithdrawalStatus.REJECTED);
        retrait.setProcessedAt(LocalDateTime.now());
        retrait.setMotifRefus(motifRefus);

        walletRepository.save(wallet);
        WithdrawalRequest saved = withdrawalRequestRepository.save(retrait);
        return mapToWithdrawalRequestDTO(saved);
    }

    // =========================================================================
    // MÉTHODES PRIVÉES — OUTILS INTERNES POUR LES RETRAITS
    // =========================================================================

    private WithdrawalRequest getPendingWithdrawal(Long withdrawalId) {
        WithdrawalRequest retrait = withdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        if (retrait.getStatus() != WithdrawalStatus.PENDING) {
            throw new BadRequestException("Cette demande de retrait a déjà été traitée");
        }

        return retrait;
    }

    private Wallet getWithdrawalWallet(WithdrawalRequest retrait) {
        return walletRepository.findByProviderId(retrait.getProvider().getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet prestataire introuvable"));
    }

    private BigDecimal safeSubtract(BigDecimal currentValue, BigDecimal amount) {
        BigDecimal result = currentValue.subtract(amount);
        return result.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : result;
    }

    private WithdrawalRequestDTO mapToWithdrawalRequestDTO(WithdrawalRequest retrait) {
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
}
