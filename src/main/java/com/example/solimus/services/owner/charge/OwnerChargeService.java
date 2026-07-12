package com.example.solimus.services.owner.charge;

import com.example.solimus.dtos.owner.charge.ChargePaymentReceiptDTO;
import com.example.solimus.dtos.owner.charge.ChargePaymentResponseDTO;
import com.example.solimus.dtos.owner.charge.InitierPaiementChargeDTO;
import com.example.solimus.dtos.owner.charge.*;
import com.example.solimus.enums.ChargeType;

public interface OwnerChargeService {

    /**
     * Liste paginée des charges du copropriétaire connecté (courantes + exceptionnelles mélangées),
     * avec recherche, filtre par type, statut et résidence — tous optionnels.
     */
    MyChargeListResponse getMyCharges(String search, ChargeType type, String status, Long residenceId, int page, int size);

    /**
     * Détail complet d'une charge précise (courante ou exceptionnelle selon le type fourni).
     */
    MyChargeDetailDTO getChargeDetail(ChargeType type, Long id);

    /**
     * Initie le paiement d'une charge (courante ou exceptionnelle) via TouchPay.
     */
    ChargePaymentResponseDTO initierPaiement(ChargeType type, Long id, InitierPaiementChargeDTO dto);

    /**
     * Récupère le reçu d'un paiement de charge, à partir de sa référence de transaction.
     */
    ChargePaymentReceiptDTO getReceipt(String transactionRef);
}
