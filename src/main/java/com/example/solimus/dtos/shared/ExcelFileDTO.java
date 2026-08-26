package com.example.solimus.dtos.shared;

import lombok.AllArgsConstructor;
import lombok.Data;

// Résultat d'un export Excel — nom de fichier dynamique (reflète les filtres appliqués) + contenu binaire .xlsx
@Data
@AllArgsConstructor
public class ExcelFileDTO {
    private String fileName;
    private byte[] content;
}
