package com.example.solimus.services.shared;

import com.example.solimus.exceptions.BadRequestException;
import com.example.solimus.services.minio.MinioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/**
 * Contrôle et dépôt (MinIO) du justificatif d'un paiement hors plateforme.
 * Le type est vérifié sur le CONTENU du fichier (signature), pas seulement sur l'extension déclarée.
 */
@Service
@RequiredArgsConstructor
public class PaymentProofDocumentService {

    public static final long MAX_SIZE_BYTES = 5L * 1024 * 1024; // 5 Mo

    private static final byte[] PDF = {'%', 'P', 'D', 'F'};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    private final MinioService minioService;

    /** Justificatif déposé : clé MinIO, nom d'origine et type MIME réel. */
    public record StoredDocument(String key, String originalName, String contentType) {
    }

    public StoredDocument store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("La preuve de paiement (fichier) est obligatoire");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BadRequestException("Fichier trop volumineux (5 Mo maximum)");
        }
        String contentType = detectContentType(file);
        if (contentType == null) {
            throw new BadRequestException("Format non supporté : la preuve doit être un PDF, un JPG ou un PNG");
        }
        String key = minioService.uploadFile(file, "payment-proofs");
        return new StoredDocument(key, file.getOriginalFilename(), contentType);
    }

    public String urlOf(String key) {
        return minioService.getFileUrl(key);
    }

    private static String detectContentType(MultipartFile file) {
        byte[] head = new byte[4];
        try (InputStream in = file.getInputStream()) {
            int read = in.readNBytes(head, 0, head.length);
            if (read < 3) return null;
        } catch (IOException e) {
            throw new BadRequestException("Lecture du fichier impossible");
        }
        if (startsWith(head, PDF)) return "application/pdf";
        if (startsWith(head, JPEG)) return "image/jpeg";
        if (startsWith(head, PNG)) return "image/png";
        return null;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(Arrays.copyOf(data, prefix.length), prefix);
    }
}
