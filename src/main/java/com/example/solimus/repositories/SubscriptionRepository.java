package com.example.solimus.repositories;

import com.example.solimus.entities.Subscription;
import com.example.solimus.enums.SubscriptionPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findByProviderId(Long providerId);
    List<Subscription> findByPlanAndDateExpirationAndRenouvellementAutoTrue(SubscriptionPlan plan, LocalDate dateExpiration);
    List<Subscription> findByPlanAndDateExpirationAndRenouvellementAutoFalse(SubscriptionPlan plan, LocalDate dateExpiration);
}
