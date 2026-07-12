package com.example.solimus.controllers;

import com.example.solimus.dtos.syndic.PaymentBridgeDTO;
import com.example.solimus.entities.*;

import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
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
    private final WithdrawalRequestRepository withdrawalRequestRepository;
    private final ChargeCallPaymentRepository chargeCallPaymentRepository;
    private final ExceptionalCallPaymentRepository exceptionalCallPaymentRepository;


    // Référentiel des abonnements prestataires : SUB-*
    private final SubscriptionRepository subscriptionRepository;

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
    // BRIDGE — Paiement Syndic/Owner → Prestataire (Acompte ou Solde)
    // =========================================================================
    /**
     * Récupère les données de transaction pour le paiement d'une intervention.
     * Appelé publiquement par la WebView (touchpay-bridge.html) avec la référence.
     *
     * @param transactionRef Référence unique de la transaction (ex: PAY-123456)
     * @return DTO contenant tous les paramètres requis pour initialiser l'interface TouchPay
     */
    @GetMapping("/payment/{transactionRef}")
    @Transactional(readOnly = true)
    public PaymentBridgeDTO getBridgePayment(@PathVariable String transactionRef) {
        log.info("🌉 Requête Bridge reçue pour le paiement d'intervention avec la référence : {}", transactionRef);

        // 1. Rechercher la transaction de paiement en base de données
        PaymentProvider payment = paymentRepository
                .findByReference(transactionRef)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement introuvable avec la référence : " + transactionRef));

        // 2. Récupérer l'initiateur du paiement (syndic OU copropriétaire) pour préremplir ses infos de contact
        User initiator = payment.getPaymentInitiator();

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
                .customerEmail(initiator.getEmail())                                // Email du client (syndic ou copropriétaire)
                .customerFirstName(initiator.getFirstName())                        // Prénom du client
                .customerLastName(initiator.getLastName())                          // Nom du client
                .customerPhone(initiator.getPhone())                                // Téléphone du client
                .build();
    }

    // ================================================
    // BRIDGE — Abonnement Premium prestataire
    // ================================================
    @GetMapping("/subscription/{transactionRef}")
    @Transactional(readOnly = true)
    public PaymentBridgeDTO getBridgeSubscription(@PathVariable String transactionRef) {

        // On recherche la Subscription créée en PENDING par le service initiatePayment
        Subscription subscription = subscriptionRepository
                .findByTransactionRef(transactionRef)
                // Si rien n'est trouvé, la référence envoyée par le front est invalide ou expirée
                .orElseThrow(() -> new ResourceNotFoundException("Abonnement introuvable"));

        // On récupère le prestataire concerné, pour préremplir ses infos dans TouchPay
        User provider = subscription.getProvider();

        // On construit le même DTO — structure commune à tout le bridge
        return PaymentBridgeDTO.builder()
                // Token marchand fixe, identique pour tous les types de paiement
                .merchantToken(touchPaySecureCode)

                // La référence exacte que TouchPay doit utiliser pour le callback
                .transactionReference(transactionRef)

                // Code d'agence fixe, configuré une fois dans application.properties
                .agencyCode(touchPayAgencyCode)

                // Constante de service TouchPay
                .serviceId(touchPayDomainName)

                // URL du script JS TouchPay à charger dans la WebView
                .hostedScriptUrl(touchPayHostedScriptUrl)

                // Montant exact figé au moment de l'initiation (mensuel ou annuel)
                .amount(subscription.getAmountPaid())

                // Ville par défaut pour la facturation
                .city(touchPayDefaultCity)

                // Redirection si le paiement réussit
                .successRedirectUrl(touchPaySuccessRedirectUrl)

                // Redirection si le paiement échoue
                .failedRedirectUrl(touchPayFailedRedirectUrl)

                // Email du prestataire, prérempli dans le formulaire TouchPay
                .customerEmail(provider.getEmail())

                // Prénom du prestataire
                .customerFirstName(provider.getFirstName())

                // Nom du prestataire
                .customerLastName(provider.getLastName())

                // Téléphone du prestataire
                .customerPhone(provider.getPhone())

                .build();
    }
    // ================================================
    // BRIDGE — Paiement charge courante copropriétaire (CPY-)
    // ================================================
    @GetMapping("/charge/{transactionRef}")
    @Transactional(readOnly = true)
    public PaymentBridgeDTO getBridgeCharge(@PathVariable String transactionRef) {
        ChargeCallPayment paiement = chargeCallPaymentRepository
                .findByReference(transactionRef)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement charge introuvable"));

        User owner = paiement.getOwner();

        return PaymentBridgeDTO.builder()
                .merchantToken(touchPaySecureCode)
                .transactionReference(transactionRef)
                .agencyCode(touchPayAgencyCode)
                .serviceId(touchPayDomainName)
                .hostedScriptUrl(touchPayHostedScriptUrl)
                .amount(paiement.getAmount())
                .city(touchPayDefaultCity)
                .successRedirectUrl(touchPaySuccessRedirectUrl)
                .failedRedirectUrl(touchPayFailedRedirectUrl)
                .customerEmail(owner.getEmail())
                .customerFirstName(owner.getFirstName())
                .customerLastName(owner.getLastName())
                .customerPhone(owner.getPhone())
                .build();
    }

    // ================================================
    // BRIDGE — Paiement charge exceptionnelle copropriétaire (ECP-)
   // ================================================
    @GetMapping("/exceptional-charge/{transactionRef}")
    @Transactional(readOnly = true)
    public PaymentBridgeDTO getBridgeExceptionalCharge(@PathVariable String transactionRef) {
        ExceptionalCallPayment paiement = exceptionalCallPaymentRepository
                .findByReference(transactionRef)
                .orElseThrow(() -> new ResourceNotFoundException("Paiement charge exceptionnelle introuvable"));

        User owner = paiement.getOwner();

        return PaymentBridgeDTO.builder()
                .merchantToken(touchPaySecureCode)
                .transactionReference(transactionRef)
                .agencyCode(touchPayAgencyCode)
                .serviceId(touchPayDomainName)
                .hostedScriptUrl(touchPayHostedScriptUrl)
                .amount(paiement.getAmount())
                .city(touchPayDefaultCity)
                .successRedirectUrl(touchPaySuccessRedirectUrl)
                .failedRedirectUrl(touchPayFailedRedirectUrl)
                .customerEmail(owner.getEmail())
                .customerFirstName(owner.getFirstName())
                .customerLastName(owner.getLastName())
                .customerPhone(owner.getPhone())
                .build();
    }


}