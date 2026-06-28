package com.example.solimus.repositories;

import com.example.solimus.entities.Subscription;
import com.example.solimus.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    // Récupère le dernier abonnement d'un prestataire (le plus récent selon endDate)
    // → utilisé avant de créer un nouveau paiement, pour vérifier qu'il n'a pas déjà un abonnement actif
    Optional<Subscription> findFirstByProviderIdOrderByEndDateDesc(Long providerId);

    // Récupère l'abonnement correspondant à une référence de transaction TouchPay (SUB-xxx)
    // → utilisé par le bridge et le callback pour retrouver la bonne ligne
    Optional<Subscription> findByTransactionRef(String transactionRef);

    // Récupère tous les abonnements ACTIVE dont la date de fin est dépassée
    // → utilisé par le scheduler horaire pour les faire passer en EXPIRED
    List<Subscription> findByStatusAndEndDateBefore(SubscriptionStatus status, LocalDateTime dateTime);

    // Récupère tous les paiements PENDING créés avant un certain seuil de temps
    // → utilisé par le scheduler chaque minute pour les faire passer en FAILED après 5 min
    List<Subscription> findByStatusAndCreatedAtBefore(SubscriptionStatus status, LocalDateTime dateTime);
}