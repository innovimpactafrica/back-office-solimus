# Paramètre syndic: Pagination de tous les endpoints retournant une liste 
# Résidence : une seule création
# A relire : Il y a un FileController dans coopachat qui sert de proxy :

# Ajouter sur ajout meeting 
// Upload de chaque document fourni et rattachement à la réunion.
// Tous les documents ajoutés à ce stade sont typés CONVOCATION par défaut —
// les autres types (PV, rapport financier...) seront ajoutés plus tard, séparément.
if (documents != null) {
for (MultipartFile file : documents) {

        // Verifie que le fichier n'est pas vide
        if (file.isEmpty()) {
            throw new BadRequestException("Un des fichiers fournis est vide");
        }

        // Verifie la taille maximale : 20 Mo
        long maxSizeBytes = 20L * 1024 * 1024; // 20 Mo en octets
        if (file.getSize() > maxSizeBytes) {
            throw new BadRequestException("Le fichier " + file.getOriginalFilename() + " dépasse la taille maximale autorisée (20 Mo)");
        }

        // Verifie l'extension autorisee : PDF, DOCX, XLSX uniquement
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || !hasAllowedExtension(originalFileName)) {
            throw new BadRequestException("Format de fichier non autorisé pour " + originalFileName + ". Formats acceptés : PDF, DOCX, XLSX");
        }

        String fileUrl = minioService.uploadFile(file, "meetings");

        MeetingDocument doc = new MeetingDocument();
        doc.setMeeting(savedMeeting);
        doc.setFileName(originalFileName);
        doc.setFileUrl(fileUrl);
        doc.setFileSizeKb(file.getSize() / 1024);
        doc.setDocumentType(MeetingDocumentType.CONVOCATION);

        savedMeeting.getDocuments().add(doc);
    }
}

#