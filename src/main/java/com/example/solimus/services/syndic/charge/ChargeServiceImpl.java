package com.example.solimus.services.syndic.charge;

import com.example.solimus.dtos.charge.*;
import com.example.solimus.dtos.syndic.charge.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.BudgetStatus;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.ExceptionalCallCategory;
import com.example.solimus.enums.ExceptionalCallStatus;
import com.example.solimus.enums.RepartitionMode;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChargeServiceImpl implements ChargeService {

    private final BudgetRepository budgetRepository;
    private final ResidenceRepository residenceRepository;
    private final PropertyRepository propertyRepository;
    private final SyndicFinancialSettingsRepository syndicFinancialSettingsRepository;
    private final UserRepository userRepository;
    private final ChargeCallRepository chargeCallRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final ExceptionalCallRepository exceptionalCallRepository;
    private final MinioService minioService;
    private final CommonFacilityRepository commonFacilityRepository;
    private final SyndicWalletTransactionRepository syndicWalletTransactionRepository;

    // ============================================================
    // 1. ÉTAPE 1  — APERÇU RÉSIDENCE
    // ============================================================

    @Override
    public BudgetResidencePreviewDTO getResidencePreview(Long residenceId) {
        // 1.1 Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new RuntimeException("Résidence non trouvée"));

        // 1.1.1 Vérifier que la résidence appartient au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // 1.2 Récupérer toutes les propriétés de cette résidence
        List<Property> properties = propertyRepository.findByResidenceId(residenceId);

        // 1.3 Construire la liste des copropriétaires avec leurs tantièmes
        List<CoOwnerTantiemePreviewDTO> coOwners = new ArrayList<>();
        BigDecimal totalTantieme = BigDecimal.ZERO;

        for (Property property : properties) {
            if (property.getOwner() != null) {
                // 1.3.1 Calculer total tantième du propriétaire
                BigDecimal tantieme = property.getTantieme() != null ? property.getTantieme() : BigDecimal.ZERO;
                totalTantieme = totalTantieme.add(tantieme); //ici, on calcule le total des tantièmes de toute la résidence.

                // 1.3.2 Vérifier si le copropriétaire est déjà dans la liste
                CoOwnerTantiemePreviewDTO existingCoOwner = coOwners.stream()
                        .filter(co -> co.getCoOwnerId().equals(property.getOwner().getId()))
                        .findFirst()
                        .orElse(null);

                if (existingCoOwner != null) {
                    // 1.3.3 Ajouter la référence de propriété au copropriétaire existant
                    existingCoOwner.getPropertyReferences().add(property.getReference());
                    existingCoOwner.setTantieme(existingCoOwner.getTantieme().add(tantieme));
                } else {
                    // 1.3.4 Créer un nouveau copropriétaire
                    CoOwnerTantiemePreviewDTO newCoOwner = CoOwnerTantiemePreviewDTO.builder()
                            .coOwnerId(property.getOwner().getId())
                            .coOwnerName(property.getOwner().getFirstName() + " " + property.getOwner().getLastName())
                            .tantieme(tantieme)
                            .propertyReferences(new ArrayList<>(List.of(property.getReference())))
                            .build();
                    coOwners.add(newCoOwner);
                }
            }
        }

        // 1.4 Construire et retourner l'aperçu
        return BudgetResidencePreviewDTO.builder()
                .residenceId(residence.getId())
                .residenceName(residence.getName())
                .totalProperties(properties.size())
                .totalTantieme(totalTantieme)
                .coOwners(coOwners)
                .build();
    }
    

    @Override
    @Transactional
    public BudgetDetailDTO createBudget(CreateBudgetDTO dto) {
        // ------------------------------------------------------------
        // ÉTAPE 2.1 — Récupérer la résidence
        // ------------------------------------------------------------
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new RuntimeException("Résidence non trouvée"));

        // ------------------------------------------------------------
        // ÉTAPE 2.2 — Vérifier l'autorisation du syndic
        // ------------------------------------------------------------
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à créer un budget pour cette résidence");
        }

        // ------------------------------------------------------------
        // ÉTAPE 2.3 — Vérifier qu'un budget n'existe pas déjà
        // ------------------------------------------------------------
        budgetRepository.findByResidenceIdAndAnnee(dto.getResidenceId(), dto.getAnnee())
                .ifPresent(budget -> {
                    throw new RuntimeException("Un budget existe déjà pour cette résidence et cette année");
                });

        // ------------------------------------------------------------  
        // ÉTAPE 2.3.1 — Vérifier qu'un budget ACTIF n'existe pas déjà
        // ------------------------------------------------------------
        budgetRepository.findByResidenceIdAndAnneeAndStatus(dto.getResidenceId(), dto.getAnnee(), "ACTIVE")
                .ifPresent(budget -> {
                    throw new RuntimeException("Un budget actif existe déjà pour cette résidence et cette année");
                });

        // ------------------------------------------------------------  
        // ÉTAPE 2.4 — Créer l'entité Budget
        // ------------------------------------------------------------
        Budget budget = new Budget();
        budget.setResidence(residence);
        budget.setAnnee(dto.getAnnee());
        budget.setRepartitionMode(dto.getRepartitionMode());
        budget.setSyndic(currentSyndic);
        budget.setBudgetTotal(BigDecimal.ZERO);
        budget.setStatus(BudgetStatus.ACTIVE);

        // Générer la référence unique du budget (ex: BUD-2026-123456)
        String reference = "BUD-" + dto.getAnnee() + "-" + (int)(Math.random() * 900000 + 100000);
        budget.setReference(reference);

        // ------------------------------------------------------------
        // ÉTAPE 2.5 — Créer les postes budgétaires
        // ------------------------------------------------------------
        List<BudgetItem> budgetItems = new ArrayList<>();
        BigDecimal budgetTotal = BigDecimal.ZERO;

        for (BudgetItemInputDTO itemDto : dto.getItems()) {
            BudgetItem item = new BudgetItem();
            item.setBudget(budget);
            item.setLibelle(itemDto.getLibelle());
            item.setMontant(itemDto.getMontant());

            // Si le syndic a sélectionné une suggestion d'équipement commun, la lier
            if (itemDto.getCommonFacilityId() != null) {
                CommonFacility facility = commonFacilityRepository.findById(itemDto.getCommonFacilityId())
                        .orElseThrow(() -> new RuntimeException("Équipement commun introuvable"));

                // Vérifier que l'équipement appartient bien à la résidence de ce budget
                if (!facility.getResidence().getId().equals(residence.getId())) {
                    throw new BadRequestException("Cet équipement commun n'appartient pas à cette résidence");
                }

                item.setCommonFacility(facility);
            }

            budgetItems.add(item); //on regroupe les postes budgetaires
            budgetTotal = budgetTotal.add(itemDto.getMontant()); //On cumule le total
        }

        budget.setBudgetTotal(budgetTotal);
        budget.setItems(budgetItems);

        // ------------------------------------------------------------
        // ÉTAPE 2.6 — Sauvegarder le budget
        // ------------------------------------------------------------
        Budget savedBudget = budgetRepository.save(budget);

        // ------------------------------------------------------------
        // ÉTAPE 2.7 — Retourner le détail complet
        // ------------------------------------------------------------
        return buildBudgetDetailDTO(savedBudget);
    }

    // ============================================================
    // LISTE DES BUDGETS (page cartes)
    // ============================================================

    @Override
    public BudgetListResponse getBudgetsForSyndic(int page, int size) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Construit la pagination : page demandée, taille demandée, tri du plus récent au plus ancien
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Récupère uniquement les budgets de la page demandée (pas tous les budgets en mémoire)
        Page<Budget> budgetPage = budgetRepository.findBySyndicId(currentSyndic.getId(), pageable);

        // Compte le nombre total de budgets du syndic (toutes pages confondues)
        Integer totalBudgets = budgetRepository.countBySyndicId(currentSyndic.getId());

        // Compte le nombre de budgets ACTIVE du syndic (toutes pages confondues)
        Integer activeBudgetsCount = budgetRepository.countBySyndicIdAndStatus(currentSyndic.getId(), BudgetStatus.ACTIVE);

        // Transforme chaque Budget de la page courante en BudgetCardDTO
        List<BudgetCardDTO> cardDtos = budgetPage.getContent().stream() // récupère la liste des budgets de la page et ouvre un flux dessus
                .map(this::buildBudgetCard) // pour chaque budget, applique buildBudgetCard() et récupère la carte correspondante
                .toList(); // rassemble tous les résultats dans une nouvelle liste

        // Crée l'objet de réponse final
        BudgetListResponse response = new BudgetListResponse();
        // Renseigne le nombre total de budgets
        response.setTotalBudgets(totalBudgets);
        // Renseigne le nombre de budgets actifs
        response.setActiveBudgetsCount(activeBudgetsCount);
        // Renseigne la liste des cartes de la page courante
        response.setBudgets(cardDtos);
        // Renseigne le numéro de la page actuelle
        response.setCurrentPage(page);
        // Renseigne le nombre total de pages disponibles
        response.setTotalPages(budgetPage.getTotalPages());

        // Retourne la réponse complète
        return response;
    }

    // ============================================================
// DÉTAIL D'UN BUDGET AVEC KPIs (carte "Budget 2026 — Résidence X")
// ============================================================
    @Override
    public BudgetOverviewDTO getBudgetOverview(Long budgetId) {

        // Récupérer le budget, erreur si introuvable
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        // Vérifier que ce budget appartient bien au syndic connecté (sécurité — même pattern que getBudgetDetail)
        User currentSyndic = getCurrentUser();
        if (!budget.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce budget");
        }

        // Récupère toutes les transactions TRAVAUX de cette résidence pour l'année du budget
        List<SyndicWalletTransaction> transactionsTravaux = syndicWalletTransactionRepository
                .findTravauxByResidenceAndYear(budget.getResidence().getId(), budget.getAnnee());

        // Additionne tous les montants de la liste (chaque montant est négatif en base, sortie d'argent)
        BigDecimal depensesBrutes = transactionsTravaux.stream()
                .map(SyndicWalletTransaction::getAmount) // récupère le montant de chaque transaction
                .reduce(BigDecimal.ZERO, BigDecimal::add); // additionne tous les montants, en partant de 0

        // On repasse en valeur positive pour l'affichage — c'est la seule donnée "réelle" calculée de la page
        BigDecimal depensesReellesGlobal = depensesBrutes.abs();

        // --- Construction du DTO principal (les 4 KPI) ---
        BudgetOverviewDTO dto = new BudgetOverviewDTO();
        dto.setId(budget.getId());
        dto.setReference(budget.getReference());
        dto.setAnnee(budget.getAnnee());
        dto.setStatus(budget.getStatus().name());
        dto.setResidenceName(budget.getResidence().getName());
        dto.setBudgetTotal(budget.getBudgetTotal());
        dto.setDepensesReellesGlobal(depensesReellesGlobal);

        // Écart budgétaire = budget total - dépenses réelles globales
        BigDecimal ecartGlobal = budget.getBudgetTotal().subtract(depensesReellesGlobal);
        dto.setEcartBudgetaire(ecartGlobal);

        // Pourcentage de consommation global (protection contre division par zéro si budgetTotal = 0)
        dto.setConsommationPercentage(calculatePercentage(depensesReellesGlobal, budget.getBudgetTotal()));

        // --- Construction du tableau des postes ---
        // Transforme chaque BudgetItem en BudgetItemOverviewDTO
        List<BudgetItemOverviewDTO> itemDtos = budget.getItems().stream() // ouvre un flux sur la liste des postes
                .map(item -> buildItemOverview(item, budget.getBudgetTotal())) // transforme chaque poste en DTO
                .toList(); // rassemble les résultats dans une nouvelle liste

        dto.setItems(itemDtos);

        return dto;
    }

    @Override
    public BudgetDetailDTO getBudgetDetail(Long budgetId) {
        // ------------------------------------------------------------
        // ÉTAPE 3.1 — Récupérer le budget
        // ------------------------------------------------------------
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        // ------------------------------------------------------------
        // ÉTAPE 3.1.1 — Vérifier que la résidence appartient au syndic connecté
        // ------------------------------------------------------------
        User currentSyndic = getCurrentUser();
        if (!budget.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce budget");
        }

        // ------------------------------------------------------------
        // ÉTAPE 3.2 — Construire et retourner le détail
        // ------------------------------------------------------------
        return buildBudgetDetailDTO(budget);
    }


    // ============================================================
    // 5. MÉTHODE HELPER — RÉCUPÉRER L'UTILISATEUR CONNECTÉ
    // ============================================================

    private User getCurrentUser() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    // ============================================================
    // 4. HELPER — Construction du détail complet du budget
   // ============================================================

    private BudgetDetailDTO buildBudgetDetailDTO(Budget budget) {

        // ------------------------------------------------------------
        // ÉTAPE 4.1 — Récupérer tous les lots de la résidence
        // ------------------------------------------------------------

        List<Property> properties =
                propertyRepository.findByResidenceId(
                        budget.getResidence().getId());


        // ------------------------------------------------------------
        // ÉTAPE 4.2 — Regrouper les lots par copropriétaire
        // ------------------------------------------------------------

        Map<User, List<Property>> proprietesParOwner =
                properties.stream()

                        // On ignore les lots vacants
                        .filter(p -> p.getOwner() != null)

                        // Un propriétaire -> tous ses lots
                        .collect(Collectors.groupingBy(Property::getOwner));


        // ------------------------------------------------------------
        // ÉTAPE 4.3 — Déterminer la fréquence de paiement
        // (Mensuel ou Trimestriel)
        // ------------------------------------------------------------

        SyndicFinancialSettings settings =
                syndicFinancialSettingsRepository
                        .findBySyndicId(budget.getSyndic().getId())
                        .orElse(null);

        boolean estMensuel =
                settings != null &&
                        settings.getChargeFrequency() == ChargeFrequency.MENSUEL;

        // Diviseur utilisé pour calculer la quote-part
        // Mensuel : /12
        // Trimestriel : /4
        int diviseurPeriode = estMensuel ? 12 : 4;

        String periodeLabel =
                estMensuel
                        ? "PAR MOIS"
                        : "PAR TRIMESTRE";


        // ------------------------------------------------------------
        // ÉTAPE 4.4 — Préparer les variables de calcul
        // ------------------------------------------------------------

        List<CoOwnerQuotePartDTO> repartition = new ArrayList<>();

        BigDecimal totalTantiemeGlobal = BigDecimal.ZERO;

        BigDecimal totalQuotePartPeriode = BigDecimal.ZERO;


        // ------------------------------------------------------------
        // ÉTAPE 4.5 — Calculer la quote-part de chaque copropriétaire
        // ------------------------------------------------------------

        for (Map.Entry<User, List<Property>> entry : proprietesParOwner.entrySet()) {

            // Copropriétaire courant
            User owner = entry.getKey();

            // Tous les lots de ce copropriétaire
            List<Property> lots = entry.getValue();

            // --------------------------------------------------------
            // Calculer le total des tantièmes du copropriétaire
            // --------------------------------------------------------

            // On additionne les tantièmes de tous ses lots.
            //
            // Exemple :
            // F1 = 7.5
            // F3 = 5.0
            //
            // Total = 12.5
            BigDecimal totalTantieme = lots.stream()

                    // On récupère les tantièmes de chaque lot.
                    .map(Property::getTantieme)

                    // On additionne tous les tantièmes.
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // --------------------------------------------------------
            // Calculer la quote-part annuelle
            // --------------------------------------------------------

            // Formule :
            //
            // Quote-part annuelle =
            // Budget total × (Total des tantièmes / 100)
            //
            // Exemple :
            // Budget = 12 000 000
            // Tantièmes = 12.5
            //
            // Résultat = 1 500 000
            BigDecimal quotePartAnnuelle = budget.getBudgetTotal()

                    // Multiplier le budget par les tantièmes.
                    .multiply(totalTantieme)

                    // Diviser par 100 pour obtenir sa part.
                    .divide(
                            BigDecimal.valueOf(100),
                            0,
                            RoundingMode.HALF_UP);

            // --------------------------------------------------------
            // Calculer le montant à payer par période
            // --------------------------------------------------------

            // Si la fréquence est :
            // - Mensuelle → division par 12
            // - Trimestrielle → division par 4
            BigDecimal quotePartPeriode = quotePartAnnuelle.divide(
                    BigDecimal.valueOf(diviseurPeriode),
                    0,
                    RoundingMode.HALF_UP);


            // --------------------------------------------------------
            // Construire la liste des types de bien appartenant au copropriétaire
            // --------------------------------------------------------
            // Exemple :
            // Appartement
            // Studio
            //
            // Résultat :
            // ["Appartement", "Studio"]
            List<String> typeBienNames = lots.stream()

                    // On récupère le nom du type de bien de chaque lot.
                    .map(property -> property.getTypeBien() != null
                            ? property.getTypeBien().getName()
                            : null)

                    // On rassemble tous les noms dans une liste.
                    .collect(Collectors.toList());

            // --------------------------------------------------------
            // Construire une ligne de répartition pour ce copropriétaire
            // --------------------------------------------------------

            repartition.add(
                    CoOwnerQuotePartDTO.builder()

                            // Identifiant du copropriétaire.
                            .coOwnerId(owner.getId())

                            // Nom complet.
                            .coOwnerName(owner.getFirstName() + " " + owner.getLastName())

                            // Liste des types de biens possédés.
                            .typeBienNames(typeBienNames)

                            // Total des tantièmes.
                            .totalTantieme(totalTantieme)

                            // Montant annuel à payer.
                            .quotePartAnnuelle(quotePartAnnuelle)

                            // Montant à payer selon la fréquence
                            // (mensuelle ou trimestrielle).
                            .quotePartPeriode(quotePartPeriode)

                            .build());

            // --------------------------------------------------------
            // Mettre à jour les totaux généraux
            // --------------------------------------------------------

            // Additionner les tantièmes de tous les copropriétaires.
            totalTantiemeGlobal = totalTantiemeGlobal.add(totalTantieme);

            // Additionner toutes les quotes-parts par période.
            totalQuotePartPeriode = totalQuotePartPeriode.add(quotePartPeriode);
        }

        // ------------------------------------------------------------
        // ÉTAPE 4.6 — Convertir les postes budgétaires en DTO
        // ------------------------------------------------------------
        List<BudgetItemDTO> itemsDTO = budget.getItems().stream()

                // Transformer chaque BudgetItem en BudgetItemDTO.
                .map(item -> BudgetItemDTO.builder()
                        .id(item.getId())
                        .libelle(item.getLibelle())
                        .montant(item.getMontant())
                        .commonFacilityId(item.getCommonFacility() != null ? item.getCommonFacility().getId() : null)
                        .commonFacilityName(item.getCommonFacility() != null ? item.getCommonFacility().getFacilityType().getName() : null)
                        .build())

                .toList();

         // ------------------------------------------------------------
         // ÉTAPE 4.7 — Construire le DTO final
        // ------------------------------------------------------------
        return BudgetDetailDTO.builder()

                // Informations générales
                .id(budget.getId())
                .residenceName(budget.getResidence().getName())
                .annee(budget.getAnnee())
                .budgetTotal(budget.getBudgetTotal())

                // Liste des postes budgétaires
                .items(itemsDTO)

                // Répartition par copropriétaire
                .repartition(repartition)

                // Libellé de la période (PAR MOIS / PAR TRIMESTRE)
                .periodeLabel(periodeLabel)

                // Totaux généraux
                .totalTantieme(totalTantiemeGlobal)
                .totalQuotePartPeriode(totalQuotePartPeriode)

                .build();


    }

    // ============================================================
    // APPEL DE CHARGES
    // ============================================================

    /**
     * Aperçu avant génération d'un appel de charges.
     * Retourne les données calculées sans rien créer en base.
     */
    @Override
    public ChargeCallPreviewDTO previewChargeCall(Long budgetId, Integer periodNumber) {
        // 1. Récupérer le budget et vérifier l'appartenance au syndic
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        User currentSyndic = getCurrentUser();
        if (!budget.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Ce budget ne vous appartient pas");
        }

        // 2. Récupérer la fréquence depuis les paramètres du syndic
        SyndicFinancialSettings settings = syndicFinancialSettingsRepository.findBySyndicId(currentSyndic.getId())
                .orElseThrow(() -> new RuntimeException("Paramètres financiers non trouvés"));
        ChargeFrequency frequency = settings.getChargeFrequency();

        // 3. Déterminer le nombre de périodes (4 si trimestriel, 12 si mensuel)
        int numberOfPeriods;
        if (frequency == ChargeFrequency.TRIMESTRIEL) {
            numberOfPeriods = 4;
        } else {
            numberOfPeriods = 12;
        }

        // 4. Vérifier qu'aucun ChargeCall n'existe déjà pour cette période
        chargeCallRepository.findByBudgetIdAndYearAndPeriodNumber(budgetId, budget.getAnnee(), periodNumber)
                .ifPresent(chargeCall -> {
                    throw new RuntimeException("Un appel de charges existe déjà pour cette période");
                });

        // 5. Calculer le montant total de la période (budget / nombre de périodes)
        BigDecimal totalAmount = budget.getBudgetTotal()
                .divide(BigDecimal.valueOf(numberOfPeriods), 2, RoundingMode.HALF_UP);

        // 6. Construire la répartition par copropriétaire (fusionné par owner)
        List<Property> properties = propertyRepository.findByResidenceId(budget.getResidence().getId());
        List<CoOwnerQuotePartPreviewDTO> repartition = new ArrayList<>();
        BigDecimal totalTantieme = BigDecimal.ZERO;

        for (Property property : properties) {
            if (property.getOwner() != null) {
                // Vérifier si le copropriétaire est déjà dans la liste
                boolean alreadyAdded = false;
                for (CoOwnerQuotePartPreviewDTO item : repartition) {
                    if (item.getCoOwnerId().equals(property.getOwner().getId())) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    // Calculer le tantième total de ce copropriétaire (somme de ses lots)
                    BigDecimal tantiemeCoOwner = BigDecimal.ZERO;
                    for (Property p : properties) {
                        if (p.getOwner() != null && p.getOwner().getId().equals(property.getOwner().getId())) {
                            tantiemeCoOwner = tantiemeCoOwner.add(
                                    p.getTantieme() != null ? p.getTantieme() : BigDecimal.ZERO
                            );
                        }
                    }

                    // Calculer la quote-part (totalAmount * tantieme / 100)
                    BigDecimal quotePart = totalAmount.multiply(tantiemeCoOwner)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    CoOwnerQuotePartPreviewDTO item = CoOwnerQuotePartPreviewDTO.builder()
                            .coOwnerId(property.getOwner().getId())
                            .coOwnerName(property.getOwner().getFirstName() + " " + property.getOwner().getLastName())
                            .tantieme(tantiemeCoOwner)
                            .quotePart(quotePart)
                            .build();

                    repartition.add(item);
                    totalTantieme = totalTantieme.add(tantiemeCoOwner);
                }
            }
        }

        // 7. Construire et retourner l'aperçu
        return ChargeCallPreviewDTO.builder()
                .budgetReference("BUD-" + budget.getAnnee())
                .residenceName(budget.getResidence().getName())
                .year(budget.getAnnee())
                .periodNumber(periodNumber)
                .totalAmount(totalAmount)
                .repartition(repartition)
                .totalTantieme(totalTantieme)
                .coOwnersCount(repartition.size())
                .build();
    }

    /**
     * Génère un appel de charges et envoie les emails aux copropriétaires.
     */
    @Override
    @Transactional
    public void generateChargeCall(Long budgetId, GenerateChargeCallDTO dto) {
        // 1. Récupérer le budget et vérifier l'appartenance au syndic
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        User currentSyndic = getCurrentUser();
        if (!budget.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Ce budget ne vous appartient pas");
        }

        // 2. Vérifier qu'aucun ChargeCall n'existe déjà pour cette période
        chargeCallRepository.findByBudgetIdAndYearAndPeriodNumber(budgetId, budget.getAnnee(), dto.getPeriodNumber())
                .ifPresent(chargeCall -> {
                    throw new RuntimeException("Un appel de charges existe déjà pour cette période");
                });

        // 3. Récupérer la fréquence et recalculer le montant total (jamais faire confiance au front)
        SyndicFinancialSettings settings = syndicFinancialSettingsRepository.findBySyndicId(currentSyndic.getId())
                .orElseThrow(() -> new RuntimeException("Paramètres financiers non trouvés"));
        ChargeFrequency frequency = settings.getChargeFrequency();

        int numberOfPeriods;
        if (frequency == ChargeFrequency.TRIMESTRIEL) {
            numberOfPeriods = 4;
        } else {
            numberOfPeriods = 12;
        }

        BigDecimal totalAmount = budget.getBudgetTotal()
                .divide(BigDecimal.valueOf(numberOfPeriods), 2, RoundingMode.HALF_UP);

        // 4. Créer le ChargeCall avec les paramètres
        ChargeCall chargeCall = new ChargeCall();
        chargeCall.setBudget(budget);
        chargeCall.setFrequency(frequency);
        chargeCall.setYear(budget.getAnnee());
        chargeCall.setPeriodNumber(dto.getPeriodNumber());
        chargeCall.setRepartitionMode(budget.getRepartitionMode());
        chargeCall.setTotalAmount(totalAmount);
        chargeCall.setSentDate(dto.getSentDate());
        chargeCall.setDueDate(dto.getDueDate());

        // 5. Créer les ChargeCallItem pour chaque copropriétaire (fusionné par owner)
        List<Property> properties = propertyRepository.findByResidenceId(budget.getResidence().getId());
        List<ChargeCallItem> items = new ArrayList<>();

        for (Property property : properties) {
            if (property.getOwner() != null) {
                // Vérifier si le copropriétaire a déjà un item
                boolean alreadyAdded = false;
                for (ChargeCallItem item : items) {
                    if (item.getCoOwner().getId().equals(property.getOwner().getId())) {
                        alreadyAdded = true;
                        break;
                    }
                }

                if (!alreadyAdded) {
                    // Calculer le tantième total de ce copropriétaire (somme de ses lots)
                    BigDecimal tantiemeCoOwner = BigDecimal.ZERO;
                    for (Property p : properties) {
                        if (p.getOwner() != null && p.getOwner().getId().equals(property.getOwner().getId())) {
                            tantiemeCoOwner = tantiemeCoOwner.add(
                                    p.getTantieme() != null ? p.getTantieme() : BigDecimal.ZERO
                            );
                        }
                    }

                    // Calculer la quote-part (totalAmount * tantieme / 100)
                    BigDecimal quotePart = totalAmount.multiply(tantiemeCoOwner)
                            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                    // Générer la référence
                    String reference = "ACI-" + budget.getAnnee() + "-" + dto.getPeriodNumber() + "-" + property.getOwner().getId();

                    ChargeCallItem item = new ChargeCallItem();
                    item.setChargeCall(chargeCall);
                    item.setReference(reference);
                    item.setCoOwner(property.getOwner());
                    item.setTantieme(tantiemeCoOwner);
                    item.setQuotePart(quotePart);
                    item.setPaidAmount(BigDecimal.ZERO);
                    item.setRemainingAmount(quotePart);

                    items.add(item);
                }
            }
        }

        chargeCall.setItems(items);
        ChargeCall savedChargeCall = chargeCallRepository.save(chargeCall);

        // 6. Envoi des emails et notifications push aux copropriétaires (non-bloquant)
        for (ChargeCallItem item : items) {
            try {
                // Email
                String subject = "Appel de charges — " + budget.getResidence().getName();
                String body = buildChargeCallEmailBody(savedChargeCall, item);
                emailService.sendEmail(item.getCoOwner().getEmail(), subject, body);
            } catch (Exception e) {
                // Log l'erreur mais ne pas bloquer la génération
                System.err.println("Erreur envoi email à " + item.getCoOwner().getEmail() + ": " + e.getMessage());
            }

            try {
                // Notification push si activée
                if (item.getCoOwner().isNotificationsEnabled()) {
                    String title = "Appel de charges";
                    String body = savedChargeCall.getBudget().getResidence().getName() + " — " + savedChargeCall.getPeriodNumber() + "/" + savedChargeCall.getYear();
                    notificationService.sendPush(item.getCoOwner().getId(), title, body);
                }
            } catch (Exception e) {
                // Log l'erreur mais ne pas bloquer la génération
                System.err.println("Erreur envoi notification à " + item.getCoOwner().getId() + ": " + e.getMessage());
            }
        }
    }

    private String buildChargeCallEmailBody(ChargeCall chargeCall, ChargeCallItem item) {
        StringBuilder body = new StringBuilder();
        body.append("Appel de charges\n\n");
        body.append("Résidence : ").append(chargeCall.getBudget().getResidence().getName()).append("\n");
        body.append("Période : ").append(chargeCall.getPeriodNumber()).append("/").append(chargeCall.getYear()).append("\n");
        body.append("Date d'échéance : ").append(chargeCall.getDueDate()).append("\n");
        body.append("Votre quote-part : ").append(item.getQuotePart()).append("\n");
        body.append("Montant restant à payer : ").append(item.getRemainingAmount()).append("\n");
        return body.toString();
    }

    // ============================================================
    // APPEL DE CHARGES EXCEPTIONNEL
    // ============================================================

    /**
     * Créer un Appel Exceptionnel — Section 1 (Informations générales)
     * Crée l'entité en statut BROUILLON, complétée par les sections suivantes
     */
    @Transactional
    public ExceptionalCallDetailDTO createExceptionalCall(CreateExceptionalCallDTO dto) {

        // Récupérer le syndic connecté — méthode déjà existante dans ChargeServiceImpl
        User currentSyndic = getCurrentUser();

        // Vérifier que la résidence appartient bien à ce syndic
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new RuntimeException("Résidence introuvable"));

        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cette résidence ne vous appartient pas");
        }

        // Créer l'Appel Exceptionnel en BROUILLON — complété par les sections suivantes
        ExceptionalCall exceptionalCall = new ExceptionalCall();
        exceptionalCall.setResidence(residence);
        exceptionalCall.setSyndic(currentSyndic);
        exceptionalCall.setCategory(dto.getCategory());
        exceptionalCall.setTitle(dto.getTitle());
        exceptionalCall.setDescription(dto.getDescription());
        exceptionalCall.setStatus(ExceptionalCallStatus.BROUILLON);

        ExceptionalCall saved = exceptionalCallRepository.save(exceptionalCall);

        return toExceptionalCallDetailDTO(saved);
    }

    /**
     * Compléter un Appel Exceptionnel — Section 2 (Informations financières)
     * Définit le montant total et la répartition (tantièmes ou personnalisée)
     */
    @Transactional
    public ExceptionalCallDetailDTO updateExceptionalCallFinancialInfo(Long exceptionalCallId, UpdateExceptionalCallFinancialDTO dto) {

        User currentSyndic = getCurrentUser();

        ExceptionalCall exceptionalCall = exceptionalCallRepository.findById(exceptionalCallId)
                .orElseThrow(() -> new RuntimeException("Appel exceptionnel introuvable"));

        if (!exceptionalCall.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cet appel exceptionnel ne vous appartient pas");
        }

        exceptionalCall.setTotalAmount(dto.getTotalAmount());
        exceptionalCall.setRepartitionMode(dto.getRepartitionMode());

        // Si cette section est modifiée après un premier passage, on repart de zéro sur les items
        exceptionalCall.getItems().clear();

        List<Property> properties = propertyRepository.findByResidenceId(exceptionalCall.getResidence().getId());

        if (dto.getRepartitionMode() == RepartitionMode.OWNERSHIP_SHARES) {
            // Calcul automatique par tantième — même logique de fusion par copropriétaire
            // déjà utilisée dans previewChargeCall/generateChargeCall (un copropriétaire
            // avec plusieurs lots dans la résidence = une seule ligne, tantièmes additionnés)
            List<Long> alreadyAddedOwnerIds = new ArrayList<>();

            for (Property property : properties) {
                if (property.getOwner() == null) continue;

                Long ownerId = property.getOwner().getId();
                boolean alreadyAdded = false;
                for (Long addedId : alreadyAddedOwnerIds) {
                    if (addedId.equals(ownerId)) {
                        alreadyAdded = true;
                        break;
                    }
                }
                if (alreadyAdded) continue;

                // Additionner les tantièmes de tous les lots de ce copropriétaire
                BigDecimal tantiemeCoOwner = BigDecimal.ZERO;
                for (Property p : properties) {
                    if (p.getOwner() != null && p.getOwner().getId().equals(ownerId)) {
                        tantiemeCoOwner = tantiemeCoOwner.add(p.getTantieme() != null ? p.getTantieme() : BigDecimal.ZERO);
                    }
                }

                // Quote-part = montant total × (tantième du copropriétaire / 100)
                BigDecimal quotePart = dto.getTotalAmount()
                        .multiply(tantiemeCoOwner)
                        .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

                ExceptionalCallItem item = new ExceptionalCallItem();
                item.setExceptionalCall(exceptionalCall);
                item.setCoOwner(property.getOwner());
                item.setTantieme(tantiemeCoOwner);
                item.setQuotePart(quotePart);
                exceptionalCall.getItems().add(item);

                alreadyAddedOwnerIds.add(ownerId);
            }

        } else {
            // Mode CUSTOM — le syndic a saisi manuellement chaque montant, pas de calcul
            if (dto.getCustomAmounts() == null || dto.getCustomAmounts().isEmpty()) {
                throw new BadRequestException("Saisissez un montant pour chaque copropriétaire en mode Personnalisée");
            }

            for (CustomCoOwnerAmountDTO customAmount : dto.getCustomAmounts()) {
                User coOwner = userRepository.findById(customAmount.getCoOwnerId())
                        .orElseThrow(() -> new RuntimeException("Copropriétaire introuvable"));

                // Snapshotter le tantième même en mode personnalisé, pour affichage/référence uniquement
                BigDecimal tantiemeCoOwner = BigDecimal.ZERO;
                for (Property p : properties) {
                    if (p.getOwner() != null && p.getOwner().getId().equals(customAmount.getCoOwnerId())) {
                        tantiemeCoOwner = tantiemeCoOwner.add(p.getTantieme() != null ? p.getTantieme() : BigDecimal.ZERO);
                    }
                }

                ExceptionalCallItem item = new ExceptionalCallItem();
                item.setExceptionalCall(exceptionalCall);
                item.setCoOwner(coOwner);
                item.setTantieme(tantiemeCoOwner);
                item.setQuotePart(customAmount.getAmount()); // montant saisi manuellement, pas calculé
                exceptionalCall.getItems().add(item);
            }
        }

        ExceptionalCall saved = exceptionalCallRepository.save(exceptionalCall);

        return toExceptionalCallDetailDTO(saved);
    }

    /**
     * Activer un Appel Exceptionnel — Section 3 (Validation & Documents)
     * Combine l'upload des documents et le changement de statut dans la même transaction
     */
    @Transactional
    public ExceptionalCallDetailDTO activateExceptionalCall(Long exceptionalCallId, Boolean requiresAgValidation, List<MultipartFile> files) {

        User currentSyndic = getCurrentUser();

        ExceptionalCall exceptionalCall = exceptionalCallRepository.findById(exceptionalCallId)
                .orElseThrow(() -> new RuntimeException("Appel exceptionnel introuvable"));

        if (!exceptionalCall.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cet appel exceptionnel ne vous appartient pas");
        }

        // Vérifications avant activation — le montant et la répartition doivent déjà être renseignés
        if (exceptionalCall.getTotalAmount() == null || exceptionalCall.getItems().isEmpty()) {
            throw new BadRequestException("Complétez les informations financières avant d'activer");
        }

        // Uploader chaque document déposé, s'il y en a — dans la même transaction que l'activation
        // (si un upload échoue, tout est annulé, y compris le changement de statut — cohérent avec
        // le fait que documents et activation sont maintenant une seule action indivisible)
        if (files != null) {
            for (MultipartFile file : files) {
                String fileUrl = minioService.uploadFile(file, "exceptional-calls");

                ExceptionalCallDocument document = new ExceptionalCallDocument();
                document.setExceptionalCall(exceptionalCall);
                document.setFileName(file.getOriginalFilename());
                document.setFileUrl(fileUrl);
                document.setFileSizeKb(file.getSize() / 1024);

                exceptionalCall.getDocuments().add(document);
            }
        }

        exceptionalCall.setRequiresAgValidation(requiresAgValidation);

        // Décision Option A : pas de lien vers une AG ici, juste le statut qui reflète
        // si une validation est requise ou non — le rattachement à une résolution
        // se fera plus tard, depuis le côté Resolution (chantier séparé).
        if (requiresAgValidation) {
            exceptionalCall.setStatus(ExceptionalCallStatus.EN_ATTENTE_VOTE);
        } else {
            exceptionalCall.setStatus(ExceptionalCallStatus.ACTIVE);
            // IMPORTANT — pas de génération de ChargeCall ici pour l'instant (décision volontaire,
            // en attente de confirmation sur ce comportement). L'Appel Exceptionnel reste autonome,
            // suivi uniquement via ses propres ExceptionalCallItem — il n'apparaît PAS encore dans
            // les écrans Finances/Paiements déjà construits pour les charges normales. Si ce lien
            // est confirmé nécessaire plus tard, ce sera un chantier séparé et explicite, pas ajouté
            // implicitement ici.
        }

        ExceptionalCall saved = exceptionalCallRepository.save(exceptionalCall);

        return toExceptionalCallDetailDTO(saved);
    }


    @Override
    @Transactional(readOnly = true)
    public List<CommonFacilitySuggestionDTO> searchCommonFacilities(Long residenceId, String q) {
        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new RuntimeException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // Récupérer les équipements communs
        List<CommonFacility> facilities;
        if (q == null || q.trim().isEmpty()) {
            facilities = commonFacilityRepository.findByResidenceId(residenceId);
        } else {
            facilities = commonFacilityRepository.findByResidenceIdAndFacilityTypeNameContainingIgnoreCase(residenceId, q.trim());
        }

        // Mapper vers DTO
        List<CommonFacilitySuggestionDTO> result = new ArrayList<>();
        for (CommonFacility facility : facilities) {
            CommonFacilitySuggestionDTO dto = CommonFacilitySuggestionDTO.builder()
                    .id(facility.getId())
                    .name(facility.getFacilityType().getName())
                    .icon(facility.getFacilityType().getIcon())
                    .build();
            result.add(dto);
        }

        return result;
    }

    // ============================================================
    // Méthodes Utilitaires
    // ============================================================

    // Construit une ligne du tableau des postes (montantReel = montantPrevu en V1)
    private BudgetItemOverviewDTO buildItemOverview(BudgetItem item, BigDecimal budgetTotal) {
        BudgetItemOverviewDTO itemDto = new BudgetItemOverviewDTO();
        itemDto.setLibelle(item.getLibelle());
        itemDto.setMontantPrevu(item.getMontant());

        // V1 : montant réel = montant prévu (voir commentaire de justification dans le DTO)
        itemDto.setMontantReel(item.getMontant());
        itemDto.setEcart(BigDecimal.ZERO);

        // Pourcentage du poste par rapport au budget total
        itemDto.setPercentage(calculatePercentage(item.getMontant(), budgetTotal));

        return itemDto;
    }

    // Construit la carte d'un seul budget — extrait dans sa propre méthode pour rester lisible
    private BudgetCardDTO buildBudgetCard(Budget budget) {

        // Crée le DTO de carte vide
        BudgetCardDTO dto = new BudgetCardDTO();
        // Renseigne l'identifiant du budget
        dto.setId(budget.getId());
        // Renseigne la référence (ex: BUD-2026-001)
        dto.setReference(budget.getReference());
        // Renseigne le statut sous forme de texte (DRAFT, ACTIVE, CLOSED)
        dto.setStatus(budget.getStatus().name());
        // Renseigne l'année du budget
        dto.setAnnee(budget.getAnnee());
        // Renseigne le nom de la résidence liée au budget
        dto.setResidenceName(budget.getResidence().getName());
        // Renseigne le libellé du mode de répartition (fixe en V1, un seul mode actif)
        dto.setRepartitionModeLabel("Tantièmes");

        // Récupère la liste des postes budgétaires du budget
        List<BudgetItem> items = budget.getItems();
        // Renseigne le nombre total de postes
        dto.setTotalPostes(items.size());
        // Renseigne le montant total du budget
        dto.setBudgetTotal(budget.getBudgetTotal());

        // Prend les 4 premiers postes et les transforme en aperçus
        List<BudgetItemPreviewDTO> topItems = items.stream() // ouvre un flux sur la liste des postes
                .limit(4) // garde uniquement les 4 premiers éléments du flux
                .map(item -> buildItemPreview(item, budget.getBudgetTotal())) // transforme chaque poste en DTO d'aperçu
                .toList(); // rassemble les résultats dans une nouvelle liste

        // Renseigne les 4 postes affichés sur la carte
        dto.setTopItems(topItems);
        // Calcule combien de postes restent au-delà des 4 déjà affichés (jamais négatif)
        dto.setAutresPostesCount(Math.max(0, items.size() - 4));

        // Récupère toutes les transactions TRAVAUX de cette résidence pour l'année du budget
        List<SyndicWalletTransaction> transactionsTravaux = syndicWalletTransactionRepository
                .findTravauxByResidenceAndYear(budget.getResidence().getId(), budget.getAnnee());

        // Additionne tous les montants de la liste (chaque montant est négatif en base, sortie d'argent)
        BigDecimal depensesBrutes = transactionsTravaux.stream()
                .map(SyndicWalletTransaction::getAmount) // récupère le montant de chaque transaction
                .reduce(BigDecimal.ZERO, BigDecimal::add); // additionne tous les montants, en partant de 0

        // Convertit la somme en valeur positive pour l'affichage
        BigDecimal depensesReelles = depensesBrutes.abs();
        // Renseigne les dépenses réelles globales du budget
        dto.setDepensesReelles(depensesReelles);

        // Calcule le pourcentage de consommation du budget (dépenses réelles / budget total)
        int consommationPercentage = calculatePercentage(depensesReelles, budget.getBudgetTotal());
        // Renseigne ce pourcentage dans le DTO
        dto.setDepensesReellesPercentage(consommationPercentage);

        // Retourne la carte complète
        return dto;
    }

    // Construit un poste d'aperçu (libellé, montant, pourcentage) pour une ligne de la carte
    private BudgetItemPreviewDTO buildItemPreview(BudgetItem item, BigDecimal budgetTotal) {
        // Crée le DTO de poste vide
        BudgetItemPreviewDTO itemDto = new BudgetItemPreviewDTO();
        // Renseigne le libellé du poste
        itemDto.setLibelle(item.getLibelle());
        // Renseigne le montant du poste
        itemDto.setMontant(item.getMontant());
        // Renseigne le pourcentage du poste par rapport au budget total (sert à dessiner la barre)
        itemDto.setPercentage(calculatePercentage(item.getMontant(), budgetTotal));
        // Retourne le poste construit
        return itemDto;
    }

    // Calcule un pourcentage (montant / total * 100), protégé contre la division par zéro
    private int calculatePercentage(BigDecimal montant, BigDecimal total) {
        // Si le total est nul ou négatif, on retourne 0 pour éviter une division par zéro
        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        // Multiplie le montant par 100, divise par le total, arrondit à l'entier le plus proche
        return montant.multiply(BigDecimal.valueOf(100))
                .divide(total, 0, RoundingMode.HALF_UP)
                .intValue();
    }

    /**
     * Helper method — convertit ExceptionalCall entity en ExceptionalCallDetailDTO
     * Réutilisé par createExceptionalCall, updateExceptionalCallFinancialInfo et activateExceptionalCall
     */
    private ExceptionalCallDetailDTO toExceptionalCallDetailDTO(ExceptionalCall exceptionalCall) {
        // Construire la liste des items (quote-parts par copropriétaire)
        List<ExceptionalCallItemDTO> itemDTOs = new ArrayList<>();
        for (ExceptionalCallItem item : exceptionalCall.getItems()) {
            String coOwnerName = item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName();
            ExceptionalCallItemDTO itemDTO = ExceptionalCallItemDTO.builder()
                    .id(item.getId())
                    .coOwnerId(item.getCoOwner().getId())
                    .coOwnerName(coOwnerName)
                    .tantieme(item.getTantieme())
                    .quotePart(item.getQuotePart())
                    .paidAmount(item.getPaidAmount())
                    .build();
            itemDTOs.add(itemDTO);
        }

        // Construire la liste des documents
        List<ExceptionalCallDocumentDTO> documentDTOs = new ArrayList<>();
        for (ExceptionalCallDocument doc : exceptionalCall.getDocuments()) {
            ExceptionalCallDocumentDTO docDTO = ExceptionalCallDocumentDTO.builder()
                    .id(doc.getId())
                    .fileName(doc.getFileName())
                    .fileUrl(doc.getFileUrl())
                    .fileSizeKb(doc.getFileSizeKb())
                    .createdAt(doc.getCreatedAt())
                    .build();
            documentDTOs.add(docDTO);
        }

        // Construire le DTO principal
        String syndicName = exceptionalCall.getSyndic().getFirstName() + " " + exceptionalCall.getSyndic().getLastName();

        return ExceptionalCallDetailDTO.builder()
                .id(exceptionalCall.getId())
                .residenceId(exceptionalCall.getResidence().getId())
                .residenceName(exceptionalCall.getResidence().getName())
                .syndicId(exceptionalCall.getSyndic().getId())
                .syndicName(syndicName)
                .category(exceptionalCall.getCategory())
                .title(exceptionalCall.getTitle())
                .description(exceptionalCall.getDescription())
                .totalAmount(exceptionalCall.getTotalAmount())
                .repartitionMode(exceptionalCall.getRepartitionMode())
                .items(itemDTOs)
                .requiresAgValidation(exceptionalCall.getRequiresAgValidation())
                .status(exceptionalCall.getStatus())
                .documents(documentDTOs)
                .createdAt(exceptionalCall.getCreatedAt())
                .build();
    }


}
