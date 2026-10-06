package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderWallet;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProviderWalletRepository extends JpaRepository<ProviderWallet, Long> {

    // Récupérer le wallet du prestataire
    Optional<ProviderWallet> findByProviderId(Long providerId);

    // Verrouille la ligne du wallet (par providerId, directement dispo au moment de la validation)
    // le temps de la transaction — même raisonnement que SyndicWalletRepository.findByIdForUpdate
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    @Query("SELECT w FROM ProviderWallet w WHERE w.provider.id = :providerId")
    Optional<ProviderWallet> findByProviderIdForUpdate(@Param("providerId") Long providerId);
}
