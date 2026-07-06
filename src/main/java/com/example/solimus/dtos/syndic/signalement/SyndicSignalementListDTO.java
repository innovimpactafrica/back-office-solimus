package com.example.solimus.dtos.syndic.signalement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyndicSignalementListDTO {

    private long totalSignalements;
    private long enAttenteCount;
    private long enTravauxCount;
    private long traiteCount;

    private Page<SyndicSignalementCardDTO> signalements;
}
