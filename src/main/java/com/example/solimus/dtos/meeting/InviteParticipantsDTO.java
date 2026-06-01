package com.example.solimus.dtos.meeting;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO pour inviter des copropriétaires à une réunion.
 * Contient la liste des IDs des utilisateurs à inviter.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InviteParticipantsDTO {

    @NotEmpty(message = "La liste des participants est obligatoire")
    private List<Long> participantIds;
}
