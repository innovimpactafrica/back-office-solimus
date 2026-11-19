package com.example.solimus.services.shared;

import com.example.solimus.exceptions.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Canaux de paiement autorisés DANS l'application.
 * <p>
 * Seuls les abonnements (syndic, prestataire) restent payés en ligne. Les charges de copropriété et
 * les travaux sont réglés HORS de Solimus puis justifiés par une preuve validée par le bénéficiaire :
 * le syndic pour une charge, le prestataire pour un acompte / solde.
 * <p>
 * Les confirmations (callbacks TouchPay / GU) restent actives : un paiement en ligne déjà engagé avant
 * la bascule se confirme normalement. Réactivable par configuration, sans redéploiement de code :
 * {@code PAYMENTS_CHARGES_ONLINE_ENABLED=true}, {@code PAYMENTS_TRAVAUX_ONLINE_ENABLED=true}.
 */
@Component
public class PaymentChannelPolicy {

    private final boolean chargesOnlineEnabled;
    private final boolean travauxOnlineEnabled;

    public PaymentChannelPolicy(
            @Value("${app.payments.charges-online.enabled:false}") boolean chargesOnlineEnabled,
            @Value("${app.payments.travaux-online.enabled:false}") boolean travauxOnlineEnabled) {
        this.chargesOnlineEnabled = chargesOnlineEnabled;
        this.travauxOnlineEnabled = travauxOnlineEnabled;
    }

    public void assertChargesOnlineEnabled() {
        if (!chargesOnlineEnabled) {
            throw new BadRequestException("Le paiement des charges se fait désormais hors de l'application : "
                    + "réglez votre syndic directement, puis envoyez votre preuve de paiement "
                    + "(POST /api/coowner/charges/{type}/{id}/payment-proof). Le syndic la valide.");
        }
    }

    public void assertTravauxOnlineEnabled() {
        if (!travauxOnlineEnabled) {
            throw new BadRequestException("Le paiement des travaux se fait désormais hors de l'application : "
                    + "réglez le prestataire directement, puis déclarez le paiement avec sa preuve "
                    + "(…/deposit/payment-proof ou …/balance/payment-proof). Le prestataire confirme la réception.");
        }
    }

    /** Contrôle d'une référence déjà initiée payée par l'endpoint générique Wave / Orange Money. */
    public void assertReferencePayableOnline(String reference) {
        if (reference == null) return;
        if (reference.startsWith("CPY-") || reference.startsWith("ECP-")) {
            assertChargesOnlineEnabled();
        } else if (reference.startsWith("PAY-") || reference.startsWith("SOL-")) {
            assertTravauxOnlineEnabled();
        }
    }
}
