package com.example.solimus.repositories;

import com.example.solimus.entities.Charge;
import com.example.solimus.entities.ChargeLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository pour l'entité ChargeLine.
 */
@Repository
public interface ChargeLineRepository extends JpaRepository<ChargeLine, Long> {

    /**
     * Trouve toutes les lignes d'une charge donnée.
     * 
     * @param charge la charge parente
     * @return la liste des lignes de cette charge
     */
    List<ChargeLine> findByCharge(Charge charge);
}
