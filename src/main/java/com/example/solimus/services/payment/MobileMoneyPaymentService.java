package com.example.solimus.services.payment;

import com.example.solimus.dtos.payment.MobileMoneyPaymentRequestDTO;
import com.example.solimus.dtos.payment.MobileMoneyPaymentResponseDTO;
import com.example.solimus.enums.MobileMoneyOperator;

import java.util.Map;

/**
 * Paiement Wave / Orange Money via l'API TouchPay (GU) :
 * PUT {baseUrl}/{codeAgence}/transaction?loginAgent=…&passwordAgent=…
 *
 * Indépendant du flux TouchPay "hosted" (pont touchpay-bridge.html), qui reste en place. Les deux
 * flux partagent la même confirmation métier (PaymentConfirmationService) : une fois le paiement
 * confirmé, les mêmes tables passent au statut payé (COMPLETED / ACTIVE).
 */
public interface MobileMoneyPaymentService {

    /**
     * Lance le paiement d'une référence déjà initiée et encore en attente (SYN-, SYR-, SYA-, PRA-,
     * SUB-, CPY-, ECP-, PAY-, SOL-) avec l'opérateur choisi. Le montant est celui du serveur.
     */
    MobileMoneyPaymentResponseDTO initiatePayment(String reference, MobileMoneyOperator operator,
                                                  MobileMoneyPaymentRequestDTO dto);

    /**
     * Contrôle préalable, AVANT de créer une tentative en attente : API configurée, numéro
     * exploitable (celui fourni, sinon le téléphone du profil) et URLs de retour mobile valides.
     */
    void preflight(String phoneNumber, String returnUrl, String cancelUrl);

    /** Paiement Wave (serviceCode SNPAIEMENTWAVE). */
    MobileMoneyPaymentResponseDTO createPaymentWave(String reference, MobileMoneyPaymentRequestDTO dto);

    /** Paiement Orange Money (serviceCode PAIEMENTMARCHANDOM). */
    MobileMoneyPaymentResponseDTO createPaymentOM(String reference, MobileMoneyPaymentRequestDTO dto);

    /**
     * Retour navigateur après paiement (return_url) : vérifie la signature du lien, confirme le
     * paiement (statut payé) puis renvoie l'URL de l'application vers laquelle rediriger.
     */
    String handleReturn(String reference, String signature);

    /**
     * Appelé par l'application web quand elle est ouverte sur son return_url (?ref=…&sig=…) : vérifie
     * la signature et passe la transaction au statut payé (même traitement que le callback TouchPay).
     */
    Map<String, Object> confirmFromApp(String reference, String signature);

    /** Retour navigateur après annulation (cancel_url) : marque le paiement échoué, renvoie l'URL de redirection. */
    String handleCancel(String reference, String signature);

    /** Webhook serveur-à-serveur GU (callback) : confirme ou fait échouer le paiement selon le statut reçu. */
    Map<String, Object> handleCallback(Map<String, Object> payload);
}
