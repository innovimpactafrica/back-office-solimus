package com.example.solimus.services.minio;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;

/**
 * Service d'interaction avec MinIO pour le stockage et la récupération de fichiers.
 */
public interface MinioService {

    /**
     * Upload un fichier unique vers MinIO.
     *
     * @param file   le fichier à uploader (MultipartFile)
     * @param folder le dossier (préfixe) dans le bucket où stocker le fichier
     * @return l'URL ou le nom/identifiant du fichier stocké
     */
    String uploadFile(MultipartFile file, String folder);

    /**
     * Upload plusieurs fichiers vers MinIO.
     *
     * @param files  la liste des fichiers à uploader
     * @param folder le dossier (préfixe) dans le bucket où stocker les fichiers
     * @return la liste des URL ou identifiants des fichiers stockés
     */
    List<String> uploadMultipleFiles(List<MultipartFile> files, String folder);

    /**
     * Récupère l'URL d'accès publique ou signée d'un fichier.
     *
     * @param fileName le nom ou l'identifiant du fichier dans MinIO
     * @return l'URL permettant d'accéder au fichier
     */
    String getFileUrl(String fileName);

    /**
     * Génère une URL pré-signée temporaire pour télécharger un fichier.
     *
     * @param fileName le nom ou l'identifiant du fichier dans MinIO
     * @param expirySeconds durée de validité en secondes
     * @return URL pré-signée temporaire
     */
    String getPresignedDownloadUrl(String fileName, int expirySeconds);

    /**
     * Télécharge un fichier depuis MinIO et retourne son flux.
     *
     * @param fileName le nom ou l'identifiant du fichier dans MinIO
     * @return un InputStream du contenu du fichier
     */
    InputStream getFile(String fileName);

    /**
     * Supprime un fichier du stockage MinIO.
     *
     * @param fileName le nom ou l'identifiant du fichier à supprimer
     */
    void deleteFile(String fileName);

    /**
     * Transforme une liste de chemins de fichiers MinIO en URLs pré-signées temporaires.
     *
     * @param urls la liste des chemins/identifiants des fichiers
     * @return la liste des URLs pré-signées
     */
    List<String> toPresignedUrls(List<String> urls);
}
