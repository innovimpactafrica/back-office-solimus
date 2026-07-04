package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SyndicWalletRepository extends JpaRepository<SyndicWallet, Long> {

    // Récupérer le wallet d'un syndic (un seul wallet par syndic)
    Optional<SyndicWallet> findBySyndicId(Long syndicId);
}
