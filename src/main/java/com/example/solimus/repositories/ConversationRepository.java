package com.example.solimus.repositories;

import com.example.solimus.entities.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // Toutes les conversations de l'utilisateur (participant A ou B), triées par dernière activité
    List<Conversation> findByParticipantAIdOrParticipantBIdOrderByLastMessageAtDesc(Long participantAId, Long participantBId);

    // Recherche la conversation canonique entre deux utilisateurs (participantA.id < participantB.id)
    // Version paginée (format Page du projet)
    Page<Conversation> findByParticipantAIdOrParticipantBId(Long participantAId, Long participantBId, Pageable pageable);

    Optional<Conversation> findByParticipantAIdAndParticipantBId(Long participantAId, Long participantBId);

    boolean existsByParticipantAIdAndParticipantBId(Long participantAId, Long participantBId);
}
