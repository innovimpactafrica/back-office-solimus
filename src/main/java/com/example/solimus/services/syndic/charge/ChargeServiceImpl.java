package com.example.solimus.services.syndic.charge;


import com.example.solimus.dtos.syndic.charge.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.repositories.*;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final BudgetItemRepository budgetItemRepository;
    private final SyndicWithdrawalRequestRepository syndicWithdrawalRequestRepository;
    private final ActivityLogRepository activityLogRepository;
    private final ExceptionalCallItemRepository exceptionalCallItemRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;
    private final ExceptionalCallDocumentRepository exceptionalCallDocumentRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;

    // ============================================================
    // 1. ÉTAPE 1  — APERÇU RÉSIDENCE
    // ============================================================

    @Override
    @Transactional(readOnly = true)
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
            item.setMontant(itemDto.getMontant());

            // Si le syndic a sélectionné une suggestion d'équipement commun
            if (itemDto.getCommonFacilityId() != null) {
                CommonFacility facility = commonFacilityRepository.findById(itemDto.getCommonFacilityId())
                        .orElseThrow(() -> new RuntimeException("Équipement commun introuvable"));

                // Vérifier que l'équipement appartient bien à la résidence de ce budget
                if (!facility.getResidence().getId().equals(residence.getId())) {
                    throw new BadRequestException("Cet équipement commun n'appartient pas à cette résidence");
                }

                item.setCommonFacility(facility);
                // Le libellé vient TOUJOURS du nom de l'équipement, jamais de itemDto.getLibelle()
                item.setLibelle(facility.getFacilityType().getName());

            } else {
                // Pas d'équipement commun : le libellé est obligatoire (texte libre)
                if (itemDto.getLibelle() == null || itemDto.getLibelle().isBlank()) {
                    throw new BadRequestException("Le libellé est obligatoire pour les postes sans équipement commun");
                }
                item.setLibelle(itemDto.getLibelle());
            }

            budgetItems.add(item);
            budgetTotal = budgetTotal.add(itemDto.getMontant());
        }

        budget.setBudgetTotal(budgetTotal);
        budget.setItems(budgetItems);

        // ------------------------------------------------------------
        // ÉTAPE 2.6 — Sauvegarder le budget
        // ------------------------------------------------------------
        Budget savedBudget = budgetRepository.save(budget);


        // ⬇️ AJOUT — Trace la création dans le journal d'activité
        ActivityLog log = new ActivityLog();
        log.setResidence(residence);
        log.setType(ActivityType.BUDGET_CREATED);
        log.setRelatedEntityType("BUDGET");
        log.setRelatedEntityId(savedBudget.getId());
        log.setActor(currentSyndic);
        log.setCreatedAt(LocalDateTime.now());
        log.setMessage("Budget créé — " + savedBudget.getReference());
        activityLogRepository.save(log);

        // ------------------------------------------------------------
        // ÉTAPE 2.7 — Retourner le détail complet
        // ------------------------------------------------------------
        return buildBudgetDetailDTO(savedBudget);
    }

    @Override
    @Transactional
    public BudgetDetailDTO updateBudget(Long budgetId, UpdateBudgetDTO dto) {
        // ------------------------------------------------------------
        // ÉTAPE 1 — Récupérer le budget existant
        // ------------------------------------------------------------
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        // ------------------------------------------------------------
        // ÉTAPE 2 — Vérifier l'autorisation du syndic
        // ------------------------------------------------------------
        User currentSyndic = getCurrentUser();
        if (!budget.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à modifier ce budget");
        }

        // ------------------------------------------------------------
        // ÉTAPE 3 — Mise à jour partielle des champs
        // ------------------------------------------------------------
        if (dto.getResidenceId() != null) {
            Residence residence = residenceRepository.findById(dto.getResidenceId())
                    .orElseThrow(() -> new RuntimeException("Résidence non trouvée"));
            if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
                throw new ForbiddenException("Vous n'êtes pas autorisé à modifier vers cette résidence");
            }
            budget.setResidence(residence);
        }

        if (dto.getAnnee() != null) {
            // Vérifier qu'un budget n'existe pas déjà pour cette résidence et cette nouvelle année
            budgetRepository.findByResidenceIdAndAnnee(budget.getResidence().getId(), dto.getAnnee())
                    .ifPresent(existingBudget -> {
                        if (!existingBudget.getId().equals(budgetId)) {
                            throw new RuntimeException("Un budget existe déjà pour cette résidence et cette année");
                        }
                    });
            budget.setAnnee(dto.getAnnee());
        }

        if (dto.getRepartitionMode() != null) {
            budget.setRepartitionMode(dto.getRepartitionMode());
        }

        // ------------------------------------------------------------
        // ÉTAPE 4 — Mise à jour des postes budgétaires si fournis
        // ------------------------------------------------------------
        if (dto.getItems() != null && !dto.getItems().isEmpty()) {
            // Vider la collection existante : orphanRemoval supprimera les anciens items
            budget.getItems().clear();

            // Créer les nouveaux items
            List<BudgetItem> budgetItems = new ArrayList<>();
            BigDecimal budgetTotal = BigDecimal.ZERO;

            for (BudgetItemInputDTO itemDto : dto.getItems()) {
                BudgetItem item = new BudgetItem();
                item.setBudget(budget);
                item.setMontant(itemDto.getMontant());

                // Si le syndic a sélectionné une suggestion d'équipement commun
                if (itemDto.getCommonFacilityId() != null) {
                    CommonFacility facility = commonFacilityRepository.findById(itemDto.getCommonFacilityId())
                            .orElseThrow(() -> new RuntimeException("Équipement commun introuvable"));

                    // Vérifier que l'équipement appartient bien à la résidence de ce budget
                    if (!facility.getResidence().getId().equals(budget.getResidence().getId())) {
                        throw new BadRequestException("Cet équipement commun n'appartient pas à cette résidence");
                    }

                    item.setCommonFacility(facility);
                    item.setLibelle(facility.getFacilityType().getName());
                } else {
                    // Pas d'équipement commun : le libellé est obligatoire
                    if (itemDto.getLibelle() == null || itemDto.getLibelle().isBlank()) {
                        throw new BadRequestException("Le libellé est obligatoire pour les postes sans équipement commun");
                    }
                    item.setLibelle(itemDto.getLibelle());
                }

                budgetItems.add(item);
                budgetTotal = budgetTotal.add(itemDto.getMontant());
            }

            budget.setBudgetTotal(budgetTotal);
            // Muter la collection existante (ne pas remplacer la référence à cause d'orphanRemoval)
            budget.getItems().addAll(budgetItems);
        }

        // ------------------------------------------------------------
        // ÉTAPE 5 — Sauvegarder le budget
        // ------------------------------------------------------------
        Budget savedBudget = budgetRepository.save(budget);

        // ------------------------------------------------------------
        // ÉTAPE 6 — Retourner le détail complet
        // ------------------------------------------------------------
        return buildBudgetDetailDTO(savedBudget);
    }

    // ============================================================
    // LISTE DES BUDGETS (page cartes)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
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
    @Transactional(readOnly = true)
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

        // On repasse en valeur positive pour l'affichage
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
        // budgetTotal → sert au calcul du percentage
        // annee → sert au calcul du montantReel (dépenses réelles de l'année pour cet équipement)
        // item → contient déjà son propre montant, pour calculer l'écart
        List<BudgetItemOverviewDTO> itemDtos = budget.getItems().stream()
                .map(item -> buildItemOverview(item, budget.getBudgetTotal(), budget.getAnnee())) // transforme chaque poste en DTO
                .toList(); // rassemble les résultats dans une nouvelle liste

        dto.setItems(itemDtos);

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
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

    @Override
    @Transactional(readOnly = true)
    public Page<BudgetRepartitionItemDTO> getBudgetRepartition(Long budgetId, Integer page, Integer size) {

        // Récupère le budget, erreur si introuvable
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        // Vérifie que ce budget appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!budget.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce budget");
        }

        // Récupère tous les lots de la résidence, pour connaître les copropriétaires et leurs tantièmes
        List<Property> properties = propertyRepository.findByResidenceId(budget.getResidence().getId());

        // Regroupe les propriétés par copropriétaire (un copropriétaire peut avoir plusieurs lots)
        Map<Long, List<Property>> propertiesByOwner = new HashMap<>();
        for (Property property : properties) {
            if (property.getOwner() == null) continue; // ignore les lots vacants

            Long ownerId = property.getOwner().getId();
            propertiesByOwner.computeIfAbsent(ownerId, k -> new ArrayList<>()).add(property);
        }

        // Construit une ligne de répartition par copropriétaire
        List<BudgetRepartitionItemDTO> repartition = new ArrayList<>();

        for (Map.Entry<Long, List<Property>> entry : propertiesByOwner.entrySet()) {
            List<Property> ownerProperties = entry.getValue();
            User owner = ownerProperties.get(0).getOwner();

            // Additionne les tantièmes de tous ses lots dans cette résidence
            BigDecimal totalTantieme = BigDecimal.ZERO;
            for (Property p : ownerProperties) {
                totalTantieme = totalTantieme.add(p.getTantieme());
            }

            // Construit la liste des appartements séparés par virgules
            String propertiesLabel = ownerProperties.stream()
                    .map(Property::getReference)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("");

            // Calcule sa quote-part du budget total, proportionnelle à son tantième
            BigDecimal quotePart = budget.getBudgetTotal()
                    .multiply(totalTantieme)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

            BudgetRepartitionItemDTO dto = new BudgetRepartitionItemDTO();
            dto.setCoOwnerName(owner.getFirstName() + " " + owner.getLastName());
            dto.setProperties(propertiesLabel);
            dto.setTantieme(totalTantieme);
            dto.setQuotePart(quotePart);

            repartition.add(dto);
        }

        // Pagination manuelle
        int totalElements = repartition.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<BudgetRepartitionItemDTO> pageContent = repartition.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, PageRequest.of(page, size), totalElements);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<BudgetLinkedChargeCallDTO> getBudgetLinkedChargeCalls(Long budgetId, Integer page, Integer size) {

        // Récupère le budget, erreur si introuvable
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        // Vérifie que ce budget appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!budget.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce budget");
        }

        // Récupère directement les ChargeCall liés (relation déjà présente sur Budget)
        List<ChargeCall> chargeCalls = budget.getChargeCalls();

        // Transforme chaque ChargeCall en DTO, avec son statut recalculé à la volée
        List<BudgetLinkedChargeCallDTO> dtos = chargeCalls.stream()
                .map(this::buildBudgetLinkedChargeCallDto)
                .toList();

        // Pagination manuelle
        int totalElements = dtos.size();
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<BudgetLinkedChargeCallDTO> pageContent = dtos.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, PageRequest.of(page, size), totalElements);
    }

    @Override
    @Transactional
    public void closeBudget(Long budgetId) {

        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        User currentSyndic = getCurrentUser();
        if (!budget.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à clôturer ce budget");
        }

        if (budget.getStatus() == BudgetStatus.CLOSED) {
            throw new BadRequestException("Ce budget est déjà clôturé");
        }

        budget.setStatus(BudgetStatus.CLOSED);
        budgetRepository.save(budget);

        // ⬇️ AJOUT — Trace la clôture dans le journal d'activité
        ActivityLog log = new ActivityLog();
        log.setResidence(budget.getResidence());
        log.setType(ActivityType.BUDGET_CLOSED);
        log.setRelatedEntityType("BUDGET");
        log.setRelatedEntityId(budget.getId());
        log.setActor(currentSyndic);
        log.setCreatedAt(LocalDateTime.now());
        log.setMessage("Budget clôturé — " + budget.getReference());
        activityLogRepository.save(log);
    }

    @Override
    @Transactional
    public void deleteBudget(Long budgetId) {

        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        User currentSyndic = getCurrentUser();
        if (!budget.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à supprimer ce budget");
        }

        if (budget.getStatus() == BudgetStatus.CLOSED) {
            throw new BadRequestException("Impossible de supprimer un budget clôturé");
        }

        // Vérifie si des appels de charges ont été générés pour ce budget
        if (chargeCallRepository.existsByBudgetId(budgetId)) {
            throw new BadRequestException("Impossible de supprimer un budget qui a déjà des appels de charges générés");
        }

        // Supprime d'abord les postes budgétaires associés
        budgetItemRepository.deleteByBudgetId(budgetId);

        // Puis supprime le budget
        budgetRepository.delete(budget);

        // Trace la suppression dans le journal d'activité
        ActivityLog log = new ActivityLog();
        log.setResidence(budget.getResidence());
        log.setType(ActivityType.BUDGET_DELETED);
        log.setRelatedEntityType("BUDGET");
        log.setRelatedEntityId(budgetId);
        log.setActor(currentSyndic);
        log.setCreatedAt(LocalDateTime.now());
        log.setMessage("Budget supprimé — " + budget.getReference());
        activityLogRepository.save(log);
    }

    // ============================================================
    // HISTORIQUE D'UN BUDGET (onglet "Historique")
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Page<HistoryItemDTO> getBudgetHistory(Long budgetId, Integer page, Integer size) {

        // Récupère le budget, erreur si introuvable
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new RuntimeException("Budget non trouvé"));

        // Vérifie que ce budget appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!budget.getResidence().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à ce budget");
        }

        // Récupère tous les événements liés à ce budget, du plus récent au plus ancien avec pagination
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLog> logPage = activityLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc("BUDGET", budgetId, pageable);

        // Transforme chaque log en ligne d'historique
        List<HistoryItemDTO> dtos = logPage.getContent().stream()
                .map(this::buildBudgetHistoryItem)
                .toList();

        return new PageImpl<>(dtos, pageable, logPage.getTotalElements());
    }

    // ============================================================
    // APPEL DE CHARGES
    // ============================================================

    /**
     * Aperçu avant génération d'un appel de charges.
     * Retourne les données calculées sans rien créer en base.
     */
    @Override
    @Transactional(readOnly = true)
    public CurrentBudgetDTO getCurrentBudget(Long residenceId) {
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new RuntimeException("Résidence non trouvée"));

        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        Budget budget = budgetRepository.findByResidenceIdAndStatus(residenceId, BudgetStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Aucun budget actif pour cette résidence"));

        return new CurrentBudgetDTO(budget.getId(), budget.getAnnee(), residence.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ResidenceBudgetSummaryDTO> getResidencesWithActiveBudget(int page, int size) {
        User currentSyndic = getCurrentUser();
        Pageable pageable = PageRequest.of(page, size);

        // Récupérer la fréquence depuis les paramètres du syndic
        ChargeFrequency frequency = syndicFinancialSettingsRepository.findBySyndicId(currentSyndic.getId())
                .map(SyndicFinancialSettings::getChargeFrequency)
                .orElse(ChargeFrequency.TRIMESTRIEL);

        // Déterminer le nombre de périodes et générer la liste
        int numberOfPeriods = (frequency == ChargeFrequency.TRIMESTRIEL) ? 4 : 12;
        List<Integer> availablePeriods = new ArrayList<>();
        for (int i = 1; i <= numberOfPeriods; i++) {
            availablePeriods.add(i);
        }

        // Récupérer les résidences du syndic connecté qui ont un budget actif avec pagination SQL
        Page<Residence> residencePage = residenceRepository.findResidencesWithActiveBudget(
                currentSyndic.getId(), pageable);

        // Récupérer tous les budgets actifs du syndic pour avoir les détails
        List<Budget> activeBudgets = budgetRepository.findBySyndicIdAndStatus(currentSyndic.getId(), BudgetStatus.ACTIVE);

        // Créer une map pour un accès rapide par residenceId
        Map<Long, Budget> budgetByResidenceId = activeBudgets.stream()
                .collect(Collectors.toMap(b -> b.getResidence().getId(), b -> b));

        // Construire le DTO pour chaque résidence
        List<ResidenceBudgetSummaryDTO> dtos = residencePage.getContent().stream()
                .map(residence -> {
                    Budget budget = budgetByResidenceId.get(residence.getId());
                    return ResidenceBudgetSummaryDTO.builder()
                            .id(residence.getId())
                            .residenceName(residence.getName())
                            .referenceBudget(budget.getReference())
                            .anneeBudget(budget.getAnnee())
                            .availablePeriods(availablePeriods)
                            .frequency(frequency)
                            .build();
                })
                .collect(Collectors.toList());

        return new PageImpl<>(dtos, pageable, residencePage.getTotalElements());
    }

    @Override
    @Transactional(readOnly = true)
    public ChargeCallPreviewDTO previewChargeCallByResidence(Long residenceId, Integer periodNumber) {
        // 1. Récupérer le budget actif de la résidence
        Budget budget = budgetRepository.findByResidenceIdAndStatus(residenceId, BudgetStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Aucun budget actif pour cette résidence"));

        // 2. Vérifier l'appartenance au syndic
        User currentSyndic = getCurrentUser();
        if (!budget.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Ce budget ne vous appartient pas");
        }

        // 3. Récupérer la fréquence depuis les paramètres du syndic
        ChargeFrequency frequency = syndicFinancialSettingsRepository.findBySyndicId(currentSyndic.getId())
                .map(SyndicFinancialSettings::getChargeFrequency)
                .orElse(ChargeFrequency.TRIMESTRIEL);

        // 4. Déterminer le nombre de périodes
        int numberOfPeriods = (frequency == ChargeFrequency.TRIMESTRIEL) ? 4 : 12;

        // 5. Vérifier si un appel de charges existe déjà pour cette période pour récupérer les dates
        Optional<ChargeCall> existingChargeCall = chargeCallRepository.findByBudgetIdAndYearAndPeriodNumber(
                budget.getId(), budget.getAnnee(), periodNumber);

        LocalDate sentDate = existingChargeCall.map(ChargeCall::getSentDate).orElse(null);
        LocalDate dueDate = existingChargeCall.map(ChargeCall::getDueDate).orElse(null);

        // 6. Calculer le montant total de la période
        BigDecimal totalAmount = budget.getBudgetTotal()
                .divide(BigDecimal.valueOf(numberOfPeriods), 2, RoundingMode.HALF_UP);

        // 7. Construire la répartition par copropriétaire (calcul automatique selon tantièmes)
        List<Property> properties = propertyRepository.findByResidenceId(residenceId);
        List<CoOwnerQuotePartPreviewDTO> repartition = new ArrayList<>();
        BigDecimal totalTantieme = BigDecimal.ZERO;

        for (Property property : properties) {
            if (property.getOwner() != null) {
                boolean alreadyAdded = repartition.stream()
                        .anyMatch(item -> item.getCoOwnerId().equals(property.getOwner().getId()));

                if (!alreadyAdded) {
                    BigDecimal tantiemeCoOwner = properties.stream()
                            .filter(p -> p.getOwner() != null && p.getOwner().getId().equals(property.getOwner().getId()))
                            .map(p -> p.getTantieme() != null ? p.getTantieme() : BigDecimal.ZERO)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    BigDecimal quotePart = totalAmount.multiply(tantiemeCoOwner)
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

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

        return ChargeCallPreviewDTO.builder()
                .budgetId(budget.getId())
                .budgetReference("BUD-" + budget.getAnnee())
                .residenceName(budget.getResidence().getName())
                .year(budget.getAnnee())
                .periodNumber(periodNumber)
                .totalAmount(totalAmount)
                .sentDate(sentDate)
                .dueDate(dueDate)
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
    public void generateChargeCall(Long residenceId, GenerateChargeCallDTO dto) {
        // 1. Récupérer le budget actif de la résidence
        Budget budget = budgetRepository.findByResidenceIdAndStatus(residenceId, BudgetStatus.ACTIVE)
                .orElseThrow(() -> new RuntimeException("Aucun budget actif pour cette résidence"));

        // 2. Vérifier l'appartenance au syndic
        User currentSyndic = getCurrentUser();
        if (!budget.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Ce budget ne vous appartient pas");
        }

        // 3. Vérifier qu'aucun ChargeCall n'existe déjà pour cette période
        chargeCallRepository.findByBudgetIdAndYearAndPeriodNumber(budget.getId(), budget.getAnnee(), dto.getPeriodNumber())
                .ifPresent(chargeCall -> {
                    throw new RuntimeException("Un appel de charges existe déjà pour cette période");
                });

        // 3. Récupérer la fréquence et recalculer le montant total (jamais faire confiance au front)
        // Repli sur la fréquence par défaut (TRIMESTRIEL) si aucun paramètre n'est persisté.
        ChargeFrequency frequency = syndicFinancialSettingsRepository.findBySyndicId(currentSyndic.getId())
                .map(SyndicFinancialSettings::getChargeFrequency)
                .orElse(ChargeFrequency.TRIMESTRIEL);

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

        String reference = "APP-" + "-T" + dto.getPeriodNumber() + budget.getAnnee()  + "-B" + budget.getId();
        chargeCall.setReference(reference);

        // 5. Créer les ChargeCallItem pour chaque copropriétaire (fusionné par owner)
        List<Property> properties = propertyRepository.findByResidenceId(budget.getResidence().getId());
        List<ChargeCallItem> items = new ArrayList<>();

        // Priorité : si customAmounts est fourni, l'utiliser (peu importe le mode du budget)
        if (dto.getCustomAmounts() != null && !dto.getCustomAmounts().isEmpty()) {
            // Mode CUSTOM — le syndic a saisi manuellement chaque montant
            // Validation : la somme des montants personnalisés doit être exactement égale au total de la période
            BigDecimal customTotal = dto.getCustomAmounts().stream()
                    .map(CustomCoOwnerAmountDTO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (customTotal.compareTo(totalAmount) != 0) {
                throw new BadRequestException(
                        "La somme des montants personnalisés (" + customTotal + " FCFA) doit être exactement égale au total de la période (" + totalAmount + " FCFA)"
                );
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

                String referenceCCI = "APPI-" + dto.getPeriodNumber() + budget.getAnnee() + "-" + "-" + coOwner.getId();

                ChargeCallItem item = new ChargeCallItem();
                item.setChargeCall(chargeCall);
                item.setReference(referenceCCI);
                item.setCoOwner(coOwner);
                item.setTantieme(tantiemeCoOwner);
                item.setQuotePart(customAmount.getAmount()); // montant saisi manuellement, pas calculé
                item.setPaidAmount(BigDecimal.ZERO);
                item.setRemainingAmount(customAmount.getAmount());

                items.add(item);
            }
        } else if (budget.getRepartitionMode() == RepartitionMode.CUSTOM) {
            // Mode CUSTOM du budget mais aucun customAmount fourni
            throw new BadRequestException("Saisissez un montant pour chaque copropriétaire en mode Personnalisée");
        } else {
            // Mode OWNERSHIP_SHARES — calcul automatique selon les tantièmes
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
                                .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

                        // Générer la référence
                        String referenceCCI = "APPI-" + dto.getPeriodNumber() + budget.getAnnee() + "-"  + "-" + property.getOwner().getId();

                        ChargeCallItem item = new ChargeCallItem();
                        item.setChargeCall(chargeCall);
                        item.setReference(referenceCCI);
                        item.setCoOwner(property.getOwner());
                        item.setTantieme(tantiemeCoOwner);
                        item.setQuotePart(quotePart);
                        item.setPaidAmount(BigDecimal.ZERO);
                        item.setRemainingAmount(quotePart);

                        items.add(item);
                    }
                }
            }
        }

        chargeCall.setItems(items);
        ChargeCall savedChargeCall = chargeCallRepository.save(chargeCall);

        // Trace la génération de cet appel de charges dans le journal d'activité de la résidence
        ActivityLog log = new ActivityLog();
        log.setResidence(budget.getResidence());
        log.setType(ActivityType.CHARGE_CALL_GENERATED);
        log.setRelatedEntityType("CHARGE_CALL");
        log.setRelatedEntityId(savedChargeCall.getId());
        log.setActor(currentSyndic);
        log.setCreatedAt(LocalDateTime.now());
        log.setMessage("Appel de charges généré — " + savedChargeCall.getReference());
        activityLogRepository.save(log);

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

    // ============================================================
  // LISTE DES APPELS DE CHARGES DU SYNDIC (toutes résidences)
  // ============================================================

    @Override
    @Transactional(readOnly = true)
    public ChargeCallListResponse getChargeCallsForSyndic(int page, int size) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Construit la pagination, triée du plus récent au plus ancien
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Récupère les ChargeCall dont le budget appartient au syndic connecté
        Page<ChargeCall> chargeCallPage = chargeCallRepository.findByBudgetSyndicId(currentSyndic.getId(), pageable);

        // Transforme chaque ChargeCall en carte
        List<ChargeCallCardDTO> cardDtos = chargeCallPage.getContent().stream()
                .map(this::buildChargeCallCard)
                .toList();

        // Assemble la réponse finale
        ChargeCallListResponse response = new ChargeCallListResponse();
        response.setTotalChargeCalls((int) chargeCallPage.getTotalElements());
        response.setChargeCalls(cardDtos);
        response.setCurrentPage(page);
        response.setTotalPages(chargeCallPage.getTotalPages());

        return response;
    }

    // ============================================================
    // DÉTAIL D'UN APPEL DE CHARGES (KPIs + suivi par copropriétaire)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public ChargeCallDetailDTO getChargeCallDetail(Long chargeCallId) {

        // Récupère l'appel de charges, erreur si introuvable
        ChargeCall chargeCall = chargeCallRepository.findById(chargeCallId)
                .orElseThrow(() -> new RuntimeException("Appel de charges non trouvé"));

        // Vérifie que cet appel appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!chargeCall.getBudget().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cet appel de charges");
        }

        // Récupère toutes les propriétés de la résidence, pour associer les appartements à chaque copropriétaire
        List<Property> properties = propertyRepository.findByResidenceId(chargeCall.getBudget().getResidence().getId());

        // Calcule le total encaissé (somme des montants payés par tous les copropriétaires)
        BigDecimal totalCollected = chargeCall.getItems().stream()
                .map(ChargeCallItem::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calcule le solde restant
        BigDecimal remainingBalance = chargeCall.getTotalAmount().subtract(totalCollected);

        // Construction du DTO principal
        ChargeCallDetailDTO dto = new ChargeCallDetailDTO();
        dto.setId(chargeCall.getId());
        dto.setReference(chargeCall.getReference());
        dto.setResidenceName(chargeCall.getBudget().getResidence().getName());
        dto.setPeriodLabel(buildPeriodLabel(chargeCall));
        dto.setTotalAmount(chargeCall.getTotalAmount());
        dto.setTotalCollected(totalCollected);
        dto.setRemainingBalance(remainingBalance);
        dto.setStatus(calculateChargeCallStatus(chargeCall).name());
        dto.setBudgetReference(chargeCall.getBudget().getReference());
        dto.setCollectedPercentage(calculatePercentage(totalCollected, chargeCall.getTotalAmount()));
        dto.setSentDate(chargeCall.getSentDate());
        dto.setDueDate(chargeCall.getDueDate());

        // Construction du tableau "Suivi par copropriétaire"
        List<ChargeCallItemDetailDTO> itemDtos = chargeCall.getItems().stream()
                .map(item -> buildChargeCallItemDetail(item, properties))
                .toList();
        dto.setItems(itemDtos);

        return dto;
    }

     // ============================================================
     // RELANCER UN APPEL DE CHARGES
    // ============================================================

    @Override
    @Transactional
    public int remindChargeCall(Long chargeCallId) {

        // Récupérer l'appel de charges, erreur si introuvable
        ChargeCall chargeCall = chargeCallRepository.findById(chargeCallId)
                .orElseThrow(() -> new RuntimeException("Appel de charges non trouvé"));

        // Vérifier que cet appel appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!chargeCall.getBudget().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à relancer cet appel de charges");
        }

        // Lecture directe du statut calculé à la volée
        ChargeCallStatus status = calculateChargeCallStatus(chargeCall);
        if (status == ChargeCallStatus.SETTLED) {
            throw new BadRequestException("Impossible de relancer : cet appel de charges est déjà soldé");
        }

        // Filtre les copropriétaires qui doivent encore de l'argent
        List<ChargeCallItem> unpaidItems = chargeCall.getItems().stream()
                .filter(item -> item.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        // Envoie email + notification push à chacun (non-bloquant)
        for (ChargeCallItem item : unpaidItems) {
            try {
                String subject = "Rappel — Appel de charges " + buildPeriodLabel(chargeCall);
                String body = buildReminderEmailBody(chargeCall, item);
                emailService.sendEmail(item.getCoOwner().getEmail(), subject, body);
            } catch (Exception e) {
                System.err.println("Erreur envoi email de relance à " + item.getCoOwner().getEmail() + ": " + e.getMessage());
            }

            try {
                if (item.getCoOwner().isNotificationsEnabled()) {
                    String title = "Rappel de paiement";
                    String body = buildPeriodLabel(chargeCall) + " — solde restant: " + item.getRemainingAmount() + " FCFA";
                    notificationService.sendPush(item.getCoOwner().getId(), title, body);
                }
            } catch (Exception e) {
                System.err.println("Erreur envoi notification de relance à " + item.getCoOwner().getId() + ": " + e.getMessage());
            }
        }

        return unpaidItems.size();
    }

    @Override
    @Transactional
    public void deleteChargeCall(Long chargeCallId) {
        // Récupérer l'appel de charges, erreur si introuvable
        ChargeCall chargeCall = chargeCallRepository.findById(chargeCallId)
                .orElseThrow(() -> new RuntimeException("Appel de charges non trouvé"));

        // Vérifier que cet appel appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!chargeCall.getBudget().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à supprimer cet appel de charges");
        }

        // Vérifier qu'aucun paiement COMPLETED n'a été effectué
        boolean hasCompletedPayments = chargeCall.getItems().stream()
                .anyMatch(item -> item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0);
        if (hasCompletedPayments) {
            throw new BadRequestException("Impossible de supprimer : des paiements ont déjà été effectués sur cet appel de charges");
        }

        // Supprimer les paiements d'abord (PENDING)
        chargeCallPaymentRepository.deleteByChargeCallId(chargeCallId);

        // Supprimer les items ensuite
        chargeCallItemRepository.deleteByChargeCallId(chargeCallId);

        // Supprimer l'appel de charges
        chargeCallRepository.delete(chargeCall);

        log.info("Appel de charges {} supprimé par le syndic {}", chargeCallId, currentSyndic.getId());
    }

    @Override
    @Transactional
    public void deleteExceptionalCall(Long exceptionalCallId) {
        // Récupérer l'appel exceptionnel, erreur si introuvable
        ExceptionalCall exceptionalCall = exceptionalCallRepository.findById(exceptionalCallId)
                .orElseThrow(() -> new RuntimeException("Appel exceptionnel non trouvé"));

        // Vérifier que cet appel appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!exceptionalCall.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à supprimer cet appel exceptionnel");
        }

        // Vérifier qu'aucun paiement COMPLETED n'a été effectué
        boolean hasCompletedPayments = exceptionalCall.getItems().stream()
                .anyMatch(item -> item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0);
        if (hasCompletedPayments) {
            throw new BadRequestException("Impossible de supprimer : des paiements ont déjà été effectués sur cet appel exceptionnel");
        }

        // Supprimer les paiements d'abord (PENDING) - seulement s'il y en a
        if (!exceptionalCall.getItems().isEmpty()) {
            exceptionalCallPaymentRepository.deleteByExceptionalCallId(exceptionalCallId);
        }

        // Supprimer les items ensuite - seulement s'il y en a
        if (!exceptionalCall.getItems().isEmpty()) {
            exceptionalCallItemRepository.deleteByExceptionalCallId(exceptionalCallId);
        }

        // Supprimer les documents - seulement s'il y en a
        if (!exceptionalCall.getDocuments().isEmpty()) {
            exceptionalCallDocumentRepository.deleteByExceptionalCallId(exceptionalCallId);
        }

        // Supprimer l'appel exceptionnel
        exceptionalCallRepository.delete(exceptionalCall);

        log.info("Appel exceptionnel {} supprimé par le syndic {}", exceptionalCallId, currentSyndic.getId());
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
        exceptionalCall.setStatus(ExceptionalCallStatus.DRAFT);

        // Étape 1 — Sauvegarder une première fois SANS référence, pour obtenir un ID unique
        // généré par la base de données (auto-increment, garanti sans collision)
        ExceptionalCall saved = exceptionalCallRepository.save(exceptionalCall);

        // Étape 2 — Construire la référence à partir de cet ID unique, puis sauvegarder à nouveau
        String reference = String.format("EXC-%d-%03d", LocalDate.now().getYear(), saved.getId());
        saved.setReference(reference);
        saved = exceptionalCallRepository.save(saved);

        // Trace la création dans le journal d'activité
        ActivityLog log = new ActivityLog();
        log.setResidence(residence);
        log.setType(ActivityType.EXCEPTIONAL_CALL_CREATED);
        log.setRelatedEntityType("EXCEPTIONAL_CALL");
        log.setRelatedEntityId(saved.getId());
        log.setActor(currentSyndic);
        log.setCreatedAt(LocalDateTime.now());
        log.setMessage("Appel exceptionnel créé — " + saved.getTitle());
        activityLogRepository.save(log);


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

        // Priorité : si customAmounts est fourni, l'utiliser (peu importe le mode)
        if (dto.getCustomAmounts() != null && !dto.getCustomAmounts().isEmpty()) {
            // Mode CUSTOM — le syndic a saisi manuellement chaque montant
            // Validation : la somme des montants personnalisés doit être exactement égale au total de l'appel exceptionnel
            BigDecimal customTotal = dto.getCustomAmounts().stream()
                    .map(CustomCoOwnerAmountDTO::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (customTotal.compareTo(dto.getTotalAmount()) != 0) {
                throw new BadRequestException(
                        "La somme des montants personnalisés (" + customTotal + " FCFA) doit être exactement égale au total de l'appel exceptionnel (" + dto.getTotalAmount() + " FCFA)"
                );
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
        } else if (dto.getRepartitionMode() == RepartitionMode.OWNERSHIP_SHARES) {

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
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);

                ExceptionalCallItem item = new ExceptionalCallItem();
                item.setExceptionalCall(exceptionalCall);
                item.setCoOwner(property.getOwner());
                item.setTantieme(tantiemeCoOwner);
                item.setQuotePart(quotePart);
                exceptionalCall.getItems().add(item);

                alreadyAddedOwnerIds.add(ownerId);
            }

        } else {
            // Mode CUSTOM du DTO mais aucun customAmount fourni
            throw new BadRequestException("Saisissez un montant pour chaque copropriétaire en mode Personnalisée");
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

        // requiresAgValidation est PUREMENT INFORMATIF (juste un badge affiché à l'écran) — il ne
        // bloque jamais le statut. Peu importe sa valeur.
        exceptionalCall.setRequiresAgValidation(requiresAgValidation);
        exceptionalCall.setStatus(ExceptionalCallStatus.ACTIVE);
        exceptionalCall.setActivatedAt(LocalDateTime.now());

        ExceptionalCall saved = exceptionalCallRepository.save(exceptionalCall);

        // Trace l'activation dans le journal d'activité
        ActivityLog log = new ActivityLog();
        log.setResidence(saved.getResidence());
        log.setType(ActivityType.EXCEPTIONAL_CALL_ACTIVATED);
        log.setRelatedEntityType("EXCEPTIONAL_CALL");
        log.setRelatedEntityId(saved.getId());
        log.setActor(currentSyndic);
        log.setMessage("Appel exceptionnel activé — " + saved.getReference());
        log.setCreatedAt(saved.getActivatedAt());
        activityLogRepository.save(log);

        // Envoi des emails et notifications push aux copropriétaires concernés (non-bloquant)
        for (ExceptionalCallItem item : saved.getItems()) {
            try {
                // Email
                String subject = "Appel exceptionnel — " + saved.getResidence().getName();
                String body = buildExceptionalCallEmailBody(saved, item);
                emailService.sendEmail(item.getCoOwner().getEmail(), subject, body);
            } catch (Exception e) {
                // Log l'erreur mais ne pas bloquer l'activation
                System.err.println("Erreur envoi email à " + item.getCoOwner().getEmail() + ": " + e.getMessage());
            }

            try {
                // Notification push si activée
                if (item.getCoOwner().isNotificationsEnabled()) {
                    String title = "Appel exceptionnel";
                    String body = saved.getTitle() + " — " + saved.getResidence().getName();
                    notificationService.sendPush(item.getCoOwner().getId(), title, body);
                }
            } catch (Exception e) {
                // Log l'erreur mais ne pas bloquer l'activation
                System.err.println("Erreur envoi notification à " + item.getCoOwner().getId() + ": " + e.getMessage());
            }
        }

        return toExceptionalCallDetailDTO(saved);
    }
    // Construit le corps de l'email envoyé à un copropriétaire lors de l'activation d'un appel exceptionnel
    private String buildExceptionalCallEmailBody(ExceptionalCall exceptionalCall, ExceptionalCallItem item) {
        StringBuilder body = new StringBuilder();
        body.append("Appel exceptionnel : ").append(exceptionalCall.getTitle()).append("\n\n");
        body.append("Résidence : ").append(exceptionalCall.getResidence().getName()).append("\n");
        body.append("Description : ").append(exceptionalCall.getDescription()).append("\n");
        body.append("Montant à votre charge : ").append(item.getQuotePart()).append(" FCFA\n");
        return body.toString();
    }
    // ============================================================
    // LISTE DES APPELS EXCEPTIONNELS DU SYNDIC
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public ExceptionalCallListResponse getExceptionalCallsForSyndic(int page, int size) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Construit la pagination, triée du plus récent au plus ancien
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());

        // Récupère les appels exceptionnels dont le syndic est celui connecté
        Page<ExceptionalCall> exceptionalCallPage = exceptionalCallRepository.findBySyndicId(currentSyndic.getId(), pageable);

        // Transforme chaque appel exceptionnel en carte
        List<ExceptionalCallCardDTO> cardDtos = exceptionalCallPage.getContent().stream()
                .map(this::buildExceptionalCallCard)
                .toList();

        // Assemble la réponse finale avec les infos de pagination
        ExceptionalCallListResponse response = new ExceptionalCallListResponse();
        response.setTotalExceptionalCalls((int) exceptionalCallPage.getTotalElements());
        response.setExceptionalCalls(cardDtos);
        response.setCurrentPage(page);
        response.setTotalPages(exceptionalCallPage.getTotalPages());

        return response;
    }

    // Construit la carte d'un appel exceptionnel (montant, collecté, restant, pourcentage)
    private ExceptionalCallCardDTO buildExceptionalCallCard(ExceptionalCall exceptionalCall) {

        // Calcule le montant déjà collecté et le solde restant
        BigDecimal collectedAmount = calculateCollectedAmount(exceptionalCall);
        BigDecimal remainingAmount = exceptionalCall.getTotalAmount() != null
                ? exceptionalCall.getTotalAmount().subtract(collectedAmount)
                : BigDecimal.ZERO;

        ExceptionalCallCardDTO dto = new ExceptionalCallCardDTO();
        dto.setId(exceptionalCall.getId());
        dto.setReference(exceptionalCall.getReference());
        dto.setTitle(exceptionalCall.getTitle());
        dto.setStatus(exceptionalCall.getStatus().name());
        dto.setResidenceName(exceptionalCall.getResidence().getName());
        dto.setTotalAmount(exceptionalCall.getTotalAmount());
        dto.setCollectedAmount(collectedAmount);
        dto.setRemainingAmount(remainingAmount);
        dto.setCollectedPercentage(calculatePercentage(collectedAmount, exceptionalCall.getTotalAmount()));
        dto.setCoOwnersCount(exceptionalCall.getItems().size());
        dto.setActivatedAt(exceptionalCall.getActivatedAt());

        return dto;
    }

    // Calcule le montant total déjà collecté sur un appel exceptionnel (somme des paiements de tous les items)
    private BigDecimal calculateCollectedAmount(ExceptionalCall exceptionalCall) {
        return exceptionalCall.getItems().stream()
                .map(ExceptionalCallItem::getPaidAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // ============================================================
    // VUE D'ENSEMBLE D'UN APPEL EXCEPTIONNEL (onglet 1 — pas de pagination, un seul objet)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public ExceptionalCallOverviewDTO getExceptionalCallOverview(Long exceptionalCallId) {

        // Récupère l'appel exceptionnel et vérifie qu'il appartient bien au syndic connecté
        ExceptionalCall exceptionalCall = getExceptionalCallForSyndic(exceptionalCallId);

        BigDecimal collectedAmount = calculateCollectedAmount(exceptionalCall);
        BigDecimal remainingAmount = exceptionalCall.getTotalAmount() != null
                ? exceptionalCall.getTotalAmount().subtract(collectedAmount)
                : BigDecimal.ZERO;

        ExceptionalCallOverviewDTO dto = new ExceptionalCallOverviewDTO();
        dto.setId(exceptionalCall.getId());
        dto.setReference(exceptionalCall.getReference());
        dto.setStatus(exceptionalCall.getStatus().name());
        dto.setResidenceName(exceptionalCall.getResidence().getName());
        dto.setTitle(exceptionalCall.getTitle());
        dto.setCategory(exceptionalCall.getCategory().name());
        dto.setDescription(exceptionalCall.getDescription());
        dto.setRepartitionModeLabel(exceptionalCall.getRepartitionMode() != null
                ? exceptionalCall.getRepartitionMode().name() : null);
        dto.setTotalAmount(exceptionalCall.getTotalAmount());
        dto.setCollectedAmount(collectedAmount);
        dto.setRemainingAmount(remainingAmount);
        dto.setCollectedPercentage(calculatePercentage(collectedAmount, exceptionalCall.getTotalAmount()));
        dto.setCoOwnersCount(exceptionalCall.getItems().size());
        dto.setCreatedAt(exceptionalCall.getCreatedAt());

        return dto;
    }

    // ============================================================
    // RÉPARTITION D'UN APPEL EXCEPTIONNEL (onglet 2, paginée directement en base)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ExceptionalCallItemDetailDTO> getExceptionalCallRepartition(Long exceptionalCallId, int page, int size) {

        // Vérifie que l'appel appartient bien au syndic connecté
        ExceptionalCall exceptionalCall = getExceptionalCallForSyndic(exceptionalCallId);

        Pageable pageable = PageRequest.of(page, size);

        // Requête paginée directement en base : ne charge que les lignes de la page demandée
        Page<ExceptionalCallItem> itemsPage = exceptionalCallItemRepository
                .findByExceptionalCallId(exceptionalCallId, pageable);

        // Récupère les propriétés de la résidence, pour construire les libellés d'appartements
        List<Property> properties = propertyRepository.findByResidenceId(exceptionalCall.getResidence().getId());

        // Transforme chaque item de la page en DTO
        return itemsPage.map(item -> buildExceptionalCallItemDetail(item, properties));
    }

    // Construit une ligne du tableau "Répartition par copropriétaire"
    private ExceptionalCallItemDetailDTO buildExceptionalCallItemDetail(ExceptionalCallItem item, List<Property> properties) {

        ExceptionalCallItemDetailDTO dto = new ExceptionalCallItemDetailDTO();
        dto.setCoOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName());

        // Récupère tous les appartements de ce copropriétaire dans la résidence, séparés par virgules
        String propertiesLabel = properties.stream()
                .filter(p -> p.getOwner() != null && p.getOwner().getId().equals(item.getCoOwner().getId()))
                .map(Property::getReference)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        dto.setProperties(propertiesLabel);

        dto.setTantieme(item.getTantieme());
        dto.setQuotePart(item.getQuotePart());
        dto.setPaidAmount(item.getPaidAmount());

        // Calcule le solde restant à payer
        BigDecimal remainingAmount = item.getQuotePart().subtract(item.getPaidAmount());
        dto.setRemainingAmount(remainingAmount);

        // Détermine le statut individuel de ce copropriétaire
        if (item.getPaidAmount().compareTo(item.getQuotePart()) >= 0) {
            dto.setStatus("PAYE");
        } else if (item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            dto.setStatus("PARTIEL");
        } else {
            dto.setStatus("IMPAYE");
        }

        return dto;
    }

    // ============================================================
    // PAIEMENTS D'UN APPEL EXCEPTIONNEL (onglet 3, paginée directement en base)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ExceptionalCallPaymentDTO> getExceptionalCallPayments(Long exceptionalCallId, int page, int size) {

        // Vérifie que l'appel appartient bien au syndic connecté
        getExceptionalCallForSyndic(exceptionalCallId);

        Pageable pageable = PageRequest.of(page, size);

        // Requête paginée directement en base, filtrée sur les paiements COMPLETED uniquement
        Page<ExceptionalCallPayment> paymentsPage = exceptionalCallPaymentRepository
                .findByExceptionalCallItemExceptionalCallIdAndStatus(exceptionalCallId, PaymentStatus.COMPLETED, pageable);

        return paymentsPage.map(this::buildExceptionalCallPaymentDto);
    }

    // Construit une ligne du tableau "Paiements reçus"
    private ExceptionalCallPaymentDTO buildExceptionalCallPaymentDto(ExceptionalCallPayment payment) {

        ExceptionalCallPaymentDTO dto = new ExceptionalCallPaymentDTO();
        dto.setCoOwnerName(payment.getOwner().getFirstName() + " " + payment.getOwner().getLastName());
        dto.setPaidAt(payment.getPaidAt());
        dto.setAmount(payment.getAmount());
        dto.setMethod(payment.getMethod() != null ? payment.getMethod().name() : null);
        dto.setReference(payment.getReference());

        return dto;
    }

    // ============================================================
    // DOCUMENTS D'UN APPEL EXCEPTIONNEL (onglet 4, paginée directement en base)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ExceptionalCallDocumentDTO> getExceptionalCallDocuments(Long exceptionalCallId, int page, int size) {

        // Vérifie que l'appel appartient bien au syndic connecté
        getExceptionalCallForSyndic(exceptionalCallId);

        Pageable pageable = PageRequest.of(page, size);

        // Requête paginée directement en base
        Page<ExceptionalCallDocument> documentsPage = exceptionalCallDocumentRepository
                .findByExceptionalCallId(exceptionalCallId, pageable);

        return documentsPage.map(this::buildExceptionalCallDocumentDto);
    }

    // Construit un DTO document, avec extension et taille calculées à partir du nom de fichier
    private ExceptionalCallDocumentDTO buildExceptionalCallDocumentDto(ExceptionalCallDocument document) {

        String fileName = document.getFileName();

        // Extrait l'extension du fichier à partir de son nom (ex: "facture.pdf" → "pdf")
        String extension = fileName != null && fileName.contains(".")
                ? fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase()
                : "";

        // Convertit la taille stockée en Ko vers Mo pour l'affichage
        Double fileSizeMb = document.getFileSizeKb() != null
                ? document.getFileSizeKb() / 1024.0
                : 0.0;

        ExceptionalCallDocumentDTO dto = ExceptionalCallDocumentDTO.builder()
                .id(document.getId())
                .fileName(fileName)
                .fileUrl(document.getFileUrl())
                .fileSizeMb(fileSizeMb)
                .fileExtension(extension)
                .createdAt(document.getCreatedAt())
                .build();
        return dto;
    }

    // ============================================================
    // HISTORIQUE D'UN APPEL EXCEPTIONNEL (onglet 5, paginée directement en base)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Page<ExceptionalCallHistoryDTO> getExceptionalCallHistory(Long exceptionalCallId, int page, int size) {

        // Vérifie que l'appel appartient bien au syndic connecté
        ExceptionalCall exceptionalCall = getExceptionalCallForSyndic(exceptionalCallId);

        Pageable pageable = PageRequest.of(page, size);

        // Requête paginée directement en base, triée du plus récent au plus ancien
        Page<ActivityLog> logsPage = activityLogRepository
                .findByRelatedEntityTypeAndRelatedEntityIdOrderByCreatedAtDesc("EXCEPTIONAL_CALL", exceptionalCallId, pageable);

        // Transforme chaque log en ligne d'historique
        return logsPage.map(log -> {
            ExceptionalCallHistoryDTO dto = new ExceptionalCallHistoryDTO();
            dto.setActorName(log.getActor() != null
                    ? log.getActor().getFirstName() + " " + log.getActor().getLastName() : "Système");
            dto.setStatusBadge(exceptionalCall.getStatus().name());
            dto.setMessage(log.getMessage());
            dto.setCreatedAt(log.getCreatedAt());
            return dto;
        });
    }

    // ============================================================
    // CLÔTURER UN APPEL EXCEPTIONNEL
    // ============================================================

    @Override
    @Transactional
    public void closeExceptionalCall(Long exceptionalCallId) {

        // Récupère l'appel exceptionnel et vérifie qu'il appartient bien au syndic connecté
        ExceptionalCall exceptionalCall = getExceptionalCallForSyndic(exceptionalCallId);
        User currentSyndic = getCurrentUser();

        // Empêche de clôturer un appel déjà clôturé
        if (exceptionalCall.getStatus() == ExceptionalCallStatus.CLOSED) {
            throw new BadRequestException("Cet appel exceptionnel est déjà clôturé");
        }

        exceptionalCall.setStatus(ExceptionalCallStatus.CLOSED);
        exceptionalCallRepository.save(exceptionalCall);

        // Trace la clôture dans le journal d'activité de la résidence
        ActivityLog log = new ActivityLog();
        log.setResidence(exceptionalCall.getResidence());
        log.setType(ActivityType.EXCEPTIONAL_CALL_CLOSED);
        log.setRelatedEntityType("EXCEPTIONAL_CALL");
        log.setRelatedEntityId(exceptionalCall.getId());
        log.setActor(currentSyndic);
        log.setCreatedAt(LocalDateTime.now());
        log.setMessage("Appel exceptionnel clôturé — " + exceptionalCall.getReference());
        activityLogRepository.save(log);
    }

    // ============================================================
    // LISTE DES PAIEMENTS (global syndic, toutes résidences confondues)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public PaymentListResponse getPaymentsForSyndic(int page, int size, String search) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size, Sort.by("chargeCall.createdAt").descending());

        // Récupère les items, filtrés par recherche si fournie
        Page<ChargeCallItem> itemsPage = (search != null && !search.isBlank())
                ? chargeCallItemRepository.findByChargeCallBudgetSyndicIdAndCoOwnerNameContaining(currentSyndic.getId(), search, pageable)
                : chargeCallItemRepository.findByChargeCallBudgetSyndicId(currentSyndic.getId(), pageable);

        List<PaymentRowDTO> rowDtos = itemsPage.getContent().stream()
                .map(this::buildPaymentRow)
                .toList();

        PaymentListResponse response = new PaymentListResponse();
        response.setTotalPayments((int) itemsPage.getTotalElements());
        response.setPayments(rowDtos);
        response.setCurrentPage(page);
        response.setTotalPages(itemsPage.getTotalPages());

        return response;
    }

    // Construit une ligne du tableau "Paiements"
    private PaymentRowDTO buildPaymentRow(ChargeCallItem item) {

        PaymentRowDTO dto = new PaymentRowDTO();
        dto.setCoOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName());
        dto.setPropertyLabel(buildPropertyLabel(item));
        dto.setAmountDue(item.getQuotePart());
        dto.setAmountPaid(item.getPaidAmount());
        dto.setBalance(item.getRemainingAmount());
        dto.setStatus(calculateItemStatus(item));

        // Récupère la date du dernier paiement effectué sur cet item, s'il existe
        chargeCallPaymentRepository.findFirstByChargeCallItemIdOrderByPaidAtDesc(item.getId())
                .ifPresent(p -> dto.setPaymentDate(p.getPaidAt() != null ? p.getPaidAt().toLocalDate() : null));

        return dto;
    }

    // ============================================================
    // LISTE DES IMPAYÉS (global syndic)
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public UnpaidListResponse getUnpaidForSyndic(int page, int size) {

        User currentSyndic = getCurrentUser();

        Pageable pageable = PageRequest.of(page, size, Sort.by("chargeCall.dueDate").ascending());

        // Récupère la page demandée, directement filtrée en base sur les items non soldés
        Page<ChargeCallItem> unpaidPage = chargeCallItemRepository.findUnpaidByBudgetSyndicId(currentSyndic.getId(), pageable);

        // Récupère TOUS les items non soldés (sans pagination), pour calculer les KPI globaux
        List<ChargeCallItem> allUnpaidItems = chargeCallItemRepository.findAllUnpaidByBudgetSyndicId(currentSyndic.getId());

        BigDecimal totalUnpaidAmount = allUnpaidItems.stream()
                .map(ChargeCallItem::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<UnpaidRowDTO> rowDtos = unpaidPage.getContent().stream()
                .map(this::buildUnpaidRow)
                .toList();

        UnpaidListResponse response = new UnpaidListResponse();
        response.setUnpaidCoOwnersCount(allUnpaidItems.size());
        response.setTotalUnpaidAmount(totalUnpaidAmount);
        response.setUnpaidItems(rowDtos);
        response.setCurrentPage(page);
        response.setTotalPages(unpaidPage.getTotalPages());

        return response;
    }

    // Construit une ligne du tableau "Impayés"
    private UnpaidRowDTO buildUnpaidRow(ChargeCallItem item) {

        LocalDate dueDate = item.getChargeCall().getDueDate();
        long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now());

        UnpaidRowDTO dto = new UnpaidRowDTO();
        dto.setChargeCallItemId(item.getId());
        dto.setCoOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName());
        dto.setPropertyLabel(buildPropertyLabel(item));
        dto.setStatus(calculateItemStatus(item));
        dto.setAmountDue(item.getQuotePart());
        dto.setUnpaidBalance(item.getRemainingAmount());
        dto.setDaysLate((int) Math.max(daysLate, 0));

        return dto;
    }

    // ============================================================
    // RELANCER UN COPROPRIÉTAIRE (impayé précis)
    // ============================================================

    @Override
    @Transactional
    public void remindUnpaidItem(Long chargeCallItemId) {

        ChargeCallItem item = chargeCallItemRepository.findById(chargeCallItemId)
                .orElseThrow(() -> new RuntimeException("Ligne de charge introuvable"));

        User currentSyndic = getCurrentUser();
        if (!item.getChargeCall().getBudget().getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à relancer ce copropriétaire");
        }

        if (item.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Cette charge est déjà soldée");
        }

        sendReminderForItem(item);
    }

    // ============================================================
    // RELANCER TOUS LES IMPAYÉS
    // ============================================================

    @Override
    @Transactional
    public int remindAllUnpaid() {

        User currentSyndic = getCurrentUser();

        List<ChargeCallItem> allUnpaidItems = chargeCallItemRepository.findAllUnpaidByBudgetSyndicId(currentSyndic.getId());

        for (ChargeCallItem item : allUnpaidItems) {
            sendReminderForItem(item);
        }

        return allUnpaidItems.size();
    }

    // ============================================================
    // DASHBOARD "GESTION DES CHARGES"
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public ChargeDashboardDTO getChargeDashboard(Long residenceId) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        ChargeDashboardDTO dto = new ChargeDashboardDTO();

        // --- Budget Annuel + Résidences Actives ---

        // Récupère tous les budgets ACTIVE du syndic, toutes résidences confondues
        List<Budget> activeBudgets = budgetRepository.findBySyndicIdAndStatus(currentSyndic.getId(), BudgetStatus.ACTIVE);

        BigDecimal annualBudget = activeBudgets.stream()
                .map(Budget::getBudgetTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setAnnualBudget(annualBudget);
        dto.setActiveResidencesCount(activeBudgets.size());

        // --- Total Appelé ---

        List<ChargeCall> allChargeCalls = chargeCallRepository.findByBudgetSyndicId(currentSyndic.getId());

        BigDecimal totalCalled = allChargeCalls.stream()
                .map(ChargeCall::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setTotalCalled(totalCalled);
        dto.setChargeCallsCount(allChargeCalls.size());
        dto.setTotalCalledEvolutionPercent(calculateChargeCallsEvolution(currentSyndic.getId()));

        // --- Total Encaissé ---

        LocalDateTime finMoisCourant = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime debutAnnee = LocalDate.now().withDayOfYear(1).atStartOfDay();

        BigDecimal totalCollected = chargeCallPaymentRepository
                .sumByBudgetSyndicIdAndPaidAtBetween(currentSyndic.getId(), debutAnnee, finMoisCourant);
        dto.setTotalCollected(totalCollected);
        dto.setTotalCollectedEvolutionPercent(calculateCollectedEvolution(currentSyndic.getId()));

        // --- Impayés ---

        List<ChargeCallItem> allUnpaidItems = chargeCallItemRepository.findAllUnpaidByBudgetSyndicId(currentSyndic.getId());

        BigDecimal unpaidAmount = allUnpaidItems.stream()
                .map(ChargeCallItem::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        dto.setUnpaidAmount(unpaidAmount);

        // Compte les copropriétaires distincts ayant au moins un impayé
        long unpaidCoOwnersCount = allUnpaidItems.stream()
                .map(item -> item.getCoOwner().getId())
                .distinct()
                .count();
        dto.setUnpaidCoOwnersCount((int) unpaidCoOwnersCount);
        dto.setUnpaidEvolutionPercent(calculateUnpaidEvolutionGlobal(currentSyndic.getId()));


        // --- Graphique Encaissement Mensuel (12 mois, Prévu vs Encaissé) ---
        dto.setMonthlyCollection(buildMonthlyCollection(currentSyndic.getId()));

        // --- Camembert Répartition des Postes ---
        Long resolvedResidenceId = residenceId != null ? residenceId : resolveDefaultResidenceForDashboard(currentSyndic.getId());
        dto.setPostesRepartition(buildPostesRepartition(resolvedResidenceId));

        return dto;
    }

    // Calcule l'évolution du total appelé entre le mois dernier et le mois d'avant
    private Double calculateChargeCallsEvolution(Long syndicId) {

        LocalDate now = LocalDate.now();
        LocalDate startLastMonth = now.withDayOfMonth(1).minusMonths(1);
        LocalDate endLastMonth = now.withDayOfMonth(1);
        LocalDate startMonthBefore = startLastMonth.minusMonths(1);

        List<ChargeCall> lastMonthCalls = chargeCallRepository
                .findByBudgetSyndicIdAndCreatedAtBetween(syndicId, startLastMonth.atStartOfDay(), endLastMonth.atStartOfDay());
        List<ChargeCall> monthBeforeCalls = chargeCallRepository
                .findByBudgetSyndicIdAndCreatedAtBetween(syndicId, startMonthBefore.atStartOfDay(), startLastMonth.atStartOfDay());

        BigDecimal lastMonthTotal = lastMonthCalls.stream().map(ChargeCall::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal monthBeforeTotal = monthBeforeCalls.stream().map(ChargeCall::getTotalAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

        return calculerVariation(lastMonthTotal, monthBeforeTotal).doubleValue();
    }

    // Calcule l'évolution du total encaissé entre le mois dernier et le mois d'avant
    private Double calculateCollectedEvolution(Long syndicId) {

        LocalDate now = LocalDate.now();
        LocalDate startLastMonth = now.withDayOfMonth(1).minusMonths(1);
        LocalDate endLastMonth = now.withDayOfMonth(1);
        LocalDate startMonthBefore = startLastMonth.minusMonths(1);

        BigDecimal lastMonthCollected = chargeCallPaymentRepository
                .sumByBudgetSyndicIdAndPaidAtBetween(syndicId, startLastMonth.atStartOfDay(), endLastMonth.atStartOfDay());
        BigDecimal monthBeforeCollected = chargeCallPaymentRepository
                .sumByBudgetSyndicIdAndPaidAtBetween(syndicId, startMonthBefore.atStartOfDay(), startLastMonth.atStartOfDay());

        return calculerVariation(lastMonthCollected, monthBeforeCollected).doubleValue();
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

    // Calcule l'évolution globale des impayés entre le mois dernier et le mois d'avant
    private Double calculateUnpaidEvolutionGlobal(Long syndicId) {

        LocalDate now = LocalDate.now();
        LocalDate startLastMonth = now.withDayOfMonth(1).minusMonths(1);
        LocalDate endLastMonth = now.withDayOfMonth(1);
        LocalDate startMonthBefore = startLastMonth.minusMonths(1);

        BigDecimal lastMonthUnpaid = getUnpaidTotalForPeriodGlobal(syndicId, startLastMonth, endLastMonth);
        BigDecimal monthBeforeUnpaid = getUnpaidTotalForPeriodGlobal(syndicId, startMonthBefore, startLastMonth);

        return calculerVariation(lastMonthUnpaid, monthBeforeUnpaid).doubleValue();
    }

    // Calcule le montant impayé total sur une période donnée, toutes résidences du syndic
    private BigDecimal getUnpaidTotalForPeriodGlobal(Long syndicId, LocalDate start, LocalDate end) {
        List<ChargeCall> calls = chargeCallRepository
                .findByBudgetSyndicIdAndCreatedAtBetween(syndicId, start.atStartOfDay(), end.atStartOfDay());

        return calls.stream()
                .flatMap(cc -> cc.getItems().stream())
                .map(ChargeCallItem::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    // Construit les 12 points du graphique "Encaissement mensuel" pour l'année en cours
    private List<MonthlyCollectionDTO> buildMonthlyCollection(Long syndicId) {

        String[] monthLabels = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        List<MonthlyCollectionDTO> result = new ArrayList<>();

        int currentYear = LocalDate.now().getYear();

        for (int month = 1; month <= 12; month++) {

            LocalDate debutMois = LocalDate.of(currentYear, month, 1);
            LocalDate finMois = debutMois.plusMonths(1);

            // Montant prévu : somme des ChargeCall générés ce mois-là
            List<ChargeCall> chargeCallsMois = chargeCallRepository
                    .findByBudgetSyndicIdAndCreatedAtBetween(syndicId, debutMois.atStartOfDay(), finMois.atStartOfDay());
            BigDecimal expected = chargeCallsMois.stream()
                    .map(ChargeCall::getTotalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // Montant encaissé : somme des paiements reçus ce mois-là
            BigDecimal collected = chargeCallPaymentRepository
                    .sumByBudgetSyndicIdAndPaidAtBetween(syndicId, debutMois.atStartOfDay(), finMois.atStartOfDay());

            MonthlyCollectionDTO point = new MonthlyCollectionDTO();
            point.setMonthLabel(monthLabels[month - 1]);
            point.setExpected(expected);
            point.setCollected(collected);

            result.add(point);
        }

        return result;
    }

    // Construit le camembert "Répartition des postes" pour une résidence et l'année en cours
    private BudgetPostesRepartitionDTO buildPostesRepartition(Long residenceId) {

        BudgetPostesRepartitionDTO dto = new BudgetPostesRepartitionDTO();

        if (residenceId == null) {
            dto.setPostes(List.of());
            return dto;
        }

        int currentYear = LocalDate.now().getYear();

        Budget budget = budgetRepository.findByResidenceIdAndAnnee(residenceId, currentYear).orElse(null);

        if (budget == null) {
            dto.setPostes(List.of());
            return dto;
        }

        dto.setResidenceName(budget.getResidence().getName());
        dto.setYear(currentYear);

        List<PosteDTO> postes = budget.getItems().stream()
                .map(item -> {
                    PosteDTO posteDto = new PosteDTO();
                    posteDto.setLibelle(item.getLibelle());
                    posteDto.setMontant(item.getMontant());
                    return posteDto;
                })
                .toList();
        dto.setPostes(postes);

        return dto;
    }

    // Résout la résidence par défaut à utiliser pour le camembert, si aucune n'est précisée
    // (prend la première résidence active du syndic)
    private Long resolveDefaultResidenceForDashboard(Long syndicId) {
        List<Budget> activeBudgets = budgetRepository.findBySyndicIdAndStatus(syndicId, BudgetStatus.ACTIVE);
        return activeBudgets.isEmpty() ? null : activeBudgets.get(0).getResidence().getId();
    }

    // ============================================================
    // UTILITAIRES PARTAGÉS
    // ============================================================

    // Calcule le statut d'une ligne de charge selon le nombre de jours de retard
    // (RETARD 1-30j, CRITIQUE 31j+, PARTIEL si paiement partiel dans les délais, PAYE si soldée)
    private String calculateItemStatus(ChargeCallItem item) {

        boolean isFullyPaid = item.getPaidAmount().compareTo(item.getQuotePart()) >= 0;
        if (isFullyPaid) return "PAYE";

        LocalDate dueDate = item.getChargeCall().getDueDate();
        long daysLate = ChronoUnit.DAYS.between(dueDate, LocalDate.now());

        if (daysLate > 30) return "CRITIQUE";
        if (daysLate > 0) return "RETARD";

        boolean hasPartialPayment = item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0;
        if (hasPartialPayment) return "PARTIEL";
        return "A_JOUR";
    }

    // Construit le libellé des biens du copropriétaire pour cette résidence
    private String buildPropertyLabel(ChargeCallItem item) {
        List<Property> properties = propertyRepository.findByOwnerIdAndResidenceId(
                item.getCoOwner().getId(), item.getChargeCall().getBudget().getResidence().getId());

        String propertiesStr = properties.stream()
                .map(Property::getReference)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        return propertiesStr + " – " + item.getChargeCall().getBudget().getResidence().getName();
    }

    // Envoie une relance (email + push) pour une ligne de charge précise, non-bloquant
    private void sendReminderForItem(ChargeCallItem item) {

        try {
            String subject = "Relance — Appel de charges " + buildPeriodLabel(item.getChargeCall());
            String body = buildReminderEmailBody(item.getChargeCall(), item);
            emailService.sendEmail(item.getCoOwner().getEmail(), subject, body);
        } catch (Exception e) {
            System.err.println("Erreur envoi email de relance à " + item.getCoOwner().getEmail() + ": " + e.getMessage());
        }

        try {
            if (item.getCoOwner().isNotificationsEnabled()) {
                String title = "Rappel de paiement";
                String body = "Solde restant: " + item.getRemainingAmount() + " FCFA";
                notificationService.sendPush(item.getCoOwner().getId(), title, body);
            }
        } catch (Exception e) {
            System.err.println("Erreur envoi notification de relance à " + item.getCoOwner().getId() + ": " + e.getMessage());
        }
    }

    // ============================================================
    // UTILITAIRE PARTAGÉ — récupère un appel exceptionnel et vérifie son appartenance au syndic
    // ============================================================

    private ExceptionalCall getExceptionalCallForSyndic(Long exceptionalCallId) {

        // Récupère l'appel exceptionnel, erreur si introuvable
        ExceptionalCall exceptionalCall = exceptionalCallRepository.findById(exceptionalCallId)
                .orElseThrow(() -> new RuntimeException("Appel exceptionnel introuvable"));

        // Vérifie que cet appel appartient bien au syndic connecté
        User currentSyndic = getCurrentUser();
        if (!exceptionalCall.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Cet appel exceptionnel ne vous appartient pas");
        }

        return exceptionalCall;
    }


    // ============================================================
    // Biens Communs
    // ============================================================

    @Override
    @Transactional(readOnly = true)
    public Page<CommonFacilitySuggestionDTO> searchCommonFacilities(Long residenceId, String q, Integer page, Integer size) {
        // Récupérer la résidence
        Residence residence = residenceRepository.findById(residenceId)
                .orElseThrow(() -> new RuntimeException("Résidence introuvable"));

        // Vérifier que le syndic est bien le propriétaire de la résidence
        User currentSyndic = getCurrentUser();
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette résidence");
        }

        // Récupérer les équipements communs avec pagination
        Pageable pageable = PageRequest.of(page, size);
        Page<CommonFacility> facilityPage;
        if (q == null || q.trim().isEmpty()) {
            facilityPage = commonFacilityRepository.findByResidenceId(residenceId, pageable);
        } else {
            // Pour la recherche, on utilise pagination manuelle car la méthode de recherche n'est pas paginée
            List<CommonFacility> facilities = commonFacilityRepository.findByResidenceIdAndFacilityTypeNameContainingIgnoreCase(residenceId, q.trim());
            int totalElements = facilities.size();
            int fromIndex = Math.min(page * size, totalElements);
            int toIndex = Math.min(fromIndex + size, totalElements);
            List<CommonFacility> pageContent = facilities.subList(fromIndex, toIndex);
            facilityPage = new PageImpl<>(pageContent, pageable, totalElements);
        }

        // Mapper vers DTO
        List<CommonFacilitySuggestionDTO> dtos = facilityPage.getContent().stream()
                .map(facility -> CommonFacilitySuggestionDTO.builder()
                        .id(facility.getId())
                        .name(facility.getFacilityType().getName())
                        .icon(facility.getFacilityType().getIcon())
                        .build())
                .toList();

        return new PageImpl<>(dtos, pageable, facilityPage.getTotalElements());
    }

    // ============================================================
    // Méthodes Utilitaires
    // ============================================================

    // Construit la carte d'un seul appel de charges
    private ChargeCallCardDTO buildChargeCallCard(ChargeCall chargeCall) {

        ChargeCallCardDTO dto = new ChargeCallCardDTO();
        dto.setId(chargeCall.getId());
        dto.setReference(chargeCall.getReference());
        dto.setYear(chargeCall.getYear());
        dto.setPeriodNumber(chargeCall.getPeriodNumber());
        dto.setPeriodLabel(buildPeriodLabel(chargeCall));
        dto.setResidenceName(chargeCall.getBudget().getResidence().getName());
        dto.setTotalAmount(chargeCall.getTotalAmount());
        dto.setCoOwnersCount(chargeCall.getItems().size());
        dto.setSentDate(chargeCall.getSentDate());
        dto.setDueDate(chargeCall.getDueDate());

        // Calcule le statut à la volée (jamais stocké en base)
        dto.setStatus(calculateChargeCallStatus(chargeCall).name());

        return dto;
    }

    // Construit une ligne du tableau "Suivi par copropriétaire"
    private ChargeCallItemDetailDTO buildChargeCallItemDetail(ChargeCallItem item, List<Property> properties) {

        ChargeCallItemDetailDTO dto = new ChargeCallItemDetailDTO();
        dto.setCoOwnerName(item.getCoOwner().getFirstName() + " " + item.getCoOwner().getLastName());

        // Récupère tous les appartements de ce copropriétaire dans la résidence, joints par virgules
        String propertiesLabel = properties.stream()
                .filter(p -> p.getOwner() != null && p.getOwner().getId().equals(item.getCoOwner().getId()))
                .map(Property::getReference)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        dto.setProperties(propertiesLabel);

        dto.setTantieme(item.getTantieme());
        dto.setQuotePart(item.getQuotePart());
        dto.setPaidAmount(item.getPaidAmount());
        dto.setRemainingAmount(item.getRemainingAmount());

        // Calcule le statut individuel de ce copropriétaire
        if (item.getPaidAmount().compareTo(item.getQuotePart()) >= 0) {
            dto.setStatus("PAYE");
        } else if (item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            dto.setStatus("PARTIEL");
        } else {
            dto.setStatus("IMPAYE");
        }

        return dto;
    }
    // Construit le corps de l'email de relance
    private String buildReminderEmailBody(ChargeCall chargeCall, ChargeCallItem item) {
        StringBuilder body = new StringBuilder();
        body.append("Rappel de paiement\n\n");
        body.append("Résidence : ").append(chargeCall.getBudget().getResidence().getName()).append("\n");
        body.append("Période : ").append(buildPeriodLabel(chargeCall)).append("\n");
        body.append("Date d'échéance : ").append(chargeCall.getDueDate()).append("\n");
        body.append("Montant restant à payer : ").append(item.getRemainingAmount()).append(" FCFA\n");
        return body.toString();
    }


    // Calcule le statut de l'appel à la volée : SETTLED, PARTIAL ou SENT (jamais stocké en base)
    private ChargeCallStatus calculateChargeCallStatus(ChargeCall chargeCall) {

        boolean allSettled = chargeCall.getItems().stream()
                .allMatch(item -> item.getPaidAmount().compareTo(item.getQuotePart()) >= 0);

        if (allSettled) {
            return ChargeCallStatus.SETTLED;
        }

        boolean hasAtLeastOnePayment = chargeCall.getItems().stream()
                .anyMatch(item -> item.getPaidAmount().compareTo(BigDecimal.ZERO) > 0);

        if (hasAtLeastOnePayment) {
            return ChargeCallStatus.PARTIAL;
        }

        return ChargeCallStatus.SENT;
    }

    // Construit une ligne d'historique à partir d'un ActivityLog
    private HistoryItemDTO buildBudgetHistoryItem(ActivityLog log) {

        return HistoryItemDTO.builder()
                .actorName(log.getActor() != null
                        ? log.getActor().getFirstName() + " " + log.getActor().getLastName() : "Système")
                .message(log.getMessage())
                .date(log.getCreatedAt())
                .build();
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


    // Construit une ligne du tableau des postes : montant réel calculé via les interventions
    // si le poste est lié à un bien commun, sinon via les demandes de retrait validées liées au poste
    private BudgetItemOverviewDTO buildItemOverview(BudgetItem item, BigDecimal budgetTotal, Integer annee) {
        BudgetItemOverviewDTO itemDto = new BudgetItemOverviewDTO();
        itemDto.setLibelle(item.getLibelle());
        itemDto.setMontantPrevu(item.getMontant());

        BigDecimal montantReel;

        if (item.getCommonFacility() != null) {
            // Poste lié à un bien commun : dépense réelle calculée via les interventions de cet équipement
            montantReel = calculerDepensesReellesParEquipement(item.getCommonFacility(), annee);
        } else {
            // Poste sans bien commun : dépense réelle calculée via les demandes de retrait validées liées à ce poste
            montantReel = syndicWithdrawalRequestRepository.sumCompletedByBudgetItem(item.getId());
        }

        itemDto.setMontantReel(montantReel);
        itemDto.setEcart(item.getMontant().subtract(montantReel));

        // Pourcentage du poste par rapport au budget total
        itemDto.setPercentage(calculatePercentage(item.getMontant(), budgetTotal));

        return itemDto;
    }

    // Calcule les dépenses réelles d'un équipement commun, pour une année donnée
    private BigDecimal calculerDepensesReellesParEquipement(CommonFacility facility, Integer annee) {

        LocalDateTime debutAnnee = LocalDateTime.of(annee, 1, 1, 0, 0);
        LocalDateTime finAnnee = LocalDateTime.of(annee + 1, 1, 1, 0, 0);

        return syndicWalletTransactionRepository
                .sumByCommonFacilityAndPeriod(facility.getId(), debutAnnee, finAnnee);
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

        // Récupère toutes les transactions TRAVAUX de cette résidence pour l'année du budget (on cherche les dépenses réelles)
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

        // Calcule le pourcentage de consommation du budget (dépenses réelles / budget total) * 100
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
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0) {
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
            Double fileSizeMb = doc.getFileSizeKb() != null
                    ? doc.getFileSizeKb() / 1024.0
                    : 0.0;
            ExceptionalCallDocumentDTO docDTO = ExceptionalCallDocumentDTO.builder()
                    .id(doc.getId())
                    .fileName(doc.getFileName())
                    .fileUrl(doc.getFileName())
                    .fileSizeMb(fileSizeMb)
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
    // ============================================================
    // MÉTHODE HELPER — RÉCUPÉRER L'UTILISATEUR CONNECTÉ
    // ============================================================

    private User getCurrentUser() {
        String email = org.springframework.security.core.context.SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));
    }

    // ============================================================
    //  HELPER — Construction du détail complet du budget
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
    // Construit un DTO d'appel de charges lié à un budget
    private BudgetLinkedChargeCallDTO buildBudgetLinkedChargeCallDto(ChargeCall chargeCall) {

        BudgetLinkedChargeCallDTO dto = new BudgetLinkedChargeCallDTO();
        dto.setId(chargeCall.getId());
        dto.setPeriodLabel(buildPeriodLabel(chargeCall));
        dto.setTotalAmount(chargeCall.getTotalAmount());
        dto.setStatus(chargeCall.getStatus().name());
        dto.setSentDate(chargeCall.getSentDate());
        dto.setDueDate(chargeCall.getDueDate());

        return dto;
    }

    // Construit le libellé de période (ex: "T1 2026 (Jan-Mar)")
    private String buildPeriodLabel(ChargeCall chargeCall) {
        String periodLabel;
        if (chargeCall.getFrequency() == ChargeFrequency.TRIMESTRIEL) {
            String[] trimestres = {"T1 (Jan-Mar)", "T2 (Avr-Jui)", "T3 (Juil-Sep)", "T4 (Oct-Déc)"};
            periodLabel = trimestres[chargeCall.getPeriodNumber() - 1] + " " + chargeCall.getYear();
        } else {
            // Mensuel
            String[] mois = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jui", "Juil", "Aoû", "Sep", "Oct", "Nov", "Déc"};
            periodLabel = mois[chargeCall.getPeriodNumber() - 1] + " " + chargeCall.getYear();
        }
        return periodLabel;
    }





}
