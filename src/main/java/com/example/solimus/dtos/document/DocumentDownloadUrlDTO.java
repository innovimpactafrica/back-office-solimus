package com.example.solimus.dtos.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDownloadUrlDTO {
    private String downloadUrl;
    private Long expiresInSeconds;
}
