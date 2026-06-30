package com.example.solimus.services.provider.wallet;

import com.example.solimus.dtos.provider.wallet.RequestWithdrawalDTO;
import com.example.solimus.dtos.provider.wallet.WithdrawalRequestDTO;
import com.example.solimus.dtos.provider.wallet.WalletDTO;

import java.math.BigDecimal;

public interface WalletService {

    /**
     * Retourne le wallet d'un prestataire avec les transactions effectuées.
     */
     WalletDTO getMyWallet(int page, int size);

     /**
      * Crédite le wallet d'un prestataire après validation d'un paiement.
      */
     void creditWallet(Long providerId, BigDecimal amount);

     /**
      * Demande un versement (retrait) pour le prestataire connecté.
      */
     WithdrawalRequestDTO requestWithdrawal(RequestWithdrawalDTO dto);

}
