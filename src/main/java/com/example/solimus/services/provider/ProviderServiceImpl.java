package com.example.solimus.services.provider;

import com.example.solimus.dtos.provider.*;
import com.example.solimus.dtos.provider.profile.ProviderProfileDTO;
import com.example.solimus.dtos.provider.profile.UpdateProviderProfileDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;
import com.example.solimus.dtos.provider.wallet.WalletTransactionDTO;
import com.example.solimus.entities.*;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ResourceNotFoundException;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.data.domain.Page;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
    private final PasswordEncoder passwordEncoder;
    private final com.example.solimus.repositories.ProviderProfileRepository providerProfileRepository;




    // =========================================================================





    /**
     * Récupère les données consolidées du tableau de bord pour le prestataire connecté.
     * Calcule les compteurs principaux, les montants financiers en cours et les tendances d'évolution mensuelle.
     */
    @Override
    public ProviderDashboardDTO getDashboard() {
        // Étape 1 : Récupérer le prestataire connecté
        User currentProvider = getCurrentUser();
        Long providerId = currentProvider.getId();
        com.example.solimus.entities.ProviderProfile profile = providerProfileRepository.findByUser(currentProvider)
                .orElseThrow(() -> new ResourceNotFoundException("Profil prestataire introuvable"));

        // Étape 2 : Récupérer les informations d'identité
        String companyName = profile.getCompanyName() != null ? profile.getCompanyName() : "Prestataire";
        String role = "Prestataire";
        String profilePhotoUrl = currentProvider.getProfilePhotoUrl();

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
        int pendingMissionsCount = interventionRepository.countBySelectedProviderIdAndStatus(providerId, InterventionStatus.QUOTE_VALIDATED);

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
                .profilePhotoUrl(profilePhotoUrl)               // URL de la photo de profil
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



    // =========================================================================
    // PARAMÈTRES DU COMPTE
    // =========================================================================

    @Override
    @Transactional
    public void changePassword(String currentPassword, String newPassword) {
        User currentProvider = getCurrentUser();

        // Vérifier que le mot de passe actuel est correct
        if (!passwordEncoder.matches(currentPassword, currentProvider.getPassword())) {
            throw new BadRequestException("Le mot de passe actuel est incorrect.");
        }

        // Vérifier que le nouveau mot de passe est différent
        if (passwordEncoder.matches(newPassword, currentProvider.getPassword())) {
            throw new BadRequestException("Le nouveau mot de passe doit être différent de l'actuel.");
        }

        // Encoder et mettre à jour le nouveau mot de passe
        currentProvider.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(currentProvider);
        log.info("Mot de passe changé avec succès pour le prestataire : {}", currentProvider.getEmail());
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
