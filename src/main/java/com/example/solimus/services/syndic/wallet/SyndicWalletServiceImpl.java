package com.example.solimus.services.syndic.wallet;

import com.example.solimus.dtos.syndic.wallet.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.enums.WithdrawalMode;
import com.example.solimus.enums.WithdrawalStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SyndicWalletServiceImpl implements WalletService {

    private final ResidenceRepository residenceRepository;
    private final BudgetItemRepository budgetItemRepository;
    private final UserRepository userRepository;
    private final SyndicWalletRepository syndicWalletRepository;
    private final SyndicWithdrawalRequestRepository syndicWithdrawalRequestRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;
    private final PropertyRepository propertyRepository;
    private final BudgetRepository budgetRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ResidenceSimpleDTO> getSyndicResidences() {
        User currentSyndic = getCurrentUser();
        List<Residence> residences = residenceRepository.findBySyndicId(currentSyndic.getId());
        return residences.stream()
                .map(r -> ResidenceSimpleDTO.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<BudgetItemSimpleDTO> getBudgetItemsWithoutCommonFacility(Long residenceId) {
        int currentYear = LocalDate.now().getYear();
        List<BudgetItem> budgetItems = budgetItemRepository.findByResidenceIdAndYearAndCommonFacilityIsNull(residenceId, currentYear);
        return budgetItems.stream()
                .map(item -> BudgetItemSimpleDTO.builder()
                        .id(item.getId())
                        .libelle(item.getLibelle())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void createWithdrawalRequest(CreateWithdrawalRequestDTO dto) {
        User currentSyndic = getCurrentUser();

        // Récupérer ou créer le wallet du syndic
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        // Récupérer la résidence
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new BadRequestException("Résidence non trouvée"));

        // Vérifier que la résidence appartient au syndic
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à effectuer un retrait pour cette résidence");
        }

        // Si budgetItemId est fourni, récupérer et vérifier le poste budgétaire
        BudgetItem budgetItem = null;
        if (dto.getBudgetItemId() != null) {
            budgetItem = budgetItemRepository.findById(dto.getBudgetItemId())
                    .orElseThrow(() -> new BadRequestException("Poste budgétaire non trouvé"));

            // Vérifier que le poste n'est pas lié à un bien commun
            // Les postes liés à des équipements (ex: Ascenseur, Jardin) doivent être gérés via le module Travaux
            // Les demandes de retrait ne sont autorisées que pour les postes libres (ex: Assurance, Nettoyage)
            if (budgetItem.getCommonFacility() != null) {
                throw new BadRequestException("Ce poste budgétaire est lié à un bien commun. Utilisez le module Travaux pour les dépenses liées aux équipements.");
            }

            // Vérifier que le poste appartient à la résidence spécifiée
            if (!budgetItem.getBudget().getResidence().getId().equals(dto.getResidenceId())) {
                throw new BadRequestException("Ce poste budgétaire n'appartient pas à la résidence spécifiée");
            }
        }

        // Créer la demande de retrait
        SyndicWithdrawalRequest request = SyndicWithdrawalRequest.builder()
                .wallet(wallet)
                .amount(dto.getAmount())
                .mode(dto.getMode())
                .residence(residence)
                .budgetItem(budgetItem)
                .accountNumber(dto.getAccountNumber())
                .reason(dto.getReason())
                .status(WithdrawalStatus.PENDING)
                .build();

        syndicWithdrawalRequestRepository.save(request);
    }

    @Override
    @Transactional(readOnly = true)
    public WalletBalanceDTO getWalletBalance() {
        User currentSyndic = getCurrentUser();

        // Récupère ou crée le wallet du syndic
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        LocalDateTime now = LocalDateTime.now();

        // Solde brut = somme de toutes les transactions du wallet jusqu'à maintenant
        BigDecimal totalTransactions = calculerSoldeADate(wallet.getId(), now);

        // Retraits déjà réservés (demandés PENDING ou validés COMPLETED) : cet argent
        // ne doit plus être considéré comme disponible
        BigDecimal retraitsReserves = syndicWithdrawalRequestRepository
                .sumPendingAndValidatedByWalletAndResidence(wallet.getId(), null);

        // Solde réellement disponible = transactions - retraits réservés
        BigDecimal soldeDisponible = totalTransactions.subtract(retraitsReserves);

        return WalletBalanceDTO.builder()
                .soldeDisponible(soldeDisponible)
                .build();
    }

    // =========================================================================
    // KPIs du portefeuille financier (Vue d'ensemble)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WalletKpiDTO getWalletKpis(Long residenceId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère ou crée le wallet du syndic
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        // Si une résidence est demandée en filtre, vérifie qu'elle appartient bien à ce syndic
        if (residenceId != null) {
            Residence residence = residenceRepository.findById(residenceId)
                    .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));
            if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
                throw new ForbiddenException("Cette résidence ne vous appartient pas");
            }
        }

        // Date de référence pour tous les calculs de ce KPI
        LocalDateTime now = LocalDateTime.now();

        // ===== 1. SOLDE DISPONIBLE + VARIATION =====

        // Calcule l'instant précis correspondant à la fin du mois précédent
        // (le tout dernier moment avant le 1er jour du mois en cours)
        LocalDateTime finMoisPrecedent = now.withDayOfMonth(1).minusNanos(1);

        // Solde actuel (brut) : si un filtre résidence est actif, on utilise la méthode filtrée par résidence,
        // sinon on utilise la méthode globale déjà existante (calculerSoldeADate)
        BigDecimal transactionsActuelles = (residenceId != null)
                ? syndicWalletTransactionRepository.sumAllByResidenceId(residenceId, now)
                : calculerSoldeADate(wallet.getId(), now);

        // Retraits déjà réservés (PENDING + COMPLETED), optionnellement filtrés par résidence
        BigDecimal retraitsReserves = syndicWithdrawalRequestRepository
                .sumPendingAndValidatedByWalletAndResidence(wallet.getId(), residenceId);

        // Solde réellement disponible = transactions - retraits réservés
        BigDecimal soldeActuel = transactionsActuelles.subtract(retraitsReserves);

        // Même logique de transactions, mais calculée à la date de fin du mois précédent, pour la variation
        BigDecimal soldePrecedent = (residenceId != null)
                ? syndicWalletTransactionRepository.sumAllByResidenceId(residenceId, finMoisPrecedent)
                : calculerSoldeADate(wallet.getId(), finMoisPrecedent);

        // La variation compare uniquement les flux de transactions (sans les retraits réservés)
        BigDecimal variation = calculerVariation(transactionsActuelles, soldePrecedent);

        // ===== 2. CHARGES COLLECTÉES (trimestre en cours) =====

        // Calcule la date de début du trimestre actuel
        LocalDateTime startOfQuarter = getStartOfCurrentQuarter(now);

        // Additionne toutes les transactions de catégorie CHARGES depuis le début du trimestre jusqu'à maintenant
        BigDecimal chargesCollectees = syndicWalletTransactionRepository.sumAmountByCategoryAndPeriod(
                wallet.getId(), WalletTransactionCategory.CHARGES, startOfQuarter, now, residenceId);

        // ===== 3. PAIEMENT PRESTATAIRES (depuis toujours, pas de limite de période) =====

        // Additionne toutes les transactions de catégorie TRAVAUX, sans limite de date
        // .abs() car ces montants sont stockés en négatif (dépense), on veut afficher une valeur positive
        BigDecimal paiementPrestataires = syndicWalletTransactionRepository.sumAmountByCategory(
                wallet.getId(), WalletTransactionCategory.TRAVAUX, residenceId).abs();

        // Compte le nombre total de transactions TRAVAUX, pour le sous-texte "X facture"
        long paiementPrestatairesCount = syndicWalletTransactionRepository.countByCategory(
                wallet.getId(), WalletTransactionCategory.TRAVAUX, residenceId);

        // ===== 4. RETRAITS EN ATTENTE (mois en cours) =====

        // Calcule le premier instant du mois en cours
        LocalDateTime startOfMonth = now.withDayOfMonth(1).toLocalDate().atStartOfDay();

        // Additionne les demandes de retrait au statut PENDING, faites ce mois-ci
        BigDecimal retraitsEnAttente = syndicWithdrawalRequestRepository.sumPendingAmountByPeriod(
                wallet.getId(), startOfMonth, now, residenceId);

        // Construit et retourne le DTO complet avec les 4 KPIs
        return WalletKpiDTO.builder()
                .soldeDisponible(soldeActuel)
                .soldeVariationPercent(variation)
                .chargesCollectees(chargesCollectees)
                .paiementPrestataires(paiementPrestataires)
                .paiementPrestatairesCount(paiementPrestatairesCount)
                .retraitsEnAttente(retraitsEnAttente)
                .build();
    }

    // =========================================================================
    // Graphique Recettes vs Dépenses (6 derniers mois glissants)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WalletChartDTO getWalletChart(Long residenceId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        LocalDateTime now = LocalDateTime.now();
        // Point de départ : 6 mois glissants avant aujourd'hui, au 1er jour de ce mois-là
        LocalDateTime startDate = now.minusMonths(5).withDayOfMonth(1).toLocalDate().atStartOfDay();

        // Récupère les sommes mensuelles pour CHARGES
        List<Object[]> chargesRows = syndicWalletTransactionRepository.sumMonthlyByCategory(
                wallet.getId(), "CHARGES", startDate, residenceId);

        // Récupère les sommes mensuelles pour TRAVAUX
        List<Object[]> travauxRows = syndicWalletTransactionRepository.sumMonthlyByCategory(
                wallet.getId(), "TRAVAUX", startDate, residenceId);

        // Récupère les sommes mensuelles pour RETRAIT
        List<Object[]> retraitRows = syndicWalletTransactionRepository.sumMonthlyByCategory(
                wallet.getId(), "RETRAIT", startDate, residenceId);

        // Construit la liste des 6 mois glissants, avec leur clé "yyyy-MM" et leur libellé affiché
        List<WalletChartPeriodDTO> periods = new ArrayList<>();
        DateTimeFormatter keyFormatter = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM", Locale.FRENCH);

        // Parcourt les 6 derniers mois, du plus ancien (i=5) au plus récent (i=0)
        for (int i = 5; i >= 0; i--) {

            // Calcule le 1er jour du mois correspondant à cette position
            LocalDate monthDate = now.minusMonths(i).toLocalDate().withDayOfMonth(1);
            String periodKey = monthDate.format(keyFormatter);
            String label = monthDate.format(labelFormatter).toUpperCase();

            // Cherche la somme CHARGES correspondant à ce mois précis
            BigDecimal recettesCharges = findAmountForPeriod(chargesRows, periodKey);

            // Cherche les sommes TRAVAUX et RETRAIT pour ce même mois, puis les additionne ensemble
            BigDecimal depensesTravaux = findAmountForPeriod(travauxRows, periodKey);
            BigDecimal depensesRetrait = findAmountForPeriod(retraitRows, periodKey);
            // Valeur absolue car ces montants sont stockés en négatif (dépenses)
            BigDecimal depensesPrestataires = depensesTravaux.add(depensesRetrait).abs();

            // Construit la ligne de ce mois et l'ajoute à la liste finale
            periods.add(WalletChartPeriodDTO.builder()
                    .label(label)
                    .recettesCharges(recettesCharges)
                    .depensesPrestataires(depensesPrestataires)
                    .build());
        }

        return WalletChartDTO.builder()
                .periods(periods)
                .build();
    }

    // =========================================================================
    // Graphique Recettes vs Dépenses (4 trimestres de l'année civile en cours)
    // Affiche toujours T1, T2, T3, T4 de l'année en cours, même les trimestres
    // pas encore arrivés (qui afficheront 0)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WalletChartDTO getWalletChartQuarterly(Long residenceId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        LocalDateTime now = LocalDateTime.now();
        int currentYear = now.getYear();

        // Point de départ : le 1er janvier de l'année en cours
        LocalDateTime startDate = LocalDate.of(currentYear, 1, 1).atStartOfDay();

        // Récupère les sommes trimestrielles pour CHARGES
        List<Object[]> chargesRows = syndicWalletTransactionRepository.sumQuarterlyByCategory(
                wallet.getId(), "CHARGES", startDate, residenceId);

        // Récupère les sommes trimestrielles pour TRAVAUX
        List<Object[]> travauxRows = syndicWalletTransactionRepository.sumQuarterlyByCategory(
                wallet.getId(), "TRAVAUX", startDate, residenceId);

        // Récupère les sommes trimestrielles pour RETRAIT
        List<Object[]> retraitRows = syndicWalletTransactionRepository.sumQuarterlyByCategory(
                wallet.getId(), "RETRAIT", startDate, residenceId);

        // Construit toujours les 4 trimestres de l'année en cours, même ceux pas encore arrivés
        List<WalletChartPeriodDTO> periods = new ArrayList<>();

        // Parcourt les 4 trimestres dans l'ordre, de T1 à T4
        for (int quarterNumber = 1; quarterNumber <= 4; quarterNumber++) {

            // Construit la clé exacte du trimestre, au même format que la requête SQL (ex: "2026-Q1")
            String periodKey = currentYear + "-Q" + quarterNumber;
            // Libellé affiché (ex: "T1 2026")
            String label = "T" + quarterNumber + " " + currentYear;

            // Cherche la somme CHARGES correspondant à ce trimestre précis
            BigDecimal recettesCharges = findAmountForPeriod(chargesRows, periodKey);

            // Cherche les sommes TRAVAUX et RETRAIT pour ce même trimestre, puis les additionne ensemble
            BigDecimal depensesTravaux = findAmountForPeriod(travauxRows, periodKey);
            BigDecimal depensesRetrait = findAmountForPeriod(retraitRows, periodKey);
            // Valeur absolue car ces montants sont stockés en négatif (dépenses)
            BigDecimal depensesPrestataires = depensesTravaux.add(depensesRetrait).abs();

            // Construit la ligne de ce trimestre et l'ajoute à la liste finale
            periods.add(WalletChartPeriodDTO.builder()
                    .label(label)
                    .recettesCharges(recettesCharges)
                    .depensesPrestataires(depensesPrestataires)
                    .build());
        }

        return WalletChartDTO.builder()
                .periods(periods)
                .build();
    }

    // =========================================================================
    // Aperçu des 4 résidences les plus récemment actives (widget Vue d'ensemble Wallet)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WalletResidencesOverviewResponseDTO getWalletResidencesOverview() {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Limite à 4 résultats via la pagination (page 0, taille 4)
        Pageable pageable = PageRequest.of(0, 4);
        List<Residence> residences = residenceRepository.findMostRecentlyActiveResidences(currentSyndic.getId(), pageable);

        List<WalletResidenceOverviewDTO> dtos = new ArrayList<>();
        int currentYear = Year.now().getValue();

        // Construit une ligne pour chaque résidence trouvée
        for (Residence residence : residences) {

            // Nombre d'appartements de cette résidence
            int apartmentsCount = (int) propertyRepository.countByResidenceId(residence.getId());

            // Budget de l'année en cours pour cette résidence
            var budgetOpt = budgetRepository.findByResidenceIdAndAnnee(residence.getId(), currentYear);

            double collectionPercentage = 0.0;

            // On ne calcule le pourcentage que si un budget existe et qu'il est supérieur à zéro
            if (budgetOpt.isPresent() && budgetOpt.get().getBudgetTotal().compareTo(BigDecimal.ZERO) > 0) {

                // Charges collectées cette année pour cette résidence (via les transactions wallet)
                LocalDateTime startOfYear = LocalDate.of(currentYear, 1, 1).atStartOfDay();
                LocalDateTime now = LocalDateTime.now();

                SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                        .orElseGet(() -> {
                            SyndicWallet newWallet = SyndicWallet.builder()
                                    .syndic(currentSyndic)
                                    .build();
                            return syndicWalletRepository.save(newWallet);
                        });

                BigDecimal chargesCollectees = syndicWalletTransactionRepository.sumAmountByCategoryAndPeriod(
                        wallet.getId(), WalletTransactionCategory.CHARGES, startOfYear, now, residence.getId());

                // Formule : (charges collectées / budget total) x 100
                collectionPercentage = chargesCollectees
                        .divide(budgetOpt.get().getBudgetTotal(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue();
            }

            dtos.add(WalletResidenceOverviewDTO.builder()
                    .id(residence.getId())
                    .name(residence.getName())
                    .photoUrl(residence.getPhotoUrl())
                    .apartmentsCount(apartmentsCount)
                    .collectionPercentage(collectionPercentage)
                    .build());
        }

        return WalletResidencesOverviewResponseDTO.builder()
                .residences(dtos)
                .build();
    }

    // =========================================================================
   // Aperçu "Derniers flux" (5 dernières transactions CHARGES + TRAVAUX, Vue d'ensemble)
   // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WalletFlowOverviewResponseDTO getWalletFlowsOverview(Long residenceId) {

        User currentSyndic = getCurrentUser();
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        // Limite à 5 résultats via la pagination (page 0, taille 5)
        // Transactions CHARGES et TRAVAUX uniquement (exclut RETRAIT), triées par date décroissante,
        // optionnellement filtré par residence. Utilisée pour le tableau "Derniers flux"
        Pageable pageable = PageRequest.of(0, 5);
        Page<SyndicWalletTransaction> transactionPage = syndicWalletTransactionRepository.findFlowsByWallet(
                wallet.getId(), residenceId, pageable);

        List<WalletFlowRowDTO> rows = new ArrayList<>();

        for (SyndicWalletTransaction transaction : transactionPage.getContent()) {

            String statut = transaction.getAmount().compareTo(BigDecimal.ZERO) < 0 ? "PAYÉ" : "REÇU";
            String categoryLabel = transaction.getCategory() == WalletTransactionCategory.CHARGES ? "Charges" : "Travaux";

            rows.add(WalletFlowRowDTO.builder()
                    .date(transaction.getTransactionDate())
                    .residenceName(transaction.getResidence() != null ? transaction.getResidence().getName() : null)
                    .beneficiaryName(transaction.getBeneficiaryName())
                    .category(transaction.getCategory().name())
                    .categoryLabel(categoryLabel)
                    .statut(statut)
                    .amount(transaction.getAmount())
                    .build());
        }

        return WalletFlowOverviewResponseDTO.builder()
                .flows(rows)
                .build();
    }

    // =========================================================================
    // Historique complet des transactions, paginé (onglet Transactions)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WalletFlowListResponseDTO getWalletFlows(Long residenceId, int page, int size) {

        User currentSyndic = getCurrentUser();
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        Pageable pageable = PageRequest.of(page, size);
        Page<SyndicWalletTransaction> transactionPage = syndicWalletTransactionRepository.findFlowsByWallet(
                wallet.getId(), residenceId, pageable);

        List<WalletFlowRowDTO> rows = new ArrayList<>();

        for (SyndicWalletTransaction transaction : transactionPage.getContent()) {

            String statut = transaction.getAmount().compareTo(BigDecimal.ZERO) < 0 ? "PAYÉ" : "REÇU";
            String categoryLabel = transaction.getCategory() == WalletTransactionCategory.CHARGES ? "Charges" : "Travaux";

            rows.add(WalletFlowRowDTO.builder()
                    .date(transaction.getTransactionDate())
                    .residenceName(transaction.getResidence() != null ? transaction.getResidence().getName() : null)
                    .beneficiaryName(transaction.getBeneficiaryName())
                    .category(transaction.getCategory().name())
                    .categoryLabel(categoryLabel)
                    .statut(statut)
                    .amount(transaction.getAmount())
                    .build());
        }

        return WalletFlowListResponseDTO.builder()
                .totalCount(transactionPage.getTotalElements())
                .flows(rows)
                .currentPage(transactionPage.getNumber())
                .totalPages(transactionPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // KPIs de l'onglet Retraits
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WithdrawalKpiDTO getWithdrawalKpis(Long residenceId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        LocalDateTime now = LocalDateTime.now();

        // Transactions brutes (charges reçues + travaux payés)
        BigDecimal transactionsActuelles = (residenceId != null)
                ? syndicWalletTransactionRepository.sumAllByResidenceId(residenceId, now)
                : calculerSoldeADate(wallet.getId(), now);

        // Retraits réservés (PENDING + COMPLETED) : déjà engagés, plus disponibles
        BigDecimal retraitsReserves = syndicWithdrawalRequestRepository
                .sumPendingAndValidatedByWalletAndResidence(wallet.getId(), residenceId);

        // Solde réellement disponible = transactions - retraits réservés
        BigDecimal soldeDisponible = transactionsActuelles.subtract(retraitsReserves);

        // Somme de toutes les demandes de retrait encore en attente de validation
        BigDecimal enAttente = syndicWithdrawalRequestRepository.sumPendingAmount(wallet.getId(), residenceId);

        // Somme de tous les retraits déjà validés et effectués, depuis toujours
        BigDecimal retraitsTotaux = syndicWithdrawalRequestRepository.sumCompletedAmount(wallet.getId(), residenceId);

        // Construit et retourne le DTO complet avec les 3 KPIs
        return WithdrawalKpiDTO.builder()
                .soldeDisponible(soldeDisponible)
                .enAttente(enAttente)
                .retraitsTotaux(retraitsTotaux)
                .build();
    }

    // =========================================================================
    // Historique paginé des demandes de retrait
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WithdrawalListResponseDTO getWithdrawalsList(Long residenceId, int page, int size) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();
        SyndicWallet wallet = syndicWalletRepository.findBySyndicId(currentSyndic.getId())
                .orElseGet(() -> {
                    SyndicWallet newWallet = SyndicWallet.builder()
                            .syndic(currentSyndic)
                            .build();
                    return syndicWalletRepository.save(newWallet);
                });

        // Prépare la pagination
        Pageable pageable = PageRequest.of(page, size);

        // Récupère la page de demandes de retrait correspondantes
        Page<SyndicWithdrawalRequest> withdrawalPage = syndicWithdrawalRequestRepository.findByWalletId(
                wallet.getId(), residenceId, pageable);

        List<WithdrawalRowDTO> rows = new ArrayList<>();

        // Construit une ligne pour chaque demande de retrait trouvée
        for (SyndicWithdrawalRequest withdrawal : withdrawalPage.getContent()) {
            rows.add(WithdrawalRowDTO.builder()
                    .id(withdrawal.getId())
                    .date(withdrawal.getRequestedAt())
                    .amount(withdrawal.getAmount())
                    .mode(withdrawal.getMode())
                    .modeLabel(getModeLabel(withdrawal.getMode()))
                    .status(withdrawal.getStatus())
                    .statusLabel(getStatusLabel(withdrawal.getStatus()))
                    .build());
        }

        // Construit la réponse finale : retraits de la page + infos de pagination
        return WithdrawalListResponseDTO.builder()
                .totalCount(withdrawalPage.getTotalElements())
                .withdrawals(rows)
                .currentPage(withdrawalPage.getNumber())
                .totalPages(withdrawalPage.getTotalPages())
                .build();
    }

    // =========================================================================
    // Détail complet d'une demande de retrait, avec sa timeline de progression
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public WithdrawalDetailDTO getWithdrawalDetail(Long withdrawalId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère la demande de retrait, erreur si introuvable
        SyndicWithdrawalRequest withdrawal = syndicWithdrawalRequestRepository.findById(withdrawalId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de retrait introuvable"));

        // Vérifie que ce retrait appartient bien au wallet de ce syndic
        if (!withdrawal.getWallet().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cette demande de retrait ne vous appartient pas");
        }

        // Construit et retourne le DTO complet, avec sa timeline de progression
        return WithdrawalDetailDTO.builder()
                .id(withdrawal.getId())
                .amount(withdrawal.getAmount())
                .requestedAt(withdrawal.getRequestedAt())
                .mode(withdrawal.getMode())
                .modeLabel(getModeLabel(withdrawal.getMode()))
                .accountNumber(withdrawal.getAccountNumber())
                .residenceName(withdrawal.getResidence() != null ? withdrawal.getResidence().getName() : null)
                .budgetItemLabel(withdrawal.getBudgetItem() != null ? withdrawal.getBudgetItem().getLibelle() : null)
                .status(withdrawal.getStatus())
                .statusLabel(getStatusLabel(withdrawal.getStatus()))
                .progressSteps(buildProgressSteps(withdrawal))
                .build();
    }


    // ============================================================
    // MÉTHODES UTILITAIRES
    // ============================================================

    // Cherche, dans la liste des résultats bruts, le montant correspondant à un mois précis
    private BigDecimal findAmountForPeriod(List<Object[]> rows, String periodKey) {

        // Parcourt chaque ligne de résultat, une par une
        for (Object[] row : rows) {

            // Récupère le mois de cette ligne (ex: "2026-03")
            String rowKey = (String) row[0];

            // Si ce mois correspond à celui qu'on cherche, on retourne son montant
            if (rowKey.equals(periodKey)) {
                return (BigDecimal) row[1];
            }
        }

        // Aucune transaction trouvée pour ce mois-là
        return BigDecimal.ZERO;
    }

    private BigDecimal calculerSoldeADate(Long walletId, LocalDateTime asOfDate) {
        if (walletId == null) return BigDecimal.ZERO;
        return syndicWalletTransactionRepository.sumTransactionsUpTo(walletId, asOfDate);
    }

    private BigDecimal calculerVariation(BigDecimal actuel, BigDecimal precedent) {
        if (precedent.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return actuel.subtract(precedent)
                .divide(precedent, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // Calcule la date de début du trimestre en cours
    private LocalDateTime getStartOfCurrentQuarter(LocalDateTime now) {

        // Récupère le numéro du mois actuel (1 à 12)
        int currentMonth = now.getMonthValue();

        // Calcule le mois de départ du trimestre correspondant
        // Ex: mois 5 (mai) -> trimestre commence au mois 4 (avril)
        int quarterStartMonth = ((currentMonth - 1) / 3) * 3 + 1;

        // Construit la date du 1er jour de ce mois, à minuit
        return now.withMonth(quarterStartMonth).withDayOfMonth(1).toLocalDate().atStartOfDay();
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    // Construit la timeline "Progression de la demande" selon le statut actuel
    private List<WithdrawalProgressStepDTO> buildProgressSteps(SyndicWithdrawalRequest withdrawal) {

        List<WithdrawalProgressStepDTO> steps = new ArrayList<>();

        // Étape 1 : "Demande créée" — toujours terminée
        steps.add(WithdrawalProgressStepDTO.builder()
                .label("Demande créée")
                .state("DONE")
                .date(withdrawal.getRequestedAt())
                .durationLabel(null)
                .build());

        // Étape 2 : "En attente de validation"
        boolean step2Completed = withdrawal.getStatus() != WithdrawalStatus.PENDING;
        steps.add(WithdrawalProgressStepDTO.builder()
                .label("En attente de validation")
                .state(step2Completed ? "DONE" : "CURRENT")
                .date(null)
                .durationLabel(!step2Completed ? calculateDurationSince(withdrawal.getRequestedAt()) : null)
                .build());

        // Étape 3 : "Validée" — un seul bloc, quel que soit le statut final
        // Terminée uniquement si COMPLETED, grisée dans tous les autres cas (PENDING ou REJECTED)
        boolean step3Completed = withdrawal.getStatus() == WithdrawalStatus.COMPLETED;
        steps.add(WithdrawalProgressStepDTO.builder()
                .label("Validée")
                .state(step3Completed ? "DONE" : "PENDING")
                .date(step3Completed ? withdrawal.getProcessedAt() : null)
                .durationLabel(null)
                .build());

        // Étape 4 : "Refusée" — ajoutée UNIQUEMENT si le statut est REJECTED
        if (withdrawal.getStatus() == WithdrawalStatus.REJECTED) {
            steps.add(WithdrawalProgressStepDTO.builder()
                    .label("Refusée")
                    .state("REJECTED")
                    .date(withdrawal.getProcessedAt())
                    .durationLabel(null)
                    .build());
        }

        return steps;
    }

    // Calcule le texte de durée écoulée depuis une date donnée, format "En cours depuis Xh" ou "Xj"
    private String calculateDurationSince(LocalDateTime since) {

        Duration duration = Duration.between(since, LocalDateTime.now());

        long days = duration.toDays();
        if (days > 0) {
            return "En cours depuis " + days + (days == 1 ? " jour" : " jours");
        }

        long hours = duration.toHours();
        if (hours > 0) {
            return "En cours depuis " + hours + "h";
        }

        long minutes = duration.toMinutes();
        return "En cours depuis " + minutes + " min";
    }

    // Traduit le mode de retrait en libellé affichable
    private String getModeLabel(WithdrawalMode mode) {
        return switch (mode) {
            case VIREMENT -> "Virement Bancaire";
            case WAVE -> "Wave";
            case ORANGE_MONEY -> "Orange Money";
        };
    }

    // Traduit le statut en libellé affichable
    private String getStatusLabel(WithdrawalStatus status) {
        return switch (status) {
            case PENDING -> "En attente";
            case COMPLETED -> "Validé";
            case REJECTED -> "Refusé";
        };
    }



}
