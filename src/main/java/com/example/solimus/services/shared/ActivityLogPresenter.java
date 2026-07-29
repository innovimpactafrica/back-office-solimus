package com.example.solimus.services.shared;

import com.example.solimus.dtos.syndic.dashboard.ActivityRowDTO;
import com.example.solimus.entities.ActivityLog;
import com.example.solimus.entities.ChargeCall;
import com.example.solimus.enums.ActivityType;
import com.example.solimus.enums.ChargeCallStatus;
import com.example.solimus.repositories.ChargeCallRepository;
import com.example.solimus.repositories.ExceptionalCallRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;

// Transforme des ActivityLog bruts en lignes de tableau "Activité Récente", prêtes à afficher.
// Partagé entre le tableau de bord syndic (ses propres résidences) et la fiche détail syndic
// vue par l'admin (mêmes règles d'affichage, seul le syndicId interrogé change).
@Component
@RequiredArgsConstructor
public class ActivityLogPresenter {

    private final ChargeCallRepository chargeCallRepository;
    private final ExceptionalCallRepository exceptionalCallRepository;
    private final InterventionRequestRepository interventionRequestRepository;

    // Construit une ligne du tableau "Activités Récentes" à partir d'un log brut
    public ActivityRowDTO buildActivityRow(ActivityLog log) {

        ActivityRowDTO dto = new ActivityRowDTO();
        // Traduit le type technique en libellé affiché (ex: PAYMENT_RECEIVED → "Paiement")
        dto.setType(mapActivityTypeToLabel(log.getType()));
        dto.setDescription(log.getMessage());
        dto.setResidenceName(log.getResidence().getName());
        dto.setOccurredAt(log.getCreatedAt());
        dto.setRelativeTime(buildRelativeTime(log.getCreatedAt()));
        // Va chercher le vrai statut de l'entité liée (intervention, appel de charges...)
        dto.setStatus(resolveActivityStatus(log));

        return dto;
    }

    // Convertit une date en texte relatif ("Il y a 2h", "Hier", "Il y a 3 jours"...)
    public String buildRelativeTime(LocalDateTime date) {
        Duration duration = Duration.between(date, LocalDateTime.now());
        long hours = duration.toHours();

        if (hours < 1) return "Il y a " + duration.toMinutes() + " min";
        if (hours < 24) return "Il y a " + hours + "h";
        if (hours < 48) return "Hier";
        return "Il y a " + (hours / 24) + " jours";
    }

    // Traduit l'ActivityType en libellé de colonne "Type" affiché
    private String mapActivityTypeToLabel(ActivityType type) {
        return switch (type) {
            case PAYMENT_RECEIVED, CHARGE_CALL_GENERATED -> "Paiement";
            case INTERVENTION_REPORTED, INTERVENTION_RESOLVED -> "Incident";
            case PROVIDER_ASSIGNED -> "Prestataire";
            case MEETING_CREATED, MEETING_PUBLISHED, MEETING_DELETED -> "Réunion";
            case MEETING_DOCUMENT_ADDED, MEETING_DOCUMENT_DOWNLOADED, MEETING_DOCUMENT_VIEWED, MEETING_DOCUMENT_UPDATED, MEETING_DOCUMENT_DELETED -> "Document";
            case COMMENT_ADDED -> "Commentaire";
            case BUDGET_CREATED, BUDGET_CLOSED, BUDGET_DELETED -> "Budget";
            case EXCEPTIONAL_CALL_CREATED, EXCEPTIONAL_CALL_ACTIVATED, EXCEPTIONAL_CALL_CLOSED -> "Appel exceptionnel";
        };
    }

    // Résout le statut d'une activité en interrogeant l'entité liée (relatedEntityType + relatedEntityId)
    private String resolveActivityStatus(ActivityLog log) {

        if (log.getRelatedEntityType() == null || log.getRelatedEntityId() == null) {
            return null;
        }

        switch (log.getRelatedEntityType()) {
            case "CHARGE_CALL":
                return chargeCallRepository.findById(log.getRelatedEntityId())
                        .map(cc -> calculateChargeCallStatus(cc).getLabel())
                        .orElse(null);

            case "EXCEPTIONAL_CALL":
                return exceptionalCallRepository.findById(log.getRelatedEntityId())
                        .map(ec -> ec.getStatus().getLabel())
                        .orElse(null);

            case "INTERVENTION":
                return interventionRequestRepository.findById(log.getRelatedEntityId())
                        .map(i -> i.getStatus().getLabel())
                        .orElse(null);

            default:
                return null;
        }
    }

    // Calcule le statut de l'appel de charges à la volée : SETTLED, PARTIAL ou SENT (jamais stocké en base)
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
}
