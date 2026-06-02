package com.example.solimus.dtos.intervention;

import com.example.solimus.enums.InitiatedBy;
import com.example.solimus.enums.IncidentLocationType;
import com.example.solimus.enums.InterventionManagementMode;
import com.example.solimus.enums.InterventionStatus;
import com.example.solimus.enums.UrgencyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterventionRequestDTO {
    private Long id;
    private String title;
    private String description;
    private InterventionStatus status;
    private InitiatedBy initiatedBy;
    private IncidentLocationType locationType;
    private InterventionManagementMode managementMode;
    private UrgencyLevel urgencyLevel;

    private String residenceName;

    // Contact Résident (pour le prestataire)
    private String residentPhone;
    private String residentEmail;

    private List<String> photoUrls;
    private List<String> workPhotoUrls;
    private List<InterventionCommentDTO> comments;
    private List<InterventionStatusHistoryDTO> history;
    private List<WorkflowStepDTO> workflowSteps;
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime createdAt;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime startedAt;

    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "dd/MM/yyyy HH:mm")
    private LocalDateTime finishedAt;
}
