package com.example.solimus.repositories;

import com.example.solimus.entities.SyndicSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyndicSubscriptionRepository extends JpaRepository<SyndicSubscription, Long> {

    // Compte les abonnés actuels sur une formule précise
    long countBySyndicPlanId(Long syndicPlanId);
}