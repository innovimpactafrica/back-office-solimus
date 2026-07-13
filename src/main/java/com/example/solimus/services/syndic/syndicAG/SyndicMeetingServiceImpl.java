package com.example.solimus.services.syndic.syndicAG;

import com.example.solimus.dtos.meeting.MeetingDetailDTO;
import com.example.solimus.dtos.syndic.meeting.CreateMeetingDTO;
import com.example.solimus.entities.ActivityLog;
import com.example.solimus.entities.Meeting;
import com.example.solimus.entities.MeetingAgendaItem;
import com.example.solimus.entities.MeetingDocument;
import com.example.solimus.entities.Residence;
import com.example.solimus.entities.User;
import com.example.solimus.enums.ActivityType;
import com.example.solimus.enums.MeetingDocumentType;
import com.example.solimus.enums.MeetingStatus;
import com.example.solimus.enums.MeetingType;
import com.example.solimus.exceptions.ForbiddenException;
import com.example.solimus.exceptions.ResourceNotFoundException;
import com.example.solimus.repositories.ActivityLogRepository;
import com.example.solimus.repositories.MeetingRepository;
import com.example.solimus.repositories.ResidenceRepository;
import com.example.solimus.repositories.UserRepository;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SyndicMeetingServiceImpl implements SyndicMeetingService {

    private final MeetingRepository meetingRepository;
    private final ResidenceRepository residenceRepository;
    private final UserRepository userRepository;
    private final MinioService minioService;
    private final ActivityLogRepository activityLogRepository;

    @Override
    @Transactional
    public MeetingDetailDTO createMeeting(CreateMeetingDTO dto, List<MultipartFile> documents) {

        // Récupère le syndic actuellement connecté
        User currentSyndic = getCurrentUser();

        // Récupère la résidence, erreur si introuvable
        Residence residence = residenceRepository.findById(dto.getResidenceId())
                .orElseThrow(() -> new ResourceNotFoundException("Résidence introuvable"));

        // Vérifie que cette résidence appartient bien au syndic connecté
        if (!residence.getSyndic().getId().equals(currentSyndic.getId())) {
            throw new ForbiddenException("Vous n'êtes pas autorisé à créer une réunion pour cette résidence");
        }

        // Construit l'entité Meeting à partir des champs reçus
        Meeting meeting = new Meeting();
        meeting.setResidence(residence);
        meeting.setSyndic(currentSyndic);
        meeting.setTitle(dto.getTitle());
        meeting.setType(dto.getType() != null ? dto.getType() : MeetingType.ORDINARY);
        meeting.setMeetingDate(dto.getMeetingDate());
        meeting.setStartTime(dto.getStartTime());
        meeting.setEndTime(dto.getEndTime());
        meeting.setLocation(dto.getLocation());
        meeting.setConvocationSentDate(dto.getConvocationSentDate());
        meeting.setConvocationMessage(dto.getConvocationMessage());
        meeting.setSendByEmail(dto.getSendByEmail() != null ? dto.getSendByEmail() : false);
        meeting.setSendByPlatformNotification(dto.getSendByPlatformNotification() != null ? dto.getSendByPlatformNotification() : false);
        meeting.setSendBySms(dto.getSendBySms() != null ? dto.getSendBySms() : false);

        // Construit l'ordre du jour à partir de la liste de titres, en respectant l'ordre fourni
        List<MeetingAgendaItem> agendaItems = new ArrayList<>();
        if (dto.getAgendaItems() != null) {
            int index = 0;
            for (String itemTitle : dto.getAgendaItems()) {
                MeetingAgendaItem item = new MeetingAgendaItem();
                item.setMeeting(meeting);
                item.setOrderIndex(index++);
                item.setTitle(itemTitle);
                agendaItems.add(item);
            }
        }
        meeting.setAgendaItems(agendaItems);

        // Sauvegarde initiale pour obtenir l'ID généré par la base,
        // nécessaire avant de pouvoir attacher les documents (relation ManyToOne vers meeting)
        Meeting savedMeeting = meetingRepository.save(meeting);

        // Upload de chaque document fourni et rattachement à la réunion.
        // Tous les documents ajoutés à ce stade sont typés CONVOCATION par défaut —
        // les autres types (PV, rapport financier...) seront ajoutés plus tard, séparément.
        if (documents != null) {
            for (MultipartFile file : documents) {
                String fileUrl = minioService.uploadFile(file, "meetings");

                MeetingDocument doc = new MeetingDocument();
                doc.setMeeting(savedMeeting);
                doc.setFileName(file.getOriginalFilename());
                doc.setFileUrl(fileUrl);
                doc.setFileSizeKb(file.getSize() / 1024);
                doc.setDocumentType(MeetingDocumentType.CONVOCATION);

                savedMeeting.getDocuments().add(doc);
            }
        }

        // Statut décidé en dernier, selon le choix "Publier" ou "Brouillon" du syndic —
        // une fois que tout (agenda, documents) est bien construit et prêt
        savedMeeting.setStatus(Boolean.TRUE.equals(dto.getPublish()) ? MeetingStatus.UPCOMING : MeetingStatus.DRAFT);

        // Sauvegarde finale avec tout (agenda, documents, statut définitif)
        meetingRepository.save(savedMeeting);

        // Tracer l'activité dans le journal
        ActivityLog activityLog = ActivityLog.builder()
                .residence(residence)
                .type(ActivityType.MEETING_CREATED)
                .relatedEntityType("MEETING")
                .relatedEntityId(savedMeeting.getId())
                .actor(currentSyndic)
                .message("Nouvelle assemblée générale créée")
                .detail(savedMeeting.getTitle())
                .build();
        activityLogRepository.save(activityLog);

        // Construit et retourne le DTO de détail complet
        return buildMeetingDetailDTO(savedMeeting);
    }

    private User getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Syndic non trouvé"));
    }

    private MeetingDetailDTO buildMeetingDetailDTO(Meeting meeting) {
        // TODO: Implémenter la construction du DTO
        return null;
    }
}
