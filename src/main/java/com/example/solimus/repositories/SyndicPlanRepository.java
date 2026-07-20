package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SyndicPlanRepository extends JpaRepository<SyndicPlan, Long> {

    // Toutes les formules actives, pour l'affichage public/liste principale
    List<SyndicPlan> findByActiveTrue();
}