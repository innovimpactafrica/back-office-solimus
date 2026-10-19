package com.example.solimus.repositories;

import com.example.solimus.entities.MobileMoneyTransaction;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MobileMoneyTransactionRepository extends JpaRepository<MobileMoneyTransaction, Long> {

    Optional<MobileMoneyTransaction> findByReference(String reference);

    // Verrou par référence : callback, retour, confirmation et réconciliation ne peuvent pas
    // appliquer deux fois le même paiement (ex: double crédit du wallet)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT t FROM MobileMoneyTransaction t WHERE t.reference = :reference")
    Optional<MobileMoneyTransaction> findByReferenceForUpdate(@Param("reference") String reference);

    // Transactions encore en cours, récentes — à réconcilier avec le statut GU
    List<MobileMoneyTransaction> findByFinalizedAtIsNullAndCreatedAtAfter(LocalDateTime since);
}
