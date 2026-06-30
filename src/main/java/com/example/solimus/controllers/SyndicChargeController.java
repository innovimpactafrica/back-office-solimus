package com.example.solimus.controllers;

import com.example.solimus.dtos.charge.ChargeResponseDTO;
import com.example.solimus.dtos.charge.ChargeDocumentDTO;
import com.example.solimus.dtos.charge.CreateChargeDTO;
import com.example.solimus.dtos.charge.CreateChargeLineDTO;
import com.example.solimus.enums.ChargeType;
import com.example.solimus.services.minio.MinioService;
import com.example.solimus.services.syndic.SyndicService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/syndic")
@RequiredArgsConstructor
@Tag(name = "Syndic - Charges", description = "Gestion des charges de copropriété par le syndic")
public class SyndicChargeController {

    private final SyndicService syndicService;
    private final MinioService minioService;

    @Operation(summary = "Créer une charge pour une résidence (Avec upload Minio)", tags = {"4.e Syndic - Charges"})
    @PostMapping(value = "/charges", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createCharge(
            @Parameter(description = "Titre de la charge")
            @RequestPart("title") String title,

            @Parameter(description = "Description de la charge")
            @RequestPart(value = "description", required = false) String description,

            @Parameter(description = "Type de charge (CHARGES_COURANTES, CHARGES_SPECIALES)")
            @RequestPart("type") String type,

            @Parameter(description = "Montant total de la charge")
            @RequestPart("totalAmount") String totalAmount,

            @Parameter(description = "Période (ex: Juin 2026)")
            @RequestPart(value = "period", required = false) String period,

            @Parameter(description = "Date d'échéance (format: yyyy-MM-dd)")
            @RequestPart(value = "dueDate", required = false) String dueDate,

            @Parameter(description = "ID de la résidence")
            @RequestPart("residenceId") String residenceId,

            @Parameter(description = "Lignes de répartition (JSON)")
            @RequestPart(value = "lines", required = false) String lines,

            @Parameter(description = "Documents joints (PDF, JPG, PNG)")
            @RequestPart(value = "documents", required = false) MultipartFile[] documents) {

        try {
            CreateChargeDTO dto = new CreateChargeDTO();
            dto.setTitle(title);
            dto.setDescription(description);
            dto.setType(ChargeType.valueOf(type));
            dto.setTotalAmount(new BigDecimal(totalAmount));
            dto.setPeriod(period);
            if (dueDate != null && !dueDate.isEmpty()) {
                dto.setDueDate(LocalDate.parse(dueDate));
            }
            dto.setResidenceId(Long.parseLong(residenceId));

            if (lines != null && !lines.isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.findAndRegisterModules();

                String cleanedLines = lines.trim();
                if (cleanedLines.startsWith("\"lines\":")) {
                    cleanedLines = cleanedLines.substring(8).trim();
                } else if (cleanedLines.startsWith("lines:")) {
                    cleanedLines = cleanedLines.substring(6).trim();
                }

                List<CreateChargeLineDTO> lineList = mapper.readValue(cleanedLines,
                        new TypeReference<List<CreateChargeLineDTO>>() {});
                dto.setLines(lineList);
            }

            List<ChargeDocumentDTO> documentDTOs = new ArrayList<>();
            if (documents != null && documents.length > 0) {
                for (MultipartFile document : documents) {
                    if (document.isEmpty()) continue;

                    String originalFilename = document.getOriginalFilename();
                    if (originalFilename != null) {
                        String extension = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
                        if (!extension.equals("pdf") && !extension.equals("jpg") && !extension.equals("jpeg") && !extension.equals("png")) {
                            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                    .body("Format de document non supporté. Formats acceptés: PDF, JPG, PNG");
                        }
                    }

                    String uploadedFileName = minioService.uploadFile(document, "charges");
                    if (uploadedFileName != null) {
                        documentDTOs.add(ChargeDocumentDTO.builder()
                                .fileName(uploadedFileName.substring(uploadedFileName.lastIndexOf("/") + 1))
                                .originalFileName(originalFilename)
                                .fileUrl(uploadedFileName)
                                .fileSizeKb(document.getSize() / 1024)
                                .contentType(document.getContentType())
                                .build());
                    }
                }
            }
            dto.setDocuments(documentDTOs);

            return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(syndicService.createCharge(dto));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur lors de la création de la charge : " + e.getMessage());
        }
    }

    @Operation(summary = "Lister les charges d'une résidence", tags = {"4.e Syndic - Charges"})
    @GetMapping("/charges/residence/{residenceId}")
    public ResponseEntity<List<ChargeResponseDTO>> getChargesByResidence(
            @PathVariable Long residenceId) {
        return ResponseEntity.ok(
            syndicService.getChargesByResidence(residenceId));
    }

    @Operation(summary = "Supprimer une charge", tags = {"4.e Syndic - Charges"})
    @DeleteMapping("/charges/{chargeId}")
    public ResponseEntity<String> deleteCharge(@PathVariable Long chargeId) {
        syndicService.deleteCharge(chargeId);
        return ResponseEntity.ok("Charge supprimée avec succès");
    }
}
