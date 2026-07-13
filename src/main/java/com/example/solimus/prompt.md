# Paramètre syndic: Pagination de tous les endpoints retournant une liste 
# Résidence : une seule création
# A relire : Il y a un FileController dans coopachat qui sert de proxy :

Fonctionnement coopachat :

Backend stocke dans MinIO → retourne objectKey (uuid.jpg)
Frontend demande : https://api.coopachat.innovimpactdev.cloud/api/files/uuid.jpg
FileController intercepte /api/files/{path} et stream le fichier depuis MinIO
Pourquoi cette architecture ?

MinIO n'est pas exposé publiquement
Le backend sert de proxy sécurisé
Les images passent par le backend (endpoint public permitAll)
Je dois vérifier si solimus a aussi un FileController :

SearchedFileController|/files|/uploads in c:/PROJECT/projet-innov/back-office-solimus
Thought for 1s
Read
FileController.java
Thought for 5s
Parfait ! Solimus a déjà un FileController identique à coopachat. L'architecture est déjà en place :

Architecture solimus (identique à coopachat) :

Images stockées dans MinIO
Backend retourne objectKey (uuid.jpg)
Frontend construit : https://api.solimus.innovimpactdev.cloud/api/files/uuid.jpg
FileController (/api/files/{path}) stream le fichier depuis MinIO
Rien à modifier côté backend solimus - tout est déjà configuré comme coopachat. Le frontend solimus doit simplement :

Avoir imageServerUrl dans environment.ts
Construire les URLs comme coopachat : ${imageServerUrl}/files/${objectKey}
L'architecture est cohérente entre les deux projets.