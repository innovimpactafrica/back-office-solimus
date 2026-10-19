package com.example.solimus.repositories;

import com.example.solimus.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Historique complet d'une conversation (ordre chronologique ascendant)
    List<Message> findByConversationIdOrderBySentAtAsc(Long conversationId);

    // Historique paginé d'une conversation, du plus récent au plus ancien
    Page<Message> findByConversationIdOrderBySentAtDesc(Long conversationId, Pageable pageable);

    // Tout dernier message d'une conversation (aperçu « lastMessagePreview »)
    Optional<Message> findFirstByConversationIdOrderBySentAtDesc(Long conversationId);

    // Nombre de messages NON lus dans une conversation pour un destinataire donné
    // (ceux qu'il n'a pas envoyés et qui n'ont pas encore de date de lecture)
    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    // Idempotence d'envoi : existe-t-il déjà ce message pour cet expéditeur dans cette conversation ?
    boolean existsByConversationIdAndSenderIdAndClientMessageId(Long conversationId, Long senderId, String clientMessageId);

    Optional<Message> findFirstByConversationIdAndSenderIdAndClientMessageId(Long conversationId, Long senderId, String clientMessageId);

    // Les messages non lus d'une conversation envoyés par un expéditeur précis (à marquer comme lus)
    List<Message> findByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    // Nombre TOTAL de messages non lus de l'utilisateur, toutes conversations confondues
    // (messages reçus — pas envoyés par lui — sans date de lecture), même principe que
    // NotificationRepository.countByUserAndReadFalse
    @Query("SELECT COUNT(m) FROM Message m " +
           "WHERE (m.conversation.participantA.id = :userId OR m.conversation.participantB.id = :userId) " +
           "AND m.sender.id <> :userId AND m.readAt IS NULL")
    long countUnreadForUser(@Param("userId") Long userId);

    // Marquage "lu" en masse, limité aux messages REÇUS par l'utilisateur dans cette conversation
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Message m SET m.readAt = :readAt " +
           "WHERE m.conversation.id = :conversationId AND m.sender.id <> :userId AND m.readAt IS NULL")
    int markReceivedAsRead(@Param("conversationId") Long conversationId,
                           @Param("userId") Long userId,
                           @Param("readAt") LocalDateTime readAt);
}
