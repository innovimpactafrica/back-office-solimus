package com.example.solimus.dtos.shared;

import lombok.AllArgsConstructor;
import lombok.Data;

// Résultat d'un export PDF — nom de fichier dynamique (reflète les filtres appliqués) + contenu binaire .pdf
// Miroir de ExcelFileDTO, gardé séparé pour ne pas mélanger les deux formats dans un même type.
@Data
@AllArgsConstructor
public class PdfFileDTO {
    private String fileName;
    private byte[] content;
}
