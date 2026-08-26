package com.example.solimus.services.owner.charge;

import com.example.solimus.dtos.owner.charge.ChargePaymentReceiptDTO;
import com.example.solimus.dtos.owner.charge.ChargePaymentResponseDTO;
import com.example.solimus.dtos.owner.charge.InitierPaiementChargeDTO;
import com.example.solimus.dtos.owner.charge.*;
import com.example.solimus.entities.*;
import com.example.solimus.enums.BudgetStatus;
import com.example.solimus.enums.ChargeFrequency;
import com.example.solimus.enums.ChargeItemPaymentStatus;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.enums.ExceptionalCallStatus;
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
import com.example.solimus.utils.ChargeAllocationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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

    // Pagination SQL réelle (UNION ALL natif entre ChargeCallItem et ExceptionalCallItem — voir
    // ChargeCallItemRepository.findMyChargesUnion) : la base ne renvoie que les N lignes de la page
    // demandée, plus de chargement complet en mémoire. Le libellé de période (title) des charges
    // courantes reste calculé en Java (buildPeriodLabel) — appliqué uniquement sur les lignes déjà
    // récupérées, jamais recalculé pour toute la liste.
    @Override
    @Transactional(readOnly = true)
    public MyChargeListResponse getMyCharges(String search, ChargeType type, String status, Long residenceId, int page, int size) {

        // Récupère le copropriétaire connecté
        User currentOwner = getCurrentUser();

        String typeCode = type != null ? type.name() : null;
        String statusRaw = resolveStatusFilter(status);

        Pageable pageable = PageRequest.of(page, size);
        Page<Object[]> rowsPage = chargeCallItemRepository.findMyChargesUnion(
                currentOwner.getId(), residenceId, typeCode, statusRaw, search, pageable);

        List<MyChargeCardDTO> pageContent = rowsPage.getContent().stream()
                .map(this::buildCardFromRow)
                .toList();

        // KPI du bandeau haut, calculés sur TOUT l'ensemble filtré (mêmes filtres, sans pagination),
        // pas seulement la page courante
        List<Object[]> summaryRows = chargeCallItemRepository.sumMyChargesSummary(
                currentOwner.getId(), residenceId, typeCode, statusRaw, search);
        MyChargesSummaryDTO summary = buildSummaryFromRow(summaryRows.get(0));

        return MyChargeListResponse.builder()
                .summary(summary)
                .charges(pageContent)
                .currentPage(page)
                .totalPages(rowsPage.getTotalPages())
                .totalElements((int) rowsPage.getTotalElements())
                .build();
    }

    // =========================================================================
    // Liste des résidences du copropriétaire connecté (id + nom)
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public List<OwnerResidenceDTO> getMyResidences() {

        User currentOwner = getCurrentUser();

        // Récupère tous les lots du copropriétaire
        List<Property> properties = propertyRepository.findAllByOwnerId(currentOwner.getId());

        // Récupère les résidences uniques (dédupliquées)
        List<Residence> residences = properties.stream()
                .map(Property::getResidence)
                .filter(residence -> residence != null)
                .distinct()
                .toList();

        // Convertit en DTO (id + nom uniquement)
        return residences.stream()
                .map(r -> OwnerResidenceDTO.builder()
                        .id(r.getId())
                        .name(r.getName())
                        .build())
                .toList();
    }

    // =========================================================================
    // DÉTAIL D'UNE CHARGE
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
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
    // Vérifie le statut réel d'un paiement, à la demande de l'app mobile
    // =========================================================================
    @Override
    @Transactional(readOnly = true)
    public ChargePaymentStatusDTO getPaymentStatus(String reference) {

        if (reference.startsWith("CPY-")) {
            return getPaymentStatusFromChargeCall(reference);
        } else if (reference.startsWith("ECP-")) {
            return getPaymentStatusFromExceptionalCall(reference);
        }

        throw new ResourceNotFoundException("Référence de paiement invalide");
    }

    // Sous-méthode : statut d'un paiement de charge courante
    private ChargePaymentStatusDTO getPaymentStatusFromChargeCall(String reference) {

        User currentUser = getCurrentUser();

        ChargeCallPayment payment = chargeCallPaymentRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));

        // Vérifie que ce paiement appartient bien au copropriétaire connecté
        if (!payment.getOwner().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Ce paiement ne vous appartient pas");
        }

        return ChargePaymentStatusDTO.builder()
                .reference(payment.getReference())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .build();
    }

    // Sous-méthode : statut d'un paiement de charge exceptionnelle
    private ChargePaymentStatusDTO getPaymentStatusFromExceptionalCall(String reference) {

        User currentUser = getCurrentUser();

        ExceptionalCallPayment payment = exceptionalCallPaymentRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable"));

        // Vérifie que ce paiement appartient bien au copropriétaire connecté
        if (!payment.getOwner().getId().equals(currentUser.getId())) {
            throw new ForbiddenException("Ce paiement ne vous appartient pas");
        }

        return ChargePaymentStatusDTO.builder()
                .reference(payment.getReference())
                .status(payment.getStatus())
                .amount(payment.getAmount())
                .paidAt(payment.getPaidAt())
                .build();
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

        // Le budget de cette charge a été clôturé par le syndic : plus aucun paiement accepté
        if (item.getChargeCall().getBudget().getStatus() == BudgetStatus.CLOSED) {
            throw new BadRequestException("Le budget de cette résidence a été clôturé, cette charge n'accepte plus de paiement");
        }

        BigDecimal remainingAmount = item.getRemainingAmount();
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

        // Un appel clôturé par le syndic n'accepte plus aucun nouveau paiement
        if (item.getExceptionalCall().getStatus() == ExceptionalCallStatus.CLOSED) {
            throw new BadRequestException("Cet appel exceptionnel est clôturé, il n'accepte plus de paiement");
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

    // Traduit le "status" reçu en query param (libellé français, ex: "En attente") vers le nom brut
    // de l'enum ChargeItemPaymentStatus (ex: "PENDING"), seule forme filtrable en SQL — accepte aussi
    // directement le nom brut par tolérance. Aucun statut connu ne correspond → traité comme "pas de
    // filtre" (permissif), plutôt que de risquer de masquer silencieusement toutes les lignes.
    private String resolveStatusFilter(String status) {
        if (status == null || status.isBlank()) return null;
        for (ChargeItemPaymentStatus s : ChargeItemPaymentStatus.values()) {
            if (s.getLabel().equalsIgnoreCase(status) || s.name().equalsIgnoreCase(status)) {
                return s.name();
            }
        }
        return null;
    }

    // Construit une carte "Mes charges" à partir d'une ligne brute de findMyChargesUnion — voir l'ordre
    // des colonnes dans ChargeCallItemRepository (SELECT * FROM (...) AS combined) :
    // [0]=source_type [1]=id [2]=title [3]=type_code [4]=residence_name [5]=residence_id
    // [6]=property_reference [7]=remaining_amount [8]=due_date [9]=status_raw [10]=payment_blocked
    // [11]=frequency [12]=period_number [13]=year [14]=search_title (ignoré ici)
    private MyChargeCardDTO buildCardFromRow(Object[] row) {

        String sourceType = (String) row[0];
        ChargeType chargeType = ChargeType.valueOf((String) row[3]);
        ChargeItemPaymentStatus statusEnum = ChargeItemPaymentStatus.valueOf((String) row[9]);

        // Le titre des charges courantes n'est jamais stocké : reconstruit ici, sur cette seule ligne
        // de la page déjà récupérée (jamais sur toute la liste) — buildPeriodLabel reste l'unique
        // source de vérité pour ce libellé, réutilisée telle quelle (voir sa surcharge ChargeCall)
        String title = (String) row[2];
        if ("CHARGE".equals(sourceType)) {
            String frequency = (String) row[11];
            int periodNumber = ((Number) row[12]).intValue();
            int year = ((Number) row[13]).intValue();
            title = "Charges " + buildPeriodLabel(frequency, periodNumber, year);
        }

        return MyChargeCardDTO.builder()
                .id(((Number) row[1]).longValue())
                .type(chargeType.name())
                .typeLabel(chargeType.getDescription())
                .title(title)
                .residenceName((String) row[4])
                .residenceId(((Number) row[5]).longValue())
                .propertyReference((String) row[6])
                .remainingAmount((BigDecimal) row[7])
                .dueDate(toLocalDate(row[8]))
                .status(statusEnum.getLabel())
                .paymentBlocked(((Number) row[10]).intValue() == 1)
                .build();
    }

    // Construit le résumé (bandeau haut) à partir de la ligne d'agrégats de sumMyChargesSummary —
    // [0]=totalToPay [1]=pendingCount [2]=nextDueDate
    private MyChargesSummaryDTO buildSummaryFromRow(Object[] row) {
        return MyChargesSummaryDTO.builder()
                .totalToPay((BigDecimal) row[0])
                .pendingCount(((Number) row[1]).intValue())
                .nextDueDate(toLocalDate(row[2]))
                .build();
    }

    // Convertit une colonne date native (renvoyée en java.sql.Date/Timestamp/LocalDateTime selon le
    // driver JDBC) en LocalDate
    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof java.sql.Date d) return d.toLocalDate();
        if (value instanceof java.sql.Timestamp ts) return ts.toLocalDateTime().toLocalDate();
        if (value instanceof LocalDateTime ldt) return ldt.toLocalDate();
        if (value instanceof LocalDate ld) return ld;
        return null;
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

        // Répartition par poste du quotePart réel de cet appel (pas la quote-part annuelle)
        List<ChargeBreakdownLineDTO> breakdown = buildBreakdown(budget, item.getQuotePart());

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
                .status(item.getStatus().getLabel())
                .paymentBlocked(budget.getStatus() == BudgetStatus.CLOSED)
                .breakdown(breakdown)
                // Toujours le vrai montant dû, jamais une re-somme du détail — garanti égal par
                // construction (buildBreakdown redistribue exactement quotePart entre les postes)
                .breakdownTotal(item.getQuotePart())
                .build();
    }

    // Construit le détail complet d'un ExceptionalCallItem
    private MyChargeDetailDTO buildExceptionalCallDetail(ExceptionalCallItem item) {

        ExceptionalCall exceptionalCall = item.getExceptionalCall();
        Residence residence = exceptionalCall.getResidence();
        String propertyRef = findPropertyReference(item.getCoOwner().getId(), residence.getId());

        // Calcule le remainingAmount à la volée
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
                .status(item.getStatus().getLabel())
                .paymentBlocked(exceptionalCall.getStatus() == ExceptionalCallStatus.CLOSED)
                // Pas de répartition par poste pour les appels exceptionnels (pas de BudgetItem lié)
                .breakdown(List.of())
                .breakdownTotal(item.getQuotePart())
                .build();
    }

    // Construit la répartition par poste budgétaire d'un appel de charges précis : redistribue le
    // quotePart réel de la période (jamais la quote-part annuelle) entre les postes, au prorata de
    // leur montant dans le budget, via la méthode du plus grand reste — garantit que la somme des
    // lignes affichées est toujours exactement égale à quotePart, jamais plus, jamais moins.
    private List<ChargeBreakdownLineDTO> buildBreakdown(Budget budget, BigDecimal quotePart) {

        // Poids de chaque poste = son propre montant dans le budget (utilisé comme un tantième)
        Map<Long, BigDecimal> montantByBudgetItemId = new LinkedHashMap<>();
        for (BudgetItem budgetItem : budget.getItems()) {
            montantByBudgetItemId.put(budgetItem.getId(), budgetItem.getMontant());
        }

        List<ChargeBreakdownLineDTO> lines = new ArrayList<>();

        // Rien à répartir si le budget n'a aucun poste
        if (montantByBudgetItemId.isEmpty()) {
            return lines;
        }

        // Répartit exactement quotePart entre les postes, au prorata de leur montant — même
        // méthode que la répartition entre copropriétaires
        Map<Long, BigDecimal> partByBudgetItemId =
                ChargeAllocationUtil.distributeByLargestRemainder(quotePart, montantByBudgetItemId);

        for (BudgetItem budgetItem : budget.getItems()) {
            lines.add(ChargeBreakdownLineDTO.builder()
                    .label(budgetItem.getLibelle())
                    .amount(partByBudgetItemId.get(budgetItem.getId()))
                    .build());
        }

        return lines;
    }

    // Construit le libellé de période lisible (mensuel ou trimestriel)
    private String buildPeriodLabel(ChargeCall chargeCall) {
        return buildPeriodLabel(chargeCall.getFrequency().name(), chargeCall.getPeriodNumber(), chargeCall.getYear());
    }

    // Même logique, à partir de valeurs brutes plutôt que d'une entité ChargeCall — utilisée pour
    // reconstruire le titre d'une ligne issue de findMyChargesUnion (voir buildCardFromRow), seule
    // autre source de vérité pour ce libellé (jamais dupliqué ailleurs pour l'affichage)
    private String buildPeriodLabel(String frequency, int periodNumber, int year) {
        if (ChargeFrequency.MENSUEL.name().equals(frequency)) {
            String[] monthNames = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin",
                    "Juillet", "Août", "Septembre", "Octobre", "Novembre", "Décembre"};
            int index = periodNumber - 1;
            String monthLabel = (index >= 0 && index < monthNames.length) ? monthNames[index] : "";
            return monthLabel + " " + year;
        }

        String[] quarterLabels = {"Jan-Mar", "Avr-Jun", "Jul-Sep", "Oct-Dec"};
        int index = periodNumber - 1;
        String quarterLabel = (index >= 0 && index < quarterLabels.length) ? quarterLabels[index] : "";
        return "T" + periodNumber + " " + year + " (" + quarterLabel + ")";
    }

    // Génère une référence unique avec un préfixe (ex: CPY-123456 ou ECP-123456)
    private String genererReference(String prefix) {
        return prefix + "-" + (int)(Math.random() * 900000 + 100000);
    }


}