package com.example.solimus.controllers;

import com.example.solimus.entities.*;
import com.example.solimus.enums.*;
import com.example.solimus.repositories.*;
import com.example.solimus.services.admin.notification.AdminNotificationPreferenceService;
import com.example.solimus.services.auth.EmailService;
import com.example.solimus.services.notification.NotificationService;
import com.example.solimus.services.provider.ProviderService;
import com.example.solimus.services.provider.wallet.WalletService;
import com.example.solimus.services.shared.StatusRecalculationService;
import com.example.solimus.utils.PasswordGeneratorUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.solimus.services.payment.PaymentConfirmationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/payments/intouch")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Paiements - Callback TouchPay", description = "Endpoints publics (sans authentification) : webhook serveur-à-serveur InTouch (source de vérité) "
        + "et pages de redirection navigateur/WebView après paiement (confirmation best-effort uniquement)")
public class SolimusCallbackController {


    // Logique métier de confirmation (succès / échec) par préfixe de référence — partagée avec le
    // flux Wave / Orange Money (MobileMoneyPaymentController)
    private final PaymentConfirmationService paymentConfirmationService;

    // URL de la page "Paramètres" de l'app web Angular
    @Value("${app.syndic-web-app-redirect-url}")
    private String syndicWebAppRedirectUrl;


    // =========================================================================
    // Routes de redirection TouchPay — retour navigateur/WebView.
    // Confirmation "best-effort (pas fiable à 100%)" UNIQUEMENT si TouchPay a lui-même mis payment_status=200
    // =========================================================================

    @Operation(summary = "Page de retour navigateur/WebView après un paiement réussi côté TouchPay",
            description = "Toujours 200 . Ne confirme le paiement en base (best-effort) que si "
                    + "payment_status ou errorCode vaut explicitement 200 dans l'URL de retour — la source de vérité "
                    + "reste le webhook POST /callback. Le chemin \"/payment-success\" est volontaire : la WebView "
                    + "mobile intercepte la navigation dès qu'elle reconnaît ce texte dans l'URL, avant même que "
                    + "cette page HTML ne s'affiche — pas de deeplink solimus:// nécessaire.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page HTML de redirection renvoyée",
                    content = @Content(mediaType = "text/html"))
    })
    // QUAND : le navigateur/WebView revient sur cette URL après un paiement TouchPay (TouchPay
    // redirige l'utilisateur ici lui-même, ce n'est jamais nous qui appelons cette méthode).
    // POURQUOI : confirmation "best-effort" — si TouchPay a mis payment_status/errorCode=200 dans
    // l'URL de retour, on en profite pour confirmer tout de suite ; SINON on ne fait rien de plus,
    // la vraie confirmation reste POST /callback ci-dessous.
    @GetMapping(value = "/payment-success", produces = "text/html")
    public String redirectPaymentSuccess(
            @RequestParam("num_command") String reference,
            @RequestParam(value = "payment_status", required = false) String paymentStatus,
            @RequestParam(value = "errorCode", required = false) String errorCode) {

        // Confirmation best-effort uniquement si TouchPay confirme explicitement 200 —
        // via payment_status (doc REST /cashin) OU errorCode (confirmé par test réel sur le widget hosted)
        boolean confirmed = "200".equals(paymentStatus) || "200".equals(errorCode);
        if (confirmed) {
            paymentConfirmationService.routeCallbackByPrefix(reference, true);
        }

        // SYR- vient du self-service syndic sur le web : on renvoie vers l'app Angular, pas vers le
        // mobile (l'app web n'a pas de WebView interceptant l'URL)
        if (reference.startsWith("SYR-")) {
            String webRedirect = syndicWebAppRedirectUrl + "?paymentStatus=success&ref=" + reference;
            return "<html><body>" +
                    "<script>window.location.href = '" + webRedirect + "';</script>" +
                    "</body></html>";
        }

        // Mobile (CPY-/ECP-/SYN-/...) : la WebView a déjà intercepté cette URL (contient "payment-success")
        // avant ce rendu — cette page ne sert que de filet de sécurité si jamais elle s'affiche quand même
        return "<html><body style=\"text-align:center; font-family:sans-serif; margin-top:50px;\">" +
                "<h1 style=\"color:#0f8a3b;\">Paiement réussi</h1>" +
                "<p>Référence : " + reference + "</p>" +
                "</body></html>";
    }

    @Operation(summary = "Page de retour navigateur/WebView après un paiement échoué côté TouchPay",
            description = "Toujours 200 (page HTML passive). Ne marque l'échec en base (best-effort) que si "
                    + "payment_status ou errorCode est explicitement présent et différent de 200 dans l'URL de retour "
                    + "— la source de vérité reste le webhook POST /callback. Le chemin \"/payment-failed\" est "
                    + "volontaire, voir /payment-success pour l'explication.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page HTML de redirection renvoyée",
                    content = @Content(mediaType = "text/html"))
    })
    // QUAND : même chose que /payment-success mais côté échec — TouchPay redirige ici lui-même.
    // POURQUOI : marquer l'échec best-effort si TouchPay le confirme explicitement dans l'URL —
    // jamais juste parce que l'utilisateur est arrivé sur cette page.
    @GetMapping(value = "/payment-failed", produces = "text/html")
    public String redirectPaymentFailed(
            @RequestParam("num_command") String reference,
            @RequestParam(value = "payment_status", required = false) String paymentStatus,
            @RequestParam(value = "errorCode", required = false) String errorCode) {

        // On ne marque échoué que si TouchPay le confirme explicitement (payment_status ou
        // errorCode présent et différent de 200) — jamais juste parce que l'utilisateur est arrivé ici
        boolean failureConfirmed = (paymentStatus != null && !"200".equals(paymentStatus))
                || (errorCode != null && !"200".equals(errorCode));
        if (failureConfirmed) {
            paymentConfirmationService.routeCallbackByPrefix(reference, false);
        }

        // Même logique que pour le succès : SYR- retourne vers l'app Angular
        if (reference.startsWith("SYR-")) {
            String webRedirect = syndicWebAppRedirectUrl + "?paymentStatus=failed&ref=" + reference;
            return "<html><body>" +
                    "<script>window.location.href = '" + webRedirect + "';</script>" +
                    "</body></html>";
        }

        // Mobile : la WebView a déjà intercepté cette URL (contient "payment-failed") avant ce rendu
        return "<html><body style=\"text-align:center; font-family:sans-serif; margin-top:50px;\">" +
                "<h1 style=\"color:#c62828;\">Paiement non confirmé</h1>" +
                "<p>Référence : " + reference + "</p>" +
                "</body></html>";
    }

    // =========================================================================
    // ENDPOINT PRINCIPAL
    // QUAND : appelé automatiquement par le SERVEUR de TouchPay/InTouch (pas par un navigateur),
    // dès qu'un paiement est réellement confirmé ou définitivement échoué côté opérateur mobile
    // money. C'est TouchPay qui doit avoir CETTE URL exacte enregistrée dans son portail marchand
    // (SOLI26685) — rien dans notre code ne la lui envoie.
    // POURQUOI : c'est la SEULE source de vérité de toute l'application pour confirmer un paiement.
    // Si cet endpoint n'est jamais appelé (mauvaise URL côté TouchPay), aucun paiement ne peut
    // jamais être confirmé, quel que soit ce qui se passe côté navigateur/app.
    // =========================================================================
    @Operation(summary = "Webhook serveur-à-serveur InTouch — seule source de vérité pour la confirmation d'un paiement",
            description = "Réponse toujours au format {success, message}. 400 si la référence est manquante, "
                    + "si son préfixe n'est reconnu par aucun type de paiement, ou si l'entité correspondante est introuvable. "
                    + "200 dans tous les autres cas (y compris paiement marqué échoué, ou callback déjà traité — idempotent).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Callback traité avec succès (paiement confirmé, marqué échoué, ou déjà traité)"),
            @ApiResponse(responseCode = "400", description = "Référence manquante, préfixe de référence non supporté, ou entité introuvable pour cette référence")
    })
    @PostMapping("/callback")
    @Transactional
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody InTouchCallbackRequest request) {

        log.info("InTouch callback reçu: partnerTx={}, guTx={}, status={}",
                request.getPartnerTransactionId(),
                request.getGuTransactionId(),
                request.getStatus());

        // partner_transaction_id = notre référence interne
        // PAY-xxxxxx = acompte owner, SOL-xxxxxx = solde owner, SUB-xxxxxx = abonnement, CPY-xxxxxx = charge courante, ECP-xxxxxx = charge exceptionnelle
        String ref = request.getPartnerTransactionId();
        if (ref == null || ref.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Référence manquante"
            ));
        }

        // InTouch renvoie SUCCESSFUL quand le paiement est réellement confirmé
        String status = request.getStatus() != null
                ? request.getStatus().trim().toUpperCase() : "";
        boolean succes = "SUCCESSFUL".equals(status);

        return paymentConfirmationService.routeCallbackByPrefix(ref, succes);
    }
}
