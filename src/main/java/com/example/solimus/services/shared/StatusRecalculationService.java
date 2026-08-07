package com.example.solimus.services.shared;

import com.example.solimus.entities.Budget;
import com.example.solimus.entities.ChargeCall;
import com.example.solimus.entities.ChargeCallItem;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.Residence;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.PaymentDelayStatus;
import com.example.solimus.enums.PropertyDisplayStatus;
import com.example.solimus.enums.PropertyStatus;
import com.example.solimus.enums.ResidenceHealthStatus;
import com.example.solimus.enums.UrgencyLevel;
import com.example.solimus.repositories.BudgetRepository;
import com.example.solimus.repositories.ChargeCallItemRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.utils.PaymentStatusUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Recalcule et persiste les statuts affichés dérivés (Property.displayStatus,
 * Residence.healthStatus) — seule source de vérité du projet pour ces deux calculs.
 * Appelée à chaque événement pertinent (paiement, intervention, changement de statut d'un
 * lot), et rattrapée quotidiennement par un job planifié pour les cas de simple écoulement
 * du temps (un retard qui apparaît sans qu'aucun événement ne se produise).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StatusRecalculationService {

    private final PropertyRepository propertyRepository;
    private final ResidenceRepository residenceRepository;
    private final ChargeCallItemRepository chargeCallItemRepository;
    private final BudgetRepository budgetRepository;
    private final InterventionRequestRepository interventionRequestRepository;

    // Recalcule et sauvegarde le displayStatus d'un lot
    @Transactional
    public void recalculatePropertyDisplayStatus(Property property) {
        property.setDisplayStatus(computePropertyDisplayStatus(property));
        propertyRepository.save(property);
    }

    // Recalcule et sauvegarde le healthStatus d'une résidence
    @Transactional
    public void recalculateResidenceHealthStatus(Residence residence) {
        residence.setHealthStatus(computeResidenceHealthStatus(residence.getId()));
        residenceRepository.save(residence);
    }

    // =========================================================================
    // JOB PLANIFIÉ QUOTIDIEN
    // =========================================================================

    @Scheduled(cron = "0 0 0 * * *") // Chaque jour à minuit
    @Transactional
    public void dailyStatusRecalculation() {

        // Recalcule le displayStatus de tous les lots ayant un impayé en cours
        List<Property> propertiesWithUnpaidCharges = propertyRepository.findPropertiesWithUnpaidCharges();
        for (Property property : propertiesWithUnpaidCharges) {
            property.setDisplayStatus(computePropertyDisplayStatus(property));
        }
        propertyRepository.saveAll(propertiesWithUnpaidCharges);

        // Recalcule le healthStatus de toutes les résidences
        List<Residence> allResidences = residenceRepository.findAll();
        for (Residence residence : allResidences) {
            residence.setHealthStatus(computeResidenceHealthStatus(residence.getId()));
        }
        residenceRepository.saveAll(allResidences);

        log.info("Recalcul quotidien des statuts terminé : {} lot(s), {} résidence(s)",
                propertiesWithUnpaidCharges.size(), allResidences.size());
    }

    // =========================================================================
    // MÉTHODES DE CALCUL (privées)
    // =========================================================================

    // Calcule le statut d'affichage d'un lot : MAINTENANCE > statut de paiement > OCCUPIED/VACANT
    private PropertyDisplayStatus computePropertyDisplayStatus(Property property) {

        // Priorité 1 : maintenance
        if (property.getStatus() == PropertyStatus.MAINTENANCE) {
            return PropertyDisplayStatus.MAINTENANCE;
        }

        // Priorité 2 : statut de paiement du propriétaire
        PaymentDelayStatus delayStatus = computeOwnerPaymentDelayStatus(property);
        if (delayStatus == PaymentDelayStatus.UNPAID) {
            return PropertyDisplayStatus.UNPAID;
        }
        if (delayStatus == PaymentDelayStatus.LATE) {
            return PropertyDisplayStatus.LATE;
        }

        // Priorité 3 : statut brut du lot
        return property.getStatus() == PropertyStatus.OCCUPIED
                ? PropertyDisplayStatus.OCCUPIED
                : PropertyDisplayStatus.VACANT;
    }

    // Recherche le paiement en attente le plus ancien du propriétaire pour cette résidence,
    // et en déduit son statut de retard via PaymentStatusUtils (seule source de vérité)
    private PaymentDelayStatus computeOwnerPaymentDelayStatus(Property property) {
        if (property.getOwner() == null) {
            return PaymentDelayStatus.UP_TO_DATE;
        }

        List<ChargeCallItem> pendingItems = chargeCallItemRepository.findPendingItemsByCoOwnerAndResidence(
                property.getOwner().getId(), property.getResidence().getId(), PageRequest.of(0, 1));

        if (pendingItems.isEmpty()) {
            return PaymentDelayStatus.UP_TO_DATE;
        }

        LocalDate dueDate = pendingItems.get(0).getChargeCall().getDueDate();
        return PaymentStatusUtils.computeDelayStatus(dueDate, false, LocalDate.now());
    }

    // Calcule le statut de santé d'une résidence : CRITIQUE si impayés > 10% ou intervention
    // URGENT active, ATTENTION si impayés > 5%, EXCELLENT sinon
    private ResidenceHealthStatus computeResidenceHealthStatus(Long residenceId) {

        BigDecimal totalDue = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        List<Budget> budgets = budgetRepository.findByResidenceId(residenceId);
        for (Budget budget : budgets) {
            for (ChargeCall chargeCall : budget.getChargeCalls()) {
                for (ChargeCallItem item : chargeCall.getItems()) {
                    totalDue = totalDue.add(item.getQuotePart());
                    totalPaid = totalPaid.add(item.getPaidAmount() != null ? item.getPaidAmount() : BigDecimal.ZERO);
                }
            }
        }

        double unpaidRate = 0.0;
        if (totalDue.compareTo(BigDecimal.ZERO) > 0) {
            unpaidRate = totalDue.subtract(totalPaid)
                    .divide(totalDue, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        boolean hasUrgentActiveIntervention = interventionRequestRepository
                .findAllByResidenceId(residenceId)
                .stream()
                .anyMatch(ir -> ir.getUrgencyLevel() == UrgencyLevel.URGENT
                        && ir.getStatus() != InterventionStatus.FINAL_VALIDATION
                        && ir.getStatus() != InterventionStatus.CANCELLED);

        if (unpaidRate > 10.0 || hasUrgentActiveIntervention) {
            return ResidenceHealthStatus.CRITIQUE;
        } else if (unpaidRate > 5.0) {
            return ResidenceHealthStatus.ATTENTION;
        } else {
            return ResidenceHealthStatus.EXCELLENT;
        }
    }
}
