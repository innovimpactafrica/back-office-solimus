package com.example.solimus.services.minio;

import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioServiceImpl implements MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.url}")
    private String minioUrl;

//-----------------------------------UPLOAD d'un fichier unique-----------------------------------
    @Override
    public String uploadFile(MultipartFile file, String folder) {
        // Upload à la racine du bucket coop-achat (pas de sous-dossiers).
        if (file == null || file.isEmpty()) {
            return null;
        }
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

            return getFileUrl(objectKey);
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

    // Transforme les chemins de fichiers MinIO stockés en base de données en URLs temporaires signées
    // Ces URLs permettent au front d'afficher les images sans accès direct à MinIO
    public List<String> toPresignedUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return new ArrayList<>();
        }

        return urls.stream()
                .map(url -> getPresignedDownloadUrl(url, 3600))
                .collect(Collectors.toList());
    }
    @Override
    public String getPresignedDownloadUrl(String fileName, int expirySeconds) {
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("Chemin fichier vide");
        }
        String objectKey = toObjectKey(fileName);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirySeconds, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            log.error("Error generating presigned download URL from Minio (objectKey={})", objectKey, e);
            throw new RuntimeException("Erreur lors de la génération du lien de téléchargement : " + e.getMessage());
        }
    }

    // Télécharge le fichier depuis MinIO et retourne son InputStream.
    @Override
    public InputStream getFile(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new RuntimeException("Chemin fichier vide");
        }
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
