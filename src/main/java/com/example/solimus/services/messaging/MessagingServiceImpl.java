package com.example.solimus.services.messaging;

import com.example.solimus.dtos.messaging.ConversationDTO;
import com.example.solimus.dtos.messaging.ConversationMessagesDTO;
import com.example.solimus.dtos.messaging.MessageDTO;
import com.example.solimus.dtos.messaging.SendMessageDTO;
import com.example.solimus.entities.Conversation;
import com.example.solimus.entities.InterventionRequest;
import com.example.solimus.entities.Signalement;
import com.example.solimus.enums.ERole;
import com.example.solimus.entities.Message;
import com.example.solimus.entities.User;
import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ConversationRepository;
import com.example.solimus.repositories.InterventionRequestRepository;
import com.example.solimus.repositories.PropertyRepository;
import com.example.solimus.repositories.SignalementRepository;
import com.example.solimus.repositories.SyndicCoOwnerRelationRepository;
import com.example.solimus.repositories.MessageRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MessagingServiceImpl implements MessagingService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PropertyRepository propertyRepository;
    private final SyndicCoOwnerRelationRepository syndicCoOwnerRelationRepository;
    private final SignalementRepository signalementRepository;
    private final InterventionRequestRepository interventionRequestRepository;

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------
    @Override
    @Transactional(readOnly = true)
    public Page<ConversationDTO> listConversations(int page, int size) {
        User me = getCurrentUser();
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.min(Math.max(1, size), 100),
                Sort.by(Sort.Direction.DESC, "lastMessageAt"));
        return conversationRepository
                .findByParticipantAIdOrParticipantBId(me.getId(), me.getId(), pageable)
                .map(c -> toConversationDTO(c, me));
    }

    @Override
    @Transactional(readOnly = true)
    public long countUnreadMessages() {
        return messageRepository.countUnreadForUser(getCurrentUser().getId());
    }

    @Override
    @Transactional
    public ConversationDTO getOrCreateConversation(Long otherUserId, Long signalementId, Long interventionRequestId) {
        User me = getCurrentUser();
        if (otherUserId == null || otherUserId.equals(me.getId())) {
            throw new BadRequestException("Impossible d'ouvrir une conversation avec vous-même");
        }
        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));

        // Cloisonnement : syndic ↔ copropriétaire/locataire de SES résidences uniquement
        User syndic = assertCanConverse(me, other);

        // Contexte métier facultatif, vérifié contre les résidences du syndic de la paire
        Signalement signalement = null;
        InterventionRequest intervention = null;
        if (signalementId != null) {
            signalement = signalementRepository.findById(signalementId)
                    .orElseThrow(() -> new ResourceNotFoundException("Signalement introuvable"));
            if (signalement.getResidence() == null || signalement.getResidence().getSyndic() == null
                    || !signalement.getResidence().getSyndic().getId().equals(syndic.getId())) {
                throw new ForbiddenException("Ce signalement ne concerne pas une résidence de ce syndic");
            }
            // Signalement converti en travaux : la demande issue de la conversion est rattachée d'office
            if (interventionRequestId == null && signalement.getLinkedIntervention() != null) {
                intervention = signalement.getLinkedIntervention();
            }
        }
        if (interventionRequestId != null) {
            intervention = interventionRequestRepository.findById(interventionRequestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Demande de travaux introuvable"));
            if (intervention.getResidence() == null || intervention.getResidence().getSyndic() == null
                    || !intervention.getResidence().getSyndic().getId().equals(syndic.getId())) {
                throw new ForbiddenException("Cette demande de travaux ne concerne pas une résidence de ce syndic");
            }
        }

        // Ordre canonique : participantA.id < participantB.id → une seule conversation par paire
        User a = (me.getId() <= other.getId()) ? me : other;
        User b = (me.getId() <= other.getId()) ? other : me;

        Conversation conversation = conversationRepository
                .findByParticipantAIdAndParticipantBId(a.getId(), b.getId())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder().participantA(a).participantB(b).build()));

        // Un seul fil par paire (contrainte uk_conversations_participants) : le contexte transmis
        // devient le contexte courant du fil
        boolean changed = false;
        if (signalement != null) {
            conversation.setSignalement(signalement);
            changed = true;
        }
        if (intervention != null) {
            conversation.setInterventionRequest(intervention);
            changed = true;
        }
        if (changed) {
            conversation = conversationRepository.save(conversation);
        }

        return toConversationDTO(conversation, me);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationMessagesDTO getMessages(Long conversationId, int page, int size) {
        User me = getCurrentUser();
        Conversation conversation = requireParticipant(conversationId, me);

        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        Pageable pageable = PageRequest.of(safePage, safeSize);

        Page<Message> messagePage = messageRepository
                .findByConversationIdOrderBySentAtDesc(conversation.getId(), pageable);

        List<MessageDTO> messages = messagePage.getContent().stream()
                .map(m -> toMessageDTO(m, me))
                .toList();

        return ConversationMessagesDTO.builder()
                .conversation(toConversationDTO(conversation, me))
                .messages(messages)
                .page(messagePage.getNumber())
                .size(messagePage.getSize())
                .totalElements(messagePage.getTotalElements())
                .totalPages(messagePage.getTotalPages())
                .build();
    }

    @Override
    @Transactional
    public MessageDTO sendMessage(Long conversationId, SendMessageDTO dto) {
        User me = getCurrentUser();
        Conversation conversation = requireParticipant(conversationId, me);

        // Idempotence : si le client fournit un clientMessageId déjà enregistré pour cet
        // expéditeur dans cette conversation, on retourne le message existant sans en créer
        // un second (double-clic ou retry réseau n'envoie pas le message deux fois).
        String clientMessageId = (dto.getClientMessageId() != null && !dto.getClientMessageId().isBlank())
                ? dto.getClientMessageId().trim() : null;
        if (clientMessageId != null) {
            Optional<Message> existing = messageRepository.findFirstByConversationIdAndSenderIdAndClientMessageId(
                    conversation.getId(), me.getId(), clientMessageId);
            if (existing.isPresent()) {
                return toMessageDTO(existing.get(), me);
            }
        }

        Message message = Message.builder()
                .conversation(conversation)
                .sender(me)
                .content(dto.getContent().trim())
                .clientMessageId(clientMessageId)
                .build();
        message = messageRepository.save(message);

        // Mettre à jour la date du dernier message (tri de la liste des conversations)
        conversation.setLastMessageAt(message.getSentAt() != null ? message.getSentAt() : LocalDateTime.now());
        conversationRepository.save(conversation);

        // Notifier l'interlocuteur (notification en base + push) — sendPush n'élève jamais d'erreur
        User recipient = conversation.otherParticipant(me);
        if (recipient != null && recipient.getId() != null && !recipient.getId().equals(me.getId())) {
            String title = (me.getFirstName() + " " + me.getLastName()).trim();
            String content = message.getContent();
            String preview = content.length() > 120 ? content.substring(0, 117) + "..." : content;
            notificationService.sendPush(recipient.getId(), title, preview);
        }

        return toMessageDTO(message, me);
    }

    @Override
    @Transactional
    public void markConversationAsRead(Long conversationId) {
        User me = getCurrentUser();
        Conversation conversation = requireParticipant(conversationId, me);

        // Marque comme lus, en une requête, les seuls messages REÇUS (pas envoyés par moi, pas encore lus)
        messageRepository.markReceivedAsRead(conversation.getId(), me.getId(), LocalDateTime.now());
    }

    /** Charge la conversation et vérifie que le courant en est bien un participant (403 sinon). */
    private Conversation requireParticipant(Long conversationId, User me) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation introuvable"));
        if (!conversation.involves(me)) {
            throw new ForbiddenException("Vous ne faites pas partie de cette conversation");
        }
        return conversation;
    }


    // Retourne le syndic de la paire si l'échange est autorisé, ForbiddenException sinon.
    // Règle (MCO-03) : un syndic ne converse qu'avec un copropriétaire ou locataire d'une de SES
    // résidences, et inversement. Toute autre paire (prestataire, admin, deux résidents...) est refusée.
    private User assertCanConverse(User me, User other) {
        ERole myRole = me.getRole() != null ? me.getRole().getName() : null;
        ERole otherRole = other.getRole() != null ? other.getRole().getName() : null;

        User syndic = null;
        User resident = null;
        if (myRole == ERole.ROLE_SYNDIC && isResident(otherRole)) {
            syndic = me;
            resident = other;
        } else if (isResident(myRole) && otherRole == ERole.ROLE_SYNDIC) {
            syndic = other;
            resident = me;
        }

        boolean linked = syndic != null
                && (propertyRepository.existsOccupantInSyndicResidences(syndic.getId(), resident.getId())
                    || syndicCoOwnerRelationRepository.findBySyndicIdAndCoOwnerId(syndic.getId(), resident.getId()).isPresent());
        if (!linked) {
            throw new ForbiddenException(
                    "Vous ne pouvez échanger qu'entre un syndic et les copropriétaires ou locataires de ses résidences");
        }
        return syndic;
    }

    private boolean isResident(ERole role) {
        return role == ERole.ROLE_COPROPRIETAIRE || role == ERole.ROLE_LOCATAIRE;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------
    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private String roleLabel(User u) {
        if (u == null || u.getRole() == null || u.getRole().getName() == null) return null;
        return u.getRole().getName().getLabel();
    }

    private ConversationDTO toConversationDTO(Conversation c, User me) {
        User other = c.otherParticipant(me);
        long unread = messageRepository.countByConversationIdAndSenderIdNotAndReadAtIsNull(c.getId(), me.getId());

        String preview = null;
        LocalDateTime lastAt = c.getLastMessageAt();
        Optional<Message> last = messageRepository.findFirstByConversationIdOrderBySentAtDesc(c.getId());
        if (last.isPresent()) {
            preview = last.get().getContent();
            lastAt = last.get().getSentAt();
        }

        return ConversationDTO.builder()
                .id(c.getId())
                .subject(c.getSubject())
                .otherUserId(other != null ? other.getId() : null)
                .otherUserFirstName(other != null ? other.getFirstName() : null)
                .otherUserLastName(other != null ? other.getLastName() : null)
                .otherUserPhotoUrl(other != null ? other.getProfilePhotoUrl() : null)
                .otherUserRoleLabel(roleLabel(other))
                .lastMessagePreview(preview)
                .lastMessageAt(lastAt)
                .unreadCount(unread)
                .signalementId(c.getSignalement() != null ? c.getSignalement().getId() : null)
                .interventionRequestId(c.getInterventionRequest() != null ? c.getInterventionRequest().getId() : null)
                .build();
    }

    private MessageDTO toMessageDTO(Message m, User me) {
        User sender = m.getSender();
        return MessageDTO.builder()
                .id(m.getId())
                .conversationId(m.getConversation() != null ? m.getConversation().getId() : null)
                .senderId(sender != null ? sender.getId() : null)
                .senderFirstName(sender != null ? sender.getFirstName() : null)
                .senderLastName(sender != null ? sender.getLastName() : null)
                .senderPhotoUrl(sender != null ? sender.getProfilePhotoUrl() : null)
                .content(m.getContent())
                .sentAt(m.getSentAt())
                .readAt(m.getReadAt())
                .mine(sender != null && me != null && sender.getId().equals(me.getId()))
                .build();
    }
}
