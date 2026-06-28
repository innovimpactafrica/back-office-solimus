package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderPlanRepository extends JpaRepository<ProviderPlan, Long> {

    /**
     * Récupère l'unique ligne de configuration de la formule prestataire.
     * Comme il ne doit exister qu'une seule formule, on prend simplement
     * la première trouvée (peu importe son id).
     */
    Optional<ProviderPlan> findFirstByOrderByIdAsc();
}