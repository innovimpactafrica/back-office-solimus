package com.example.solimus.dtos.owner.document;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDocumentDTO {

    private String sourceType;    // "MEETING" ou "EXCEPTIONAL_CALL"
    private Long sourceId;        // id du MeetingDocument ou ExceptionalCallDocument

    private String fileName;
    private String fileUrl;
    private Long fileSizeKb;

    private String category;      // libellé affiché (ex: "PV", "Charges")
    private LocalDateTime createdAt;
}
