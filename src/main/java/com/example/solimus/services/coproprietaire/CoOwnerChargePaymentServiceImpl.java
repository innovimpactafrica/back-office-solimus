package com.example.solimus.services.coproprietaire;

import com.example.solimus.dtos.charge.ChargePaymentReceiptDTO;
import com.example.solimus.dtos.charge.ChargePaymentResponseDTO;
import com.example.solimus.dtos.charge.InitierPaiementChargeDTO;
import com.example.solimus.entities.ChargeAllocation;
import com.example.solimus.entities.ChargePayment;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ChargeStatus;
import com.example.solimus.enums.PaymentStatus;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ChargeAllocationRepository;
import com.example.solimus.repositories.ChargePaymentRepository;
import com.example.solimus.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Slf4j
public class CoOwnerChargePaymentServiceImpl implements CoOwnerChargePaymentService {

    private final ChargeAllocationRepository allocationRepository;
    private final ChargePaymentRepository chargePaymentRepository;
    private final UserRepository userRepository;

    @Value("${app.touchpay.bridge-url}")
    private String touchPayBridgeUrlTemplate;

    @Override
    @Transactional
    public ChargePaymentResponseDTO initierPaiement(
            Long allocationId,
            InitierPaiementChargeDTO dto) {

        User currentOwner = getCurrentUser();

        // 1. Récupérer l'allocation
        ChargeAllocation allocation = allocationRepository
            .findById(allocationId)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Charge introuvable"));

        // 2. Vérifier que c'est bien son allocation
        if (!allocation.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException(
                "Vous n'avez pas accès à cette charge");
        }

        // 3. Vérifier que la charge n'est pas déjà payée
        if (allocation.getStatus() == ChargeStatus.PAYEE) {
            throw new BadRequestException(
                "Cette charge est déjà payée");
        }

        // 4. Vérifier qu'aucun paiement PENDING n'existe déjà
        boolean paiementEnCours = chargePaymentRepository
            .existsByAllocationIdAndStatus(
                allocationId, PaymentStatus.PENDING);

        if (paiementEnCours) {
            throw new BadRequestException(
                "Un paiement est déjà en cours pour cette charge");
        }

        // 5. Générer la référence unique
        String transactionRef = genererReference("CPY");

        // 6. Créer le paiement en PENDING
        ChargePayment paiement = new ChargePayment();
        paiement.setReference(transactionRef);
        paiement.setAllocation(allocation);
        paiement.setOwner(currentOwner);
        paiement.setAmount(allocation.getAmount());
        paiement.setMethod(dto.getMethod());
        paiement.setStatus(PaymentStatus.PENDING);

        chargePaymentRepository.save(paiement);

        // 7. Construire l'URL bridge
        String bridgeUrl = String.format(
            touchPayBridgeUrlTemplate, transactionRef);

        log.info("Paiement charge {} initié — ref: {}",
            allocationId, transactionRef);

        return ChargePaymentResponseDTO.builder()
            .success(true)
            .message("Paiement initié. Veuillez compléter via TouchPay.")
            .transactionReference(transactionRef)
            .amount(allocation.getAmount())
            .paymentUrl(bridgeUrl)
            .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ChargePaymentReceiptDTO getReceipt(String transactionRef) {

        ChargePayment paiement = chargePaymentRepository
            .findByReference(transactionRef)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Paiement introuvable"));

        // Vérifier que c'est bien son paiement
        User currentOwner = getCurrentUser();
        if (!paiement.getOwner().getId().equals(currentOwner.getId())) {
            throw new ForbiddenException("Accès non autorisé");
        }

        return ChargePaymentReceiptDTO.builder()
            .reference(paiement.getReference())
            .chargeTitle(paiement.getAllocation().getCharge().getTitle())
            .amount(paiement.getAmount())
            .method(paiement.getMethod())
            .paidAt(paiement.getPaidAt())
            .status(paiement.getAllocation().getStatus())
            .build();
    }

    private String genererReference(String prefix) {
        return prefix + "-"
            + (int)(Math.random() * 900000 + 100000);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext()
            .getAuthentication().getName();
        return userRepository.findByEmail(email)
            .orElseThrow(() -> new ResourceNotFoundException(
                "Utilisateur introuvable"));
    }
}
