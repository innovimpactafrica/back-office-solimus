package com.example.solimus.repositories;

import com.example.solimus.entities.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // Récupérer le wallet du prestataire
    Optional<Wallet> findByProviderId(Long providerId);
}
