package com.example.solimus.repositories;

import com.example.solimus.entities.ChargeCallItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChargeCallItemRepository extends JpaRepository<ChargeCallItem, Long> {

    /**
     * Lister toutes les lignes d'un appel de charges.
     */
    List<ChargeCallItem> findByChargeCallId(Long chargeCallId);

    /**
     * Lister toutes les lignes pour un copropriétaire.
     */
    List<ChargeCallItem> findByCoOwnerId(Long coOwnerId);
}
