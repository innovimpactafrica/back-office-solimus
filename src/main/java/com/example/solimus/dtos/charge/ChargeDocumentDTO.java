package com.example.solimus.dtos.charge;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargeDocumentDTO {
    private Long id;
    private String fileName;
    private String originalFileName;
    private String fileUrl;
    private Long fileSizeKb;
    private String contentType;
}
