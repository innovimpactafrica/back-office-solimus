package com.example.solimus.repositories;

import com.example.solimus.entities.SubscriptionPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SubscriptionPaymentRepository extends JpaRepository<SubscriptionPayment, Long> {
    List<SubscriptionPayment> findBySubscriptionIdOrderByCreatedAtDesc(Long subscriptionId);
}
