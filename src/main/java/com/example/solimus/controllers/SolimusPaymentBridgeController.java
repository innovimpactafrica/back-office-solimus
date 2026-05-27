package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.PaymentBridgeDTO;
import com.example.solimus.entities.Payment;
import com.example.solimus.entities.SubscriptionPayment;
import com.example.solimus.entities.User;
import com.example.solimus.entities.WithdrawalRequest;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.PaymentRepository;
import com.example.solimus.repositories.SubscriptionPaymentRepository;
import com.example.solimus.repositories.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

/**
 * Contrôleur faisant office de pont (Bridge) entre le backend SOLIMUS et l'interface TouchPay (WebView).
 * Il permet à la page HTML touchpay-bridge.html de charger de manière sécurisée les paramètres 
 * requis par le script TouchPay en fonction d'une référence unique de transaction.
 */
@RestController
@RequestMapping("/api/payments/bridge")
@RequiredArgsConstructor
@Slf4j
public class SolimusPaymentBridgeController {

    // Référentiel des paiements (syndic -> prestataire)
    private final PaymentRepository paymentRepository;
    private final SubscriptionPaymentRepository subscriptionPaymentRepository;
    private final WithdrawalRequestRepository withdrawalRequestRepository;

    // URL du script hébergé TouchPay (géré par InTouch)
    @Value("${touchpay.hosted.script-url}")
    private String touchPayHostedScriptUrl;

    // Code d'agence fourni par InTouch
    @Value("${touchpay.agency-code}")
    private String touchPayAgencyCode;

    // Clé ou Token marchand sécurisé fourni par InTouch
    @Value("${touchpay.token}")
    private String touchPaySecureCode;

    // Nom de domaine ou identifiant de service enregistré chez InTouch
    @Value("${touchpay.hosted.domain-name}")
    private String touchPayDomainName;

    // URL de redirection après un paiement réussi
    @Value("${touchpay.hosted.success-redirect-url}")
    private String touchPaySuccessRedirectUrl;

    // URL de redirection après un échec de paiement
    @Value("${touchpay.hosted.failed-redirect-url}")
    private String touchPayFailedRedirectUrl;

    // Ville par défaut pour la facturation TouchPay (ex: Dakar)
    @Value("${touchpay.hosted.default-city:Dakar}")
    private String touchPayDefaultCity;


    // =========================================================================
    // BRIDGE — Paiement Syndic → Prestataire (Acompte ou Solde)
    // =========================================================================
    /**
     * Récupère les données de transaction pour le paiement d'une intervention.
     * Appelé publiquement par la WebView (touchpay-bridge.html) avec la référence.
     *
     * @param transactionRef Référence unique de la transaction (ex: PAY-123456)
     * @return DTO contenant tous les paramètres requis pour initialiser l'interface TouchPay
     */
    @GetMapping("/payment/{transactionRef}")
    public PaymentBridgeDTO getBridgePayment(@PathVariable String transactionRef) {
        log.info("🌉 Requête Bridge reçue pour le paiement d'intervention avec la référence : {}", transactionRef);

        // 1. Rechercher la transaction de paiement en base de données
        Payment payment = paymentRepository
            .findByReference(transactionRef)
            .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable avec la référence : " + transactionRef));

        // 2. Récupérer le syndic initiateur du paiement (pour préremplir ses infos de contact)
        User syndic = payment.getSyndic();

        // 3. Retourner le payload structuré pour la WebView TouchPay
        return PaymentBridgeDTO.builder()
            .merchantToken(touchPaySecureCode)                                  // Token marchand d'authentification
            .transactionReference(transactionRef)                               // Référence unique de transaction
            .agencyCode(touchPayAgencyCode)                                     // Code d'agence InTouch
            .serviceId(touchPayDomainName)                                      // Identifiant de service (ex: TouchPay)
            .hostedScriptUrl(touchPayHostedScriptUrl)                           // Script JS à charger dans le bridge
            .amount(payment.getAmount())                                        // Montant exact à prélever
            .city(touchPayDefaultCity)                                          // Ville par défaut
            .successRedirectUrl(touchPaySuccessRedirectUrl)                     // URL de redirection si réussite
            .failedRedirectUrl(touchPayFailedRedirectUrl)                       // URL de redirection si échec
            .customerEmail(syndic.getEmail())                                   // Email du client (syndic)
            .customerFirstName(syndic.getFirstName())                           // Prénom du client
            .customerLastName(syndic.getLastName())                             // Nom du client
            .customerPhone(syndic.getPhone())                                   // Téléphone du client
            .build();
    }
    // ================================================
    // BRIDGE — Abonnement Premium prestataire
    // ================================================
    @GetMapping("/subscription/{transactionRef}")
    public PaymentBridgeDTO getBridgeSubscription(@PathVariable String transactionRef) {
        SubscriptionPayment paiement = subscriptionPaymentRepository
            .findByReference(transactionRef)
            .orElseThrow(() -> new ResourceNotFoundException("Paiement abonnement introuvable"));

        User provider = paiement.getSubscription().getProvider();

        return PaymentBridgeDTO.builder()
            .merchantToken(touchPaySecureCode)
            .transactionReference(transactionRef)
            .agencyCode(touchPayAgencyCode)
            .serviceId(touchPayDomainName)
            .hostedScriptUrl(touchPayHostedScriptUrl)
            .amount(paiement.getMontant())
            .city(touchPayDefaultCity)
            .successRedirectUrl(touchPaySuccessRedirectUrl)
            .failedRedirectUrl(touchPayFailedRedirectUrl)
            .customerEmail(provider.getEmail())
            .customerFirstName(provider.getFirstName())
            .customerLastName(provider.getLastName())
            .customerPhone(provider.getPhone())
            .build();
    }



}
