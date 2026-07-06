package com.example.solimus.dtos.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionalCallDocumentDTO {

    private Long id;
    private String fileName;
    private String fileUrl;
    private Long fileSizeKb;
    private LocalDateTime createdAt;
}
