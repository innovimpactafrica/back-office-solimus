package com.example.solimus.repositories;

import com.example.solimus.entities.ProviderPlan;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderPlanRepository extends JpaRepository<ProviderPlan, Long> {

    // Vérifie l'unicité du nom à la création
    boolean existsByNameIgnoreCase(String name);

    // Vérifie l'unicité du nom à la modification, en excluant la formule elle-même
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}