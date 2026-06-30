package com.example.solimus.services.syndic.charge;

import com.example.solimus.dtos.syndic.charge.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        // ÉTAPE 2.4 — Créer l'entité Budget
        // ------------------------------------------------------------
        Budget budget = new Budget();
        budget.setResidence(residence);
        budget.setAnnee(dto.getAnnee());
        budget.setRepartitionMode(dto.getRepartitionMode());
        budget.setSyndic(currentSyndic);
        budget.setBudgetTotal(BigDecimal.ZERO);

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
            budgetItems.add(item);
            budgetTotal = budgetTotal.add(itemDto.getMontant());
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
}
