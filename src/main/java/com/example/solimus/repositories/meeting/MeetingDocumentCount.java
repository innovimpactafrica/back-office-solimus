package com.example.solimus.repositories.meeting;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

// ===== PROJECTION NOMBRE DE DOCUMENTS PAR REUNION =====
// Resultat d'une requete agregee groupee par reunion (constructor expression JPQL)
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class MeetingDocumentCount {

    private Long meetingId;      // id de la reunion concernee
    private long documentCount;  // nombre de documents lies a cette reunion
}
