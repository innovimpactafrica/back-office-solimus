package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<ProviderWallet, Long> {

    // Récupérer le wallet du prestataire
    Optional<ProviderWallet> findByProviderId(Long providerId);
}
