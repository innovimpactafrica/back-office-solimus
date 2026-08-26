package com.example.solimus.services.shared;

import com.example.solimus.dtos.syndic.residence.WalletTransactionDTO;
import com.example.solimus.entities.ChargeCall;
import com.example.solimus.entities.Property;
import com.example.solimus.entities.SyndicWalletTransaction;
import com.example.solimus.enums.WalletTransactionCategory;
import com.example.solimus.repositories.ChargeCallPaymentRepository;
import com.example.solimus.repositories.ExceptionalCallPaymentRepository;
import com.example.solimus.repositories.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// Transforme une SyndicWalletTransaction brute en WalletTransactionDTO prêt à afficher — reconstruit
// le libellé lisible et le(s) lot(s) concerné(s) pour les transactions CHARGES. Partagé entre la
// fiche résidence (transactions récentes) et le module Finances (historique complet).
@Component
@RequiredArgsConstructor
public class WalletTransactionPresenter {

    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;
    private final PropertyRepository propertyRepository;

    public WalletTransactionDTO toDTO(SyndicWalletTransaction tw) {

        String label = tw.getLabel();
        String propertyReference = null;

        // CHARGES : reconstruit un libellé lisible ("Charges — T3 2026" / "Charge exceptionnelle — X")
        // à partir de la source réelle du paiement, et récupère le(s) lot(s) du copropriétaire
        if (tw.getCategory() == WalletTransactionCategory.CHARGES && tw.getCoOwner() != null) {

            if (tw.getReference() != null && tw.getReference().startsWith("CPY-")) {
                label = chargeCallPaymentRepository.findByReference(tw.getReference())
                        .map(payment -> {
                            ChargeCall chargeCall = payment.getChargeCallItem().getChargeCall();
                            return "Charge courante — " + buildSimplePeriodeLabel(chargeCall) + " " + chargeCall.getYear();
                        })
                        .orElse(label);
            } else if (tw.getReference() != null && tw.getReference().startsWith("ECP-")) {
                label = exceptionalCallPaymentRepository.findByReference(tw.getReference())
                        .map(payment -> "Charge exceptionnelle — " + payment.getExceptionalCallItem().getExceptionalCall().getTitle())
                        .orElse(label);
            }

            propertyReference = propertyRepository.findByOwnerIdAndResidenceId(tw.getCoOwner().getId(), tw.getResidence().getId())
                    .stream()
                    .map(Property::getReference)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse(null);
        }

        // TRAVAUX : lot concerné via l'intervention liée, "Partie commune" si pas de lot précis
        if (tw.getCategory() == WalletTransactionCategory.TRAVAUX && tw.getInterventionRequest() != null) {
            propertyReference = (tw.getInterventionRequest().getProperty() != null)
                    ? tw.getInterventionRequest().getProperty().getReference()
                    : "Partie commune";
        }

        return WalletTransactionDTO.builder()
                .id(tw.getId())
                .label(label)
                .residenceName(tw.getResidence() != null ? tw.getResidence().getName() : null)
                .payerOrPayeeName(tw.getBeneficiaryName())
                .propertyReference(propertyReference)
                .reference(tw.getReference())
                .transactionDate(tw.getTransactionDate())
                .amount(tw.getAmount())
                .mode(tw.getMode())
                .category(tw.getCategory())
                .build();
    }

    // Libellé court de la période d'un appel de charges, ex: "T3" (trimestriel) ou "Jan" (mensuel)
    private String buildSimplePeriodeLabel(ChargeCall chargeCall) {
        if (chargeCall.getFrequency() != null && chargeCall.getFrequency().name().equals("TRIMESTRIEL")) {
            return "T" + chargeCall.getPeriodNumber();
        }
        String[] mois = {"Jan", "Fév", "Mar", "Avr", "Mai", "Jun", "Jul", "Aoû", "Sep", "Oct", "Nov", "Déc"};
        int index = chargeCall.getPeriodNumber() - 1;
        return (index >= 0 && index < mois.length) ? mois[index] : "P" + chargeCall.getPeriodNumber();
    }
}
