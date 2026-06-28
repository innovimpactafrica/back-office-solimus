package com.example.solimus.repositories;

import com.example.solimus.entities.Quote;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.User;
import com.example.solimus.enums.QuoteStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

@Repository
public interface QuoteRepository extends JpaRepository<Quote, Long> {
    
    // Lister les devis pour une demande spécifique, triés par prix croissant (du moins cher au plus cher)
    List<Quote> findAllByInterventionRequestOrderByTotalAmountAsc(InterventionRequest request);


    // Vérifier si un prestataire a déjà soumis un devis pour une demande donnée
    boolean existsByInterventionRequestAndProvider(InterventionRequest request, User provider);


    // Récupérer le premier devis reçu pour une intervention (le plus ancien chronologiquement)
    Optional<Quote> findFirstByInterventionRequestOrderByCreatedAtAsc(InterventionRequest request);

    // Récupérer le devis d'un prestataire pour une demande donnée
    Optional <Quote> findByInterventionRequestAndProvider(InterventionRequest request, User provider);

    // Requête pour filtrer et rechercher les devis du prestataire
    @Query("SELECT q FROM Quote q WHERE q.provider.id = :providerId " +
           "AND (:status IS NULL OR q.status = :status) " +
           "AND (:search IS NULL OR LOWER(q.reference) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(q.interventionRequest.title) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Quote> findMyQuotes(@Param("providerId") Long providerId, 
                             @Param("status") QuoteStatus status,
                             @Param("search") String search, 
                             Pageable pageable);

    // Calculer le montant total validé (listage devis)
    @Query("SELECT SUM(q.totalAmount) FROM Quote q WHERE q.provider.id = :providerId AND q.status = :status")
    BigDecimal sumTotalAmountByProviderAndStatus(@Param("providerId") Long providerId, @Param("status") QuoteStatus status);


}
