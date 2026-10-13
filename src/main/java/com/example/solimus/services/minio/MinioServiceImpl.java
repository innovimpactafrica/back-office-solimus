package com.example.solimus.services.minio;

import io.minio.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class MinioServiceImpl implements MinioService {

    /**
     * Peut être null si MinIO n'est pas configuré (pas de clés en dev local).
     * Toutes les méthodes doivent vérifier minioClient != null avant utilisation.
     */
    @Autowired(required = false)
    private MinioClient minioClient;

    @Value("${minio.bucket:solimus}")
    private String bucket;

    @Value("${minio.url:http://localhost:9000}")
    private String minioUrl;

    /** Vérifie que MinIO est disponible, lève une exception explicite sinon. */
    private void requireMinio(String operation) {
        if (minioClient == null) {
            throw new RuntimeException("MinIO n'est pas configuré (MINIO_ACCESS_KEY / MINIO_SECRET_KEY vides). "
                    + "Opération '" + operation + "' impossible.");
        }
    }

//-----------------------------------UPLOAD d'un fichier unique-----------------------------------
    @Override
    public String uploadFile(MultipartFile file, String folder) {
        // Upload à la racine du bucket coop-achat (pas de sous-dossiers).
        if (file == null || file.isEmpty()) {
            return null;
        }
        requireMinio("uploadFile");
        try {
            boolean found = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }

            String originalFilename = file.getOriginalFilename();
            String extension = StringUtils.getFilenameExtension(originalFilename);
            String fileName = UUID.randomUUID() + (extension != null ? "." + extension : "");
            // Toujours à la racine du bucket (folder ignoré)
            String objectKey = fileName;

          //upload le fichier dans le bucket
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build());

            return objectKey;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error uploading file to Minio", e);
            throw new RuntimeException("Erreur lors de l'upload du fichier : " + e.getMessage());
        }
    }

    //-----------------------------------UPLOAD de plusieurs fichiers-----------------------------------
    @Override
    public List<String> uploadMultipleFiles(List<MultipartFile> files, String folder) {
        // Boucle sur les fichiers et appelle uploadFile pour chacun.
        List<String> fileNames = new ArrayList<>();
        if (files != null && !files.isEmpty()) {
            for (MultipartFile file : files) {
                String fileName = uploadFile(file, folder);
                if (fileName != null) {
                    fileNames.add(fileName);
                }
            }
        }
        return fileNames;
    }


    /** Extrait le nom de fichier (sans préfixe de dossier) - tout est stocké à la racine du bucket. */
    private String toObjectKey(String path) {
        if (path == null || path.isBlank()) return path;
        int lastSlash = path.lastIndexOf('/');
        return lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
    }

    //-----------------------------------GET l'URL d'un fichier-----------------------------------
    @Override
    public String getFileUrl(String fileName) {
        if (fileName == null || fileName.isEmpty()) return null;
        return minioUrl + "/" + bucket + "/" + toObjectKey(fileName);
    }

    // Télécharge le fichier depuis MinIO et retourne son InputStream.
    @Override
    public InputStream getFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("Chemin fichier vide");
        }
        requireMinio("getFile");
        String objectKey = toObjectKey(fileName);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.error("Error getting file from Minio (objectKey={})", objectKey, e);
            throw new RuntimeException("Erreur lors de la récupération du fichier : " + e.getMessage());
        }
    }


    //-----------------------------------DELETE un fichier-----------------------------------
    @Override
    public void deleteFile(String fileName) {
        requireMinio("deleteFile");
        String objectKey = toObjectKey(fileName);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build());
        } catch (Exception e) {
            log.error("Error deleting file from Minio", e);
            throw new RuntimeException("Erreur lors de la suppression du fichier : " + e.getMessage());
        }
    }
}
