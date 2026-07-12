package com.example.solimus.services.owner.charge;

import com.example.solimus.dtos.owner.charge.ChargePaymentReceiptDTO;
import com.example.solimus.dtos.owner.charge.ChargePaymentResponseDTO;
import com.example.solimus.dtos.owner.charge.InitierPaiementChargeDTO;
import com.example.solimus.dtos.owner.charge.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ChargeCallItemRepository;
import com.example.solimus.repositories.ChargeCallPaymentRepository;
import com.example.solimus.repositories.ExceptionalCallItemRepository;
import com.example.solimus.repositories.ExceptionalCallPaymentRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class OwnerChargeServiceImpl implements OwnerChargeService {

    private final UserRepository userRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final ExceptionalCallItemRepository exceptionalCallItemRepository;
    private final PropertyRepository propertyRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;

    // Template d'URL pour le pont de paiement TouchPay
    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    // =========================================================================
    // LISTER MES CHARGES (courantes + exceptionnelles mélangées, paginées, filtrées)
    // =========================================================================

    // Note : pagination faite manuellement (pas Pageable de Spring Data) car on mélange
    // 2 sources différentes (ChargeCallItem + ExceptionalCallItem) en une seule liste unifiée.
    // Le volume par copropriétaire reste toujours faible, donc pas d'impact de performance.
    @Override
    public MyChargeListResponse getMyCharges(String search, ChargeType type, String status, Long residenceId, int page, int size) {

        // Récupère le copropriétaire connecté
        User currentOwner = getCurrentUser();

        // Récupère toutes ses charges courantes et exceptionnelles, non filtrées pour l'instant
        List<ChargeCallItem> chargeItems = chargeCallItemRepository.findByCoOwnerId(currentOwner.getId());
        List<ExceptionalCallItem> exceptionalItems = exceptionalCallItemRepository.findByCoOwnerId(currentOwner.getId());

        // Construit une liste unifiée de cartes, en mélangeant les deux types
        List<MyChargeCardDTO> allCards = new ArrayList<>();

        for (ChargeCallItem item : chargeItems) {
            allCards.add(buildChargeCallCard(item));
        }
        for (ExceptionalCallItem item : exceptionalItems) {
            allCards.add(buildExceptionalCallCard(item));
        }

        // Applique tous les filtres, y compris résidence.
        // Chaque condition "== null || isBlank()" veut dire "pas de filtre demandé, on garde tout" ;
        // sinon on vérifie la vraie correspondance (contains pour le texte libre, égalité exacte pour type/statut/résidence).
        List<MyChargeCardDTO> filtered = allCards.stream()
                .filter(c -> search == null || search.isBlank() || c.getTitle().toLowerCase().contains(search.toLowerCase()))
                .filter(c -> type == null || c.getType().equalsIgnoreCase(type.name()))
                .filter(c -> status == null || status.isBlank() || c.getStatus().equalsIgnoreCase(status))
                .filter(c -> residenceId == null || c.getResidenceId().equals(residenceId))
                .sorted(Comparator.comparing(MyChargeCardDTO::getDueDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        // Construit le résumé (bandeau haut) à partir de la liste FILTRÉE :
        // les KPI (total à payer, nombre en attente, prochaine échéance) suivent donc le filtre résidence appliqué
        MyChargesSummaryDTO summary = buildSummary(filtered);

        // Pagination manuelle : découpe la liste filtrée selon la page demandée
        int start = Math.min(page * size, filtered.size());
        int end = Math.min(start + size, filtered.size());
        List<MyChargeCardDTO> pageContent = filtered.subList(start, end);

        int totalPages = (int) Math.ceil((double) filtered.size() / size);

        return MyChargeListResponse.builder()
                .summary(summary)
                .charges(pageContent)
                .currentPage(page)
                .totalPages(totalPages)
                .totalElements(filtered.size())
                .build();
    }

    // =========================================================================
    // DÉTAIL D'UNE CHARGE
    // =========================================================================

    @Override
    public MyChargeDetailDTO getChargeDetail(ChargeType type, Long id) {

        // Récupère le copropriétaire connecté
        User currentOwner = getCurrentUser();

        if (type == ChargeType.REGULAR) {
            ChargeCallItem item = chargeCallItemRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));

            // Vérifie que cette charge appartient bien au copropriétaire connecté
            if (!item.getCoOwner().getId().equals(currentOwner.getId())) {
                throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette charge");
            }

            return buildChargeCallDetail(item);

        } else if (type == ChargeType.EXCEPTIONAL) {
            ExceptionalCallItem item = exceptionalCallItemRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));

            // Vérifie que cette charge appartient bien au copropriétaire connecté
            if (!item.getCoOwner().getId().equals(currentOwner.getId())) {
                throw new ForbiddenException("Vous n'êtes pas autorisé à accéder à cette charge");
            }

            return buildExceptionalCallDetail(item);
        }

        throw new ResourceNotFoundException("Type de charge invalide");
    }

    // ================================================
    // Paiement charge copropriétaire
    // ================================================
    @Override
    @Transactional
    public ChargePaymentResponseDTO initierPaiement(ChargeType type, Long id, InitierPaiementChargeDTO dto) {

        User currentOwner = getCurrentUser();

        if (type == ChargeType.REGULAR) {
            return initierPaiementChargeCall(id, dto, currentOwner);
        } else if (type == ChargeType.EXCEPTIONAL) {
            return initierPaiementExceptionalCall(id, dto, currentOwner);
        }

        throw new ResourceNotFoundException("Type de charge invalide");
    }

    @Override
    @Transactional(readOnly = true)
    public ChargePaymentReceiptDTO getReceipt(String transactionRef) {

        User currentOwner = getCurrentUser();

        if (transactionRef.startsWith("CPY-")) {
            return getReceiptFromChargeCall(transactionRef, currentOwner);
        } else if (transactionRef.startsWith("ECP-")) {
            return getReceiptFromExceptionalCall(transactionRef, currentOwner);
        }

        throw new ResourceNotFoundException("Référence de paiement invalide");
    }



    // =========================================================================
    // UTILITAIRES ET MAPPERS
    // =========================================================================

    private ChargePaymentResponseDTO initierPaiementChargeCall(Long id, InitierPaiementChargeDTO dto, User currentOwner) {

        ChargeCallItem item = chargeCallItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));

        if (!item.getCoOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'avez pas accès à cette charge");
        }

        BigDecimal remainingAmount = item.getQuotePart().subtract(item.getPaidAmount());
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Cette charge est déjà payée");
        }

        chargeCallPaymentRepository.findFirstByChargeCallItemIdOrderByPaidAtDesc(id)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .ifPresent(p -> {
                    throw new BadRequestException("Un paiement est déjà en cours pour cette charge");
                });

        String transactionRef = genererReference("CPY");

        ChargeCallPayment newPayment = new ChargeCallPayment();
        newPayment.setReference(transactionRef);
        newPayment.setChargeCallItem(item);
        newPayment.setOwner(currentOwner);
        newPayment.setAmount(remainingAmount);
        newPayment.setMethod(dto.getMethod());
        newPayment.setStatus(PaymentStatus.PENDING);
        chargeCallPaymentRepository.save(newPayment);

        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, newPayment.getReference());

        return ChargePaymentResponseDTO.builder()
                .success(true)
                .message("Paiement initié. Veuillez compléter via TouchPay.")
                .transactionReference(newPayment.getReference())
                .amount(remainingAmount)
                .paymentUrl(bridgeUrl)
                .build();
    }

    // Sous-méthode privée pour les appels exceptionnels
    private ChargePaymentResponseDTO initierPaiementExceptionalCall(Long id, InitierPaiementChargeDTO dto, User currentOwner) {

        ExceptionalCallItem item = exceptionalCallItemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Charge introuvable"));

        if (!item.getCoOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Vous n'avez pas accès à cette charge");
        }

        BigDecimal remainingAmount = item.getQuotePart().subtract(item.getPaidAmount());
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BadRequestException("Cette charge est déjà payée");
        }

        exceptionalCallPaymentRepository.findFirstByExceptionalCallItemIdOrderByPaidAtDesc(id)
                .filter(p -> p.getStatus() == PaymentStatus.PENDING)
                .ifPresent(p -> {
                    throw new BadRequestException("Un paiement est déjà en cours pour cette charge");
                });

        // Préfixe différent pour distinguer dans le callback/routing (ECP au lieu de CPY)
        String transactionRef = genererReference("ECP");

        ExceptionalCallPayment newPayment = new ExceptionalCallPayment();
        newPayment.setReference(transactionRef);
        newPayment.setExceptionalCallItem(item);
        newPayment.setOwner(currentOwner);
        newPayment.setAmount(remainingAmount);
        newPayment.setMethod(dto.getMethod());
        newPayment.setStatus(PaymentStatus.PENDING);
        exceptionalCallPaymentRepository.save(newPayment);

        String bridgeUrl = String.format(touchPayBridgeUrlTemplate, newPayment.getReference());

        return ChargePaymentResponseDTO.builder()
                .success(true)
                .message("Paiement initié. Veuillez compléter via TouchPay.")
                .transactionReference(newPayment.getReference())
                .amount(remainingAmount)
                .paymentUrl(bridgeUrl)
                .build();
    }

    // Construit le reçu à partir d'un paiement de charge courante
    private ChargePaymentReceiptDTO getReceiptFromChargeCall(String transactionRef, User currentOwner) {

        ChargeCallPayment paiement = chargeCallPaymentRepository.findByReference(transactionRef)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));

        if (!paiement.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé");
        }

        // Construit le titre à partir de la période de l'appel de charges, comme sur les cartes
        String chargeTitle = "Charges " + buildPeriodLabel(paiement.getChargeCallItem().getChargeCall());

        return ChargePaymentReceiptDTO.builder()
                .reference(paiement.getReference())
                .chargeTitle(chargeTitle)
                .amount(paiement.getAmount())
                .method(paiement.getMethod())
                .paidAt(paiement.getPaidAt())
                .status(paiement.getStatus())
                .build();
    }

    // Construit le reçu à partir d'un paiement d'appel exceptionnel
    private ChargePaymentReceiptDTO getReceiptFromExceptionalCall(String transactionRef, User currentOwner) {

        ExceptionalCallPayment paiement = exceptionalCallPaymentRepository.findByReference(transactionRef)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));

        if (!paiement.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé");
        }

        // Le titre vient directement du titre de l'appel exceptionnel
        String chargeTitle = paiement.getExceptionalCallItem().getExceptionalCall().getTitle();

        return ChargePaymentReceiptDTO.builder()
                .reference(paiement.getReference())
                .chargeTitle(chargeTitle)
                .amount(paiement.getAmount())
                .method(paiement.getMethod())
                .paidAt(paiement.getPaidAt())
                .status(paiement.getStatus())
                .build();
    }

    // Récupère l'utilisateur actuellement authentifié via le SecurityContext
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    // Construit le résumé (bandeau haut) à partir de la liste de charges déjà filtrée
    private MyChargesSummaryDTO buildSummary(List<MyChargeCardDTO> cards) {

        // Filtre uniquement les charges qui ont encore un montant à payer (non soldées)
        List<MyChargeCardDTO> pending = cards.stream()
                .filter(c -> c.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .toList();

        // Additionne les montants de toutes ces charges en attente
        BigDecimal totalToPay = pending.stream()
                .map(MyChargeCardDTO::getRemainingAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Cherche la date d'échéance la plus proche parmi les charges en attente,
        // en ignorant celles qui n'ont pas de date (cas des appels exceptionnels)
        LocalDate nextDueDate = pending.stream()
                .map(MyChargeCardDTO::getDueDate)
                .filter(Objects::nonNull)
                .min(LocalDate::compareTo)
                .orElse(null);

        // Construit le résumé avec les 3 valeurs calculées
        return MyChargesSummaryDTO.builder()
                .totalToPay(totalToPay)
                .pendingCount(pending.size())
                .nextDueDate(nextDueDate)
                .build();
    }

    // Construit une carte pour un ChargeCallItem
    private MyChargeCardDTO buildChargeCallCard(ChargeCallItem item) {

        Residence residence = item.getChargeCall().getBudget().getResidence();
        String propertyRef = findPropertyReference(item.getCoOwner().getId(), residence.getId());

        return MyChargeCardDTO.builder()
                .id(item.getId())
                .type(ChargeType.REGULAR.name())
                .typeLabel(ChargeType.REGULAR.getDescription())
                // ChargeCall n'a pas de titre propre (contrairement à ExceptionalCall) : on le construit
                // dynamiquement à partir de sa période, pour distinguer chaque appel dans la liste
                .title("Charges " + buildPeriodLabel(item.getChargeCall()))
                .residenceName(residence.getName())
                .residenceId(residence.getId())
                .propertyReference(propertyRef)
                .remainingAmount(item.getRemainingAmount())
                .dueDate(item.getChargeCall().getDueDate())
                .status(resolveItemStatus(item.getPaidAmount(), item.getQuotePart()))
                .build();
    }

    // Construit une carte pour un ExceptionalCallItem
    private MyChargeCardDTO buildExceptionalCallCard(ExceptionalCallItem item) {

        Residence residence = item.getExceptionalCall().getResidence();
        String propertyRef = findPropertyReference(item.getCoOwner().getId(), residence.getId());

        // Calcule le solde restant, car ExceptionalCallItem n'a pas de champ remainingAmount stocké
        BigDecimal remainingAmount = item.getQuotePart().subtract(item.getPaidAmount());


        return MyChargeCardDTO.builder()
                .id(item.getId())
                .type(ChargeType.EXCEPTIONAL.name())
                .typeLabel(ChargeType.EXCEPTIONAL.getDescription())
                // ExceptionalCall a déjà un titre propre, saisi par le syndic à la création
                .title(item.getExceptionalCall().getTitle())
                .residenceName(residence.getName())
                .residenceId(residence.getId())
                .propertyReference(propertyRef)
                .remainingAmount(remainingAmount)
                .dueDate(null) // ExceptionalCall n'a pas de champ dueDate dans le modèle actuel
                .status(resolveItemStatus(item.getPaidAmount(), item.getQuotePart()))
                .build();
    }

    // Détermine le statut d'une ligne à partir de paidAmount(montant payé)/quotePart (ce qu'on doit payé)
    private String resolveItemStatus(BigDecimal paidAmount, BigDecimal quotePart) {
        if (paidAmount.compareTo(quotePart) >= 0) return "Payé";
        if (paidAmount.compareTo(BigDecimal.ZERO) > 0) return "Partiel";
        return "En attente";
    }

    // Récupère TOUS les biens du copropriétaire dans cette résidence, séparés par des virgules
    private String findPropertyReference(Long coOwnerId, Long residenceId) {
        List<Property> properties = propertyRepository.findByOwnerIdAndResidenceId(coOwnerId, residenceId);

        return properties.stream()
                .map(Property::getReference)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    // Construit le détail complet d'un ChargeCallItem
    private MyChargeDetailDTO buildChargeCallDetail(ChargeCallItem item) {

        ChargeCall chargeCall = item.getChargeCall();
        Budget budget = chargeCall.getBudget();
        Residence residence = budget.getResidence();
        String propertyRef = findPropertyReference(item.getCoOwner().getId(), residence.getId());

       // la répartition par poste : montant du poste (résidence) × tantième / 100 / nombre de périodes
        List<ChargeBreakdownLineDTO> breakdown = buildBreakdown(budget, item.getTantieme(), chargeCall.getFrequency());

        return MyChargeDetailDTO.builder()
                .id(item.getId())
                .reference(item.getReference())
                .type(ChargeType.REGULAR.name())
                .typeLabel(ChargeType.REGULAR.getDescription())
                .remainingAmount(item.getRemainingAmount())
                .dueDate(chargeCall.getDueDate())
                .residenceName(residence.getName())
                .propertyReference(propertyRef)
                .period(buildPeriodLabel(chargeCall))
                .issuedDate(chargeCall.getSentDate())
                .status(resolveItemStatus(item.getPaidAmount(), item.getQuotePart()))
                .breakdown(breakdown)
                .breakdownTotal(breakdown.stream().map(ChargeBreakdownLineDTO::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add))
                .build();
    }

    // Construit le détail complet d'un ExceptionalCallItem
    private MyChargeDetailDTO buildExceptionalCallDetail(ExceptionalCallItem item) {

        ExceptionalCall exceptionalCall = item.getExceptionalCall();
        Residence residence = exceptionalCall.getResidence();
        String propertyRef = findPropertyReference(item.getCoOwner().getId(), residence.getId());

        // Calcule le solde restant, car ExceptionalCallItem n'a pas de champ remainingAmount stocké
        BigDecimal remainingAmount = item.getQuotePart().subtract(item.getPaidAmount());


        return MyChargeDetailDTO.builder()
                .id(item.getId())
                .reference(item.getReference())
                .type(ChargeType.EXCEPTIONAL.name())
                .typeLabel(ChargeType.EXCEPTIONAL.getDescription())
                .remainingAmount(remainingAmount)
                .dueDate(null) // ExceptionalCall n'a pas de champ dueDate dans le modèle actuel
                .residenceName(residence.getName())
                .propertyReference(propertyRef)
                .period(String.valueOf(exceptionalCall.getCreatedAt().getYear()))
                .issuedDate(exceptionalCall.getCreatedAt().toLocalDate())
                .status(resolveItemStatus(item.getPaidAmount(), item.getQuotePart()))
                // Pas de répartition par poste pour les appels exceptionnels (pas de BudgetItem lié)
                .breakdown(List.of())
                .breakdownTotal(item.getQuotePart())
                .build();
    }

    // Construit la répartition par poste : montant du poste (résidence) × tantième / 100 / nombre de périodes
    private List<ChargeBreakdownLineDTO> buildBreakdown(Budget budget, BigDecimal tantieme, ChargeFrequency frequency) {

        int diviseur = (frequency == ChargeFrequency.MENSUEL) ? 12 : 4;

        List<ChargeBreakdownLineDTO> lines = new ArrayList<>();

        for (BudgetItem budgetItem : budget.getItems()) {
            BigDecimal partPoste = budgetItem.getMontant()
                    .multiply(tantieme)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal partPeriode = partPoste.divide(BigDecimal.valueOf(diviseur), 0, RoundingMode.HALF_UP);

            lines.add(ChargeBreakdownLineDTO.builder()
                    .label(budgetItem.getLibelle())
                    .amount(partPeriode)
                    .build());
        }

        return lines;
    }

    // Construit le libellé de période lisible (mensuel ou trimestriel)
    private String buildPeriodLabel(ChargeCall chargeCall) {
        if (chargeCall.getFrequency() == ChargeFrequency.MENSUEL) {
            String[] monthNames = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                    "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
            int index = chargeCall.getPeriodNumber() - 1;
            String monthLabel = (index >= 0 && index < monthNames.length) ? monthNames[index] : "";
            return monthLabel + " " + chargeCall.getYear();
        }

        String[] quarterLabels = {"Jan-Mar", "Avr-Jun", "Jul-Sep", "Oct-Dec"};
        int index = chargeCall.getPeriodNumber() - 1;
        String quarterLabel = (index >= 0 && index < quarterLabels.length) ? quarterLabels[index] : "";
        return "T" + chargeCall.getPeriodNumber() + " " + chargeCall.getYear() + " (" + quarterLabel + ")";
    }

    // Génère une référence unique avec un préfixe (ex: CPY-123456 ou ECP-123456)
    private String genererReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }


}