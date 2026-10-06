package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWallet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface SyndicWalletRepository extends JpaRepository<SyndicWallet, Long> {

    // Récupérer le wallet d'un syndic (un seul wallet par syndic)
    Optional<SyndicWallet> findBySyndicId(Long syndicId);

    // Verrouille la ligne du wallet le temps de la transaction (validation d'un retrait) — empêche
    // deux validations concurrentes sur le même wallet de lire le même solde avant que l'une des
    // deux ait fini. Timeout court (3s) pour échouer vite avec un message clair plutôt que de
    // bloquer l'admin longtemps (voir WithdrawalRequestServiceImpl.validateWithdrawalRequest)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT w FROM SyndicWallet w WHERE w.id = :walletId")
    Optional<SyndicWallet> findByIdForUpdate(@Param("walletId") Long walletId);
}
