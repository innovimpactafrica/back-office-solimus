package com.example.solimus.controllers;

import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.InTouchCallbackRequest;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.enums.PaymentType;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.services.provider.ProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/intouch")
@RequiredArgsConstructor
@Slf4j
public class SolimusCallbackController {

    // Repository des paiements d'intervention : acompte PAY-* et solde SOL-*.
    private final PaymentRepository paymentRepository;

    // Repository de la demande d'intervention liée au paiement.
    private final InterventionRequestRepository interventionRepository;

    // Service utilisé pour créditer le wallet du prestataire après confirmation InTouch.
    private final ProviderService providerService;

    // Endpoint appelé automatiquement par InTouch après le traitement du paiement TouchPay.
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleCallback(
            @RequestBody InTouchCallbackRequest request) {

        log.info("InTouch callback reçu: partnerTx={}, guTx={}, status={}",
                request.getPartnerTransactionId(), request.getGuTransactionId(), request.getStatus());

        // partner_transaction_id correspond à notre référence interne : PAY-xxxxxx ou SOL-xxxxxx.
        String ref = request.getPartnerTransactionId();
        if (ref == null || ref.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Référence manquante"
            ));
        }

        // InTouch renvoie SUCCESSFUL quand le paiement est réellement confirmé.
        String status = request.getStatus() != null
                ? request.getStatus().trim().toUpperCase() : "";
        boolean succes = "SUCCESSFUL".equals(status);

        // Pour l'instant, ce callback gère uniquement les paiements d'intervention.
        if (ref.startsWith("PAY-") || ref.startsWith("SOL-")) {
            return handleInterventionPaymentCallback(ref, succes);
        }

        return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Type de référence non supporté"
        ));
    }

    private ResponseEntity<Map<String, Object>> handleInterventionPaymentCallback(String ref, boolean succes) {
        // On retrouve le paiement PENDING créé avant l'ouverture de la WebView TouchPay.
        return paymentRepository.findByReference(ref)
                .map(payment -> {
                    // Sécurité anti-double callback : InTouch peut parfois rappeler plusieurs fois.
                    if (payment.getStatus() == PaymentStatus.COMPLETED) {
                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement déjà confirmé"
                        ));
                    }

                    // Si InTouch indique un échec, on ne crédite pas le wallet et on ne modifie pas les montants.
                    if (!succes) {
                        payment.setStatus(PaymentStatus.FAILED);
                        paymentRepository.save(payment);

                        return ResponseEntity.ok(Map.<String, Object>of(
                                "success", true,
                                "message", "Paiement marqué comme échoué"
                        ));
                    }

                    // À partir d'ici, InTouch a confirmé le paiement : on marque donc le paiement comme validé.
                    payment.setStatus(PaymentStatus.COMPLETED);
                    payment.setPaidAt(LocalDateTime.now());
                    paymentRepository.save(payment);

                    // Le prestataire reçoit l'argent seulement après confirmation réelle du paiement.
                    providerService.crediterWallet(
                            payment.getProvider().getId(),
                            payment.getAmount()
                    );

                    // On récupère la demande d'intervention pour synchroniser sa partie financière.
                    InterventionRequest req = payment.getInterventionRequest();

                    if (payment.getType() == PaymentType.ACOMPTE) {
                        // Ici depositAmount veut dire "montant déjà payé au total" sur la demande.
                        // Après un acompte, le seul montant déjà payé est justement cet acompte.
                        req.setDepositAmount(payment.getAmount());

                        // Le solde restant devient totalAmount - acompte.
                        // Attention : InterventionRequest.@PreUpdate recalcule aussi ce champ à la sauvegarde.
                        req.setRemainingAmount(req.getTotalAmount().subtract(payment.getAmount()));
                    } else if (payment.getType() == PaymentType.SOLDE) {
                        // Ici le syndic vient compléter le reste à payer.
                        // Même si payment.getAmount() contient seulement le solde payé maintenant,
                        // après ce paiement la demande est payée entièrement.
                        // Donc le "montant déjà payé au total" devient le montant total du devis.
                        // Pour que le solde reste à 0 après @PreUpdate, depositAmount doit égaler totalAmount.
                        req.setDepositAmount(req.getTotalAmount() != null ? req.getTotalAmount() : BigDecimal.ZERO);

                        // Valeur explicite pour le code métier et la lisibilité avant sauvegarde.
                        req.setRemainingAmount(BigDecimal.ZERO);

                        // Le paiement du solde clôture financièrement et valide définitivement l'intervention.
                        req.setStatus(InterventionStatus.FINAL_VALIDATION);
                        req.setValidatedAt(LocalDateTime.now());
                    }

                    // Sauvegarde finale de la demande : déclenche aussi @PreUpdate dans InterventionRequest.
                    interventionRepository.save(req);

                    return ResponseEntity.ok(Map.<String, Object>of(
                            "success", true,
                            "message", "Callback traité avec succès"
                    ));
                })
                .orElseGet(() -> ResponseEntity.badRequest().body(Map.<String, Object>of(
                        "success", false,
                        "message", "Paiement introuvable"
                )));
    }
}
