package com.example.solimus.dtos.document;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoOwnerDocumentDTO {

    private Long id;
    private String fileName;
    private String fileUrl;
    private Long fileSizeKb;
    private String documentType;

    @JsonFormat(pattern = "dd/MM/yyyy")
    private LocalDate date;

    private String source;  // "MEETING" ou "CHARGE"
    private Long sourceId;  // ID réunion ou charge
}
