package com.example.solimus.services.provider.profile;

import com.example.solimus.dtos.provider.profile.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProviderProfileServiceImpl implements ProviderProfileService {

    private final ProviderProfileRepository providerProfileRepository;
    private final UserRepository userRepository;
    private final ProviderSubscriptionRepository providerSubscriptionRepository;
    private final QuoteRepository quoteRepository;
    private final MinioService minioService;

    // =========================================================================
    // NOTIFICATIONS
    // =========================================================================

    @Override
    @Transactional
    public void toggleNotifications() {

        // On identifie le prestataire connecté via son JWT
        User currentProvider = getCurrentUser();

        // On inverse la valeur actuelle
        currentProvider.setNotificationsEnabled(!currentProvider.isNotificationsEnabled());

        // On sauvegarde le changement
        userRepository.save(currentProvider);
        log.info("Préférences de notification mises à jour pour le prestataire : {}",
                currentProvider.isNotificationsEnabled() ? "activées" : "désactivées");
    }

    // ============================================================
    // Mise à jour Localisation Prestataire
    // ============================================================

    @Override
    @Transactional
    public void updateLocation(UpdateLocationDTO dto) {

        // On identifie le prestataire connecté via son JWT
        User currentUser = getCurrentUser();


        // Récupérer le profil prestataire
        ProviderProfile profile = providerProfileRepository.findByUser(currentUser)
                .orElseThrow(() -> new RuntimeException("Profil prestataire introuvable"));

        // Mettre à jour les coordonnées GPS et l'horodatage
        profile.setGpsLatitude(dto.getLatitude());
        profile.setGpsLongitude(dto.getLongitude());
        profile.setGpsUpdatedAt(LocalDateTime.now());

        providerProfileRepository.save(profile);
    }

    // =========================================================================
    // PROFIL PRESTATAIRE
    // =========================================================================
    @Override
    public ProviderProfileDTO getMyProfile() {

        // 1. Récupérer le prestataire connecté via son JWT
        User currentProvider = getCurrentUser();

        // 2. Récupérer le profil prestataire associé à l'utilisateur
        ProviderProfile profile = providerProfileRepository.findByUser(currentProvider)
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // 3. Générer une presigned URL pour la photo de profil si elle existe
        String profilePhotoUrl = currentProvider.getProfilePhotoUrl();

        // 4. Construire et retourner le DTO avec les informations du profil
        return ProviderProfileDTO.builder()
                .companyName(profile.getCompanyName() != null ? profile.getCompanyName() : currentProvider.getFirstName() + " " + currentProvider.getLastName())
                .specialtyName(profile.getSpecialty() != null ? profile.getSpecialty().getName() : "N/A")
                .email(currentProvider.getEmail())
                .phone(currentProvider.getPhone())
                .language("Français, Wolof") // Statique
                .memberSince(currentProvider.getCreatedAt())
                .profilePhotoUrl(profilePhotoUrl)
                .build();
    }

    @Override
    public UpdateProviderProfileDTO getPersonalInformation() {

        // 1. Récupérer le prestataire connecté via son JWT
        User currentProvider = getCurrentUser();

        // 2. Récupérer le profil prestataire associé à l'utilisateur
        ProviderProfile profile = providerProfileRepository.findByUser(currentProvider)
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // 3. Construire et retourner le DTO avec les informations personnelles du prestataire
        return UpdateProviderProfileDTO.builder()
                .companyName(profile.getCompanyName())
                .firstName(currentProvider.getFirstName())
                .lastName(currentProvider.getLastName())
                .phone(currentProvider.getPhone())
                .email(currentProvider.getEmail())
                .profilePhotoUrl(currentProvider.getProfilePhotoUrl())
                .specialtyName(profile.getSpecialty() != null ? profile.getSpecialty().getName() : "N/A")
                .interventionZone(profile.getInterventionZone())
                .build();
    }

    @Override
    @Transactional
    public void updateProfile(UpdateProviderProfileDTO dto) {

        // 1. Récupérer le prestataire connecté via son JWT
        User currentProvider = getCurrentUser();

        // 2. Récupérer le profil prestataire associé à l'utilisateur
        ProviderProfile profile = providerProfileRepository.findByUser(currentProvider)
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // 3. Mettre à jour uniquement les champs modifiables directement
        if (dto.getCompanyName() != null) profile.setCompanyName(dto.getCompanyName());
        if (dto.getFirstName() != null) currentProvider.setFirstName(dto.getFirstName());
        if (dto.getLastName() != null) currentProvider.setLastName(dto.getLastName());

        // 4. Mettre à jour les champs avec vérification (phone, email)
        if (dto.getPhone() != null) {
            // Vérifier si le téléphone est déjà utilisé par un autre utilisateur
            if (userRepository.existsByPhoneAndIdNot(dto.getPhone(), currentProvider.getId())) {
                throw new RuntimeException("Ce numéro de téléphone est déjà utilisé par un autre compte.");
            }
            currentProvider.setPhone(dto.getPhone());
        }
        if (dto.getEmail() != null) {
            // Vérifier si l'email est déjà utilisé par un autre utilisateur
            if (userRepository.existsByEmailAndIdNot(dto.getEmail(), currentProvider.getId())) {
                throw new RuntimeException("Cet email est déjà utilisé par un autre compte.");
            }
            currentProvider.setEmail(dto.getEmail());
        }

        // 5. Sauvegarder les modifications
        userRepository.save(currentProvider);
        providerProfileRepository.save(profile);
    }
    // =========================================================================
    // Abonnement Prestataire
    // =========================================================================
    /**
     * Retourne l'abonnement actuel du prestataire + l'historique des paiements (paginé).
     */
    @Override
    public MySubscriptionDTO getMySubscription(Pageable pageable) {

        // 1. Récupérer le prestataire connecté via son JWT
        User currentProvider = getCurrentUser();

        // 2. Récupérer l'abonnement le plus récent (pour la carte actuelle)
        Optional<ProviderSubscription> latestSubscription = providerSubscriptionRepository
                .findFirstByProviderIdOrderByEndDateDesc(currentProvider.getId());

        // 3. Si aucun abonnement, retourner un DTO vide (pas d'abonnement)
        if (latestSubscription.isEmpty()) {
            return MySubscriptionDTO.builder()
                    .planName(null)
                    .active(false)
                    .status("Aucun")
                    .startDate(null)
                    .endDate(null)
                    .paymentMethod(null)
                    .paymentHistory(Page.empty())
                    .build();
        }

        // 4. Construire la carte de l'abonnement actuel
        ProviderSubscription currentSubscription = latestSubscription.get();

        // Nom du plan d'abonnement (ex: "Premium"), avec fallback si null
        String planName = currentSubscription.getProviderPlan() != null
                ? currentSubscription.getProviderPlan().getName()
                : "Inconnu";

        // Vérifie si l'abonnement est actuellement valide (statut actif ET date non expirée)
        boolean isActive = currentSubscription.isCurrentlyActive();

        // Affiche "Actif" si valide, sinon le libellé du statut
        String status = isActive ? "Actif" : currentSubscription.getStatus().getLabel();

        // Méthode de paiement utilisée (Wave, Orange Money, etc.), avec label lisible
        String paymentMethod = currentSubscription.getMethod() != null
                ? currentSubscription.getMethod().getLabel()
                : null;

        // 5. Récupérer l'historique des paiements (paginé)
        Page<ProviderSubscription> subscriptionsPage = providerSubscriptionRepository
                .findByProviderIdOrderByStartDateDesc(currentProvider.getId(), pageable);

        // 6. Construire la page d'historique
        Page<SubscriptionHistoryItemDTO> historyPage = subscriptionsPage.map(sub ->
                SubscriptionHistoryItemDTO.builder()
                        .planName(sub.getProviderPlan() != null ? sub.getProviderPlan().getName() : "Inconnu")
                        .status(sub.getStatus().getLabel())
                        .reference(sub.getTransactionRef())
                        .amount(sub.getAmountPaid())
                        .paymentMethod(sub.getMethod() != null ? sub.getMethod().getLabel() : null)
                        .date(sub.getStartDate())
                        .build());

        // 7. Retourner le DTO complet
        return MySubscriptionDTO.builder()
                .planName(planName)
                .active(isActive)
                .status(status)
                .startDate(currentSubscription.getStartDate())
                .endDate(currentSubscription.getEndDate())
                .paymentMethod(paymentMethod)
                .paymentHistory(historyPage)
                .build();
    }

    // =========================================================================
    // Devis Prestataire
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

    @Override
    @Transactional(readOnly = true)
    public QuoteDetailDTO getQuoteDetails(Long quoteId) {

        // 1. Récupérer le prestataire connecté
        User currentProvider = getCurrentUser();

        // 2. Récupérer le devis uniquement s'il appartient à ce prestataire
        Quote quote = quoteRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("Devis introuvable"));

        if (!quote.getProvider().getId().equals(currentProvider.getId())) {
            throw new ResourceNotFoundException("Vous n'êtes pas autorisé à consulter ce devis");
        }

        // 3. Récupérer la demande d'intervention associée
        InterventionRequest request = quote.getInterventionRequest();

        // 4. Déterminer le client selon le mode de gestion
        User client = request.getManagementMode() == InterventionManagementMode.SYNDIC
                ? request.getSyndic()
                : request.getOwner();

        // 5. Déterminer l'adresse (toujours l'adresse de la résidence)
        String clientAdresse = null;
        if (request.getResidence() != null) {
            clientAdresse = request.getResidence().getFullAddress();
        }

        // 6. Séparer les items par type (matériel vs main d'œuvre)
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

        // 7. Construire et retourner le DTO
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

    // ============================================================
    // Méthodes Utilitaires
    // ============================================================

    // Récupère l'utilisateur actuellement authentifié via le contexte de sécurité Spring
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }


}
