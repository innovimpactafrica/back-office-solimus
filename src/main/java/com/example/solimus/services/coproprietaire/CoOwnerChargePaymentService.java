package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.charge.ChargePaymentReceiptDTO;
import com.example.solimus.dtos.charge.ChargePaymentResponseDTO;
import com.example.solimus.dtos.charge.InitierPaiementChargeDTO;

public interface CoOwnerChargePaymentService {

    // Initier le paiement d'une charge
    ChargePaymentResponseDTO initierPaiement(
        Long allocationId,
        InitierPaiementChargeDTO dto
    );

    // Récupérer le reçu d'un paiement
    ChargePaymentReceiptDTO getReceipt(String transactionRef);
}
