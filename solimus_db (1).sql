-- MySQL dump 10.13  Distrib 8.0.45, for Win64 (x86_64)
--
-- Host: 89.167.72.116    Database: solimus_db
-- ------------------------------------------------------
-- Server version	8.0.45-0ubuntu0.24.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `activation_codes`
--

DROP TABLE IF EXISTS `activation_codes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activation_codes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `code` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expiry_date` datetime(6) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVATION',
  `used` tinyint(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_activation_user` (`user_id`),
  CONSTRAINT `FK_activation_user_id` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `activation_codes`
--

LOCK TABLES `activation_codes` WRITE;
/*!40000 ALTER TABLE `activation_codes` DISABLE KEYS */;
/*!40000 ALTER TABLE `activation_codes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `estimated_delays`
--

DROP TABLE IF EXISTS `estimated_delays`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `estimated_delays` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `label` varchar(255) NOT NULL,
  `days_equivalent` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKd1ynd49g4ja9kaj2lr6eb5owi` (`label`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `estimated_delays`
--

LOCK TABLES `estimated_delays` WRITE;
/*!40000 ALTER TABLE `estimated_delays` DISABLE KEYS */;
INSERT INTO `estimated_delays` VALUES (2,'48 heures',2),(3,'3 jours',3),(4,'1 semaine',7),(5,'24 heures',1);
/*!40000 ALTER TABLE `estimated_delays` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) DEFAULT NULL,
  `description` varchar(200) NOT NULL,
  `type` varchar(20) NOT NULL,
  `script` varchar(1000) NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','<< Flyway Baseline >>','BASELINE','<< Flyway Baseline >>',NULL,'root','2026-05-12 16:46:50',0,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_comments`
--

DROP TABLE IF EXISTS `intervention_comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_comments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `author_id` bigint NOT NULL,
  `intervention_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKma89d2jvip8uql9o6r74cxb4y` (`author_id`),
  KEY `FK3hbv4wsqg5lq4gnntftho4to7` (`intervention_id`),
  CONSTRAINT `FK3hbv4wsqg5lq4gnntftho4to7` FOREIGN KEY (`intervention_id`) REFERENCES `intervention_requests` (`id`),
  CONSTRAINT `FKma89d2jvip8uql9o6r74cxb4y` FOREIGN KEY (`author_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_comments`
--

LOCK TABLES `intervention_comments` WRITE;
/*!40000 ALTER TABLE `intervention_comments` DISABLE KEYS */;
INSERT INTO `intervention_comments` VALUES (1,'Les travaux sont bien démarrés','2026-05-18 16:10:20.433069',8,2),(2,'on finira bientôt','2026-05-18 16:44:02.951175',8,2),(3,'MERCI','2026-05-26 16:16:42.734496',8,3);
/*!40000 ALTER TABLE `intervention_comments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_notified_providers`
--

DROP TABLE IF EXISTS `intervention_notified_providers`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_notified_providers` (
  `intervention_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  KEY `FK80h52iokmt8861ch8nexuo04j` (`user_id`),
  KEY `FKsvyys4m9mbjgf1jkkvgam071v` (`intervention_id`),
  CONSTRAINT `FK80h52iokmt8861ch8nexuo04j` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKsvyys4m9mbjgf1jkkvgam071v` FOREIGN KEY (`intervention_id`) REFERENCES `intervention_requests` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_notified_providers`
--

LOCK TABLES `intervention_notified_providers` WRITE;
/*!40000 ALTER TABLE `intervention_notified_providers` DISABLE KEYS */;
INSERT INTO `intervention_notified_providers` VALUES (2,8),(2,9),(3,8),(3,13),(3,22),(4,8),(5,8);
/*!40000 ALTER TABLE `intervention_notified_providers` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_photos`
--

DROP TABLE IF EXISTS `intervention_photos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_photos` (
  `intervention_id` bigint NOT NULL,
  `photo_url` varchar(255) DEFAULT NULL,
  KEY `FKj4mudvb42fewm8yqmerqbcs2w` (`intervention_id`),
  CONSTRAINT `FKj4mudvb42fewm8yqmerqbcs2w` FOREIGN KEY (`intervention_id`) REFERENCES `intervention_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_photos`
--

LOCK TABLES `intervention_photos` WRITE;
/*!40000 ALTER TABLE `intervention_photos` DISABLE KEYS */;
INSERT INTO `intervention_photos` VALUES (2,'59e18f13-ca80-4e01-a460-10fb3e0b0d78.png'),(3,'https://minio.innovimpactafrica.cloud/solimus/6ab05cea-451c-4146-a3ad-0cd4c4f0e224.png'),(4,'https://minio.innovimpactafrica.cloud/solimus/b259d6b0-575e-48c2-8470-1141d0f37b3b.jpg'),(5,'https://minio.innovimpactafrica.cloud/solimus/70f3b152-af5d-4c0b-9986-00e0b2006ac8.png');
/*!40000 ALTER TABLE `intervention_photos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_requests`
--

DROP TABLE IF EXISTS `intervention_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `description` text,
  `status` enum('CANCELLED','FINAL_VALIDATION','FINISHED','PENDING','QUOTE_SENT','STARTED','SYNDIC_VALIDATED') NOT NULL,
  `title` varchar(255) NOT NULL,
  `property_id` bigint NOT NULL,
  `residence_id` bigint NOT NULL,
  `selected_provider_id` bigint DEFAULT NULL,
  `specialty_id` bigint DEFAULT NULL,
  `syndic_id` bigint NOT NULL,
  `deposit_amount` decimal(38,2) DEFAULT NULL,
  `finished_at` datetime(6) DEFAULT NULL,
  `remaining_amount` decimal(38,2) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `total_amount` decimal(38,2) DEFAULT NULL,
  `validated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKtncm3sqi1qnufai75490kn329` (`property_id`),
  KEY `FK2mqex6h6c0uihemanlntf66bd` (`residence_id`),
  KEY `FKped0hvreeboe36d5c9n0bag9r` (`selected_provider_id`),
  KEY `FK8gf3xfdrv6nd2bvck6n7cyvy5` (`specialty_id`),
  KEY `FKlrbqqa6reoas1mjvthevpdwvn` (`syndic_id`),
  CONSTRAINT `FK2mqex6h6c0uihemanlntf66bd` FOREIGN KEY (`residence_id`) REFERENCES `residences` (`id`),
  CONSTRAINT `FK8gf3xfdrv6nd2bvck6n7cyvy5` FOREIGN KEY (`specialty_id`) REFERENCES `specialties` (`id`),
  CONSTRAINT `FKlrbqqa6reoas1mjvthevpdwvn` FOREIGN KEY (`syndic_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKped0hvreeboe36d5c9n0bag9r` FOREIGN KEY (`selected_provider_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKtncm3sqi1qnufai75490kn329` FOREIGN KEY (`property_id`) REFERENCES `properties` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_requests`
--

LOCK TABLES `intervention_requests` WRITE;
/*!40000 ALTER TABLE `intervention_requests` DISABLE KEYS */;
INSERT INTO `intervention_requests` VALUES (2,'2026-05-15 11:57:46.378270','De l’eau s’écoule en continu sous l’évier de la cuisine. Le robinet semble fonctionnel, mais il y a une fuite au niveau du tuyau de raccordement. L’eau commence à s’accumuler au sol.','FINAL_VALIDATION','Fuite d’eau cuisine',5,1,8,1,2,3000.00,'2026-05-18 16:45:17.103809',0.00,'2026-05-18 16:09:11.797331',94000.00,'2026-05-19 15:29:18.739203'),(3,'2026-05-22 09:14:42.033281','Fuite d\'eau au niveau du groupe de sécurité du chauffe-eau. L\'eau coule en continu dans la cuvette de vidange. Le chauffe-eau a 8 ans. Appartement inondé sur 5m². Intervention urgente demandée.','FINISHED','Fuite d\'eau sur chauffe-eau',5,2,8,1,2,0.00,'2026-05-26 16:16:43.368775',27000.00,'2026-05-26 16:05:42.801721',27000.00,NULL),(4,'2026-05-27 12:28:10.300038','Les prises et les luminaires du salon ne fonctionnent plus depuis hier soir. Une odeur de brûlé a été signalée près du tableau électrique.','QUOTE_SENT','Panne électrique salon',4,2,NULL,1,2,0.00,NULL,0.00,NULL,0.00,NULL),(5,'2026-05-27 12:29:57.143663','Une fuite importante a été détectée sous l’évier de la cuisine. L’eau commence à se répandre dans le couloir et risque d’endommager le sol.','PENDING','Fuite d’eau cuisine',4,2,NULL,1,2,0.00,NULL,0.00,NULL,0.00,NULL);
/*!40000 ALTER TABLE `intervention_requests` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_status_history`
--

DROP TABLE IF EXISTS `intervention_status_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_status_history` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','FINAL_VALIDATION','FINISHED','PENDING','QUOTE_SENT','STARTED','SYNDIC_VALIDATED') NOT NULL,
  `changed_by_id` bigint NOT NULL,
  `intervention_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKmvu6gr57rhwpa7s82snxb6eb5` (`changed_by_id`),
  KEY `FK657e5hrnpg844os3hykm7ef4o` (`intervention_id`),
  CONSTRAINT `FK657e5hrnpg844os3hykm7ef4o` FOREIGN KEY (`intervention_id`) REFERENCES `intervention_requests` (`id`),
  CONSTRAINT `FKmvu6gr57rhwpa7s82snxb6eb5` FOREIGN KEY (`changed_by_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_status_history`
--

LOCK TABLES `intervention_status_history` WRITE;
/*!40000 ALTER TABLE `intervention_status_history` DISABLE KEYS */;
INSERT INTO `intervention_status_history` VALUES (1,'2026-05-18 14:48:06.267350','SYNDIC_VALIDATED',2,2),(2,'2026-05-17 14:00:00.000000','QUOTE_SENT',1,2),(3,'2026-05-18 16:09:11.829388','STARTED',8,2),(4,'2026-05-18 16:45:17.130533','FINISHED',8,2),(5,'2026-05-19 15:29:18.967843','FINAL_VALIDATION',2,2),(6,'2026-05-22 09:14:42.078053','PENDING',2,3),(7,'2026-05-22 09:28:17.314422','QUOTE_SENT',8,3),(8,'2026-05-22 09:30:44.483557','SYNDIC_VALIDATED',2,3),(10,'2026-05-22 10:55:16.662034','SYNDIC_VALIDATED',2,3),(11,'2026-05-26 16:05:42.818157','STARTED',8,3),(12,'2026-05-26 16:16:43.369410','FINISHED',8,3),(13,'2026-05-27 12:28:10.327900','PENDING',2,4),(14,'2026-05-27 12:29:57.170223','PENDING',2,5),(15,'2026-05-27 21:43:56.142851','QUOTE_SENT',8,4);
/*!40000 ALTER TABLE `intervention_status_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_work_photos`
--

DROP TABLE IF EXISTS `intervention_work_photos`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_work_photos` (
  `intervention_id` bigint NOT NULL,
  `work_photo_url` varchar(255) DEFAULT NULL,
  KEY `FK3drk5gk5hhyj5qtajau4jphbl` (`intervention_id`),
  CONSTRAINT `FK3drk5gk5hhyj5qtajau4jphbl` FOREIGN KEY (`intervention_id`) REFERENCES `intervention_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_work_photos`
--

LOCK TABLES `intervention_work_photos` WRITE;
/*!40000 ALTER TABLE `intervention_work_photos` DISABLE KEYS */;
INSERT INTO `intervention_work_photos` VALUES (2,'https://minio.innovimpactafrica.cloud/coop-achat/04256e80-8581-4e02-943d-49b9884da7a6.png');
/*!40000 ALTER TABLE `intervention_work_photos` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `intervention_zones`
--

DROP TABLE IF EXISTS `intervention_zones`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `intervention_zones` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `city` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `intervention_zones`
--

LOCK TABLES `intervention_zones` WRITE;
/*!40000 ALTER TABLE `intervention_zones` DISABLE KEYS */;
/*!40000 ALTER TABLE `intervention_zones` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `payments`
--

DROP TABLE IF EXISTS `payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `method` enum('ORANGE_MONEY','VIREMENT_BANCAIRE','WAVE') DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `reference` varchar(255) NOT NULL,
  `status` enum('COMPLETED','FAILED','PENDING') DEFAULT NULL,
  `type` enum('ACOMPTE','SOLDE') DEFAULT NULL,
  `intervention_request_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `syndic_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKxqxnoljqu6mkrkwwcnfisx38` (`reference`),
  KEY `FK3i9rq3lmwdy2176mcdc9wstvb` (`intervention_request_id`),
  KEY `FK9lvrvodn6mf6kk973hjabsloh` (`provider_id`),
  KEY `FK7slfx23dkq5oidu79r8eywt8k` (`syndic_id`),
  CONSTRAINT `FK3i9rq3lmwdy2176mcdc9wstvb` FOREIGN KEY (`intervention_request_id`) REFERENCES `intervention_requests` (`id`),
  CONSTRAINT `FK7slfx23dkq5oidu79r8eywt8k` FOREIGN KEY (`syndic_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK9lvrvodn6mf6kk973hjabsloh` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `payments`
--

LOCK TABLES `payments` WRITE;
/*!40000 ALTER TABLE `payments` DISABLE KEYS */;
INSERT INTO `payments` VALUES (1,3000.00,'2026-05-19 08:29:42.192148','WAVE','2026-05-19 08:29:42.172925','PAY-746952','COMPLETED','ACOMPTE',2,8,2),(2,91000.00,'2026-05-19 15:29:18.788796','WAVE','2026-05-19 15:29:18.750178','SOL-165300','COMPLETED','SOLDE',2,8,2);
/*!40000 ALTER TABLE `payments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `properties`
--

DROP TABLE IF EXISTS `properties`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `properties` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `area` double DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `floor` int DEFAULT NULL,
  `reference` varchar(255) NOT NULL,
  `type` enum('APPARTEMENT','LOCAL_COMMERCIAL','PARKING','STUDIO') NOT NULL,
  `residence_id` bigint NOT NULL,
  `owner_id` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdj5mmdseny0h4rs965j985iyy` (`residence_id`),
  KEY `FK32k2h9s30s0ukftb8hj947ef2` (`owner_id`),
  CONSTRAINT `FK32k2h9s30s0ukftb8hj947ef2` FOREIGN KEY (`owner_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKdj5mmdseny0h4rs965j985iyy` FOREIGN KEY (`residence_id`) REFERENCES `residences` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `properties`
--

LOCK TABLES `properties` WRITE;
/*!40000 ALTER TABLE `properties` DISABLE KEYS */;
INSERT INTO `properties` VALUES (4,42,NULL,2,'S-204','STUDIO',1,5),(5,15,NULL,-1,'PK-12','PARKING',2,5),(6,85,NULL,1,'A-101','APPARTEMENT',1,6);
/*!40000 ALTER TABLE `properties` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `provider_ratings`
--

DROP TABLE IF EXISTS `provider_ratings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `provider_ratings` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `comment` text,
  `created_at` datetime(6) DEFAULT NULL,
  `rating` int NOT NULL,
  `evaluator_id` bigint NOT NULL,
  `intervention_request_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKbh2sumtgyisw9w77n7miy9qg9` (`intervention_request_id`),
  KEY `FK2pqhut8akhwhoj9mtta3nnumg` (`evaluator_id`),
  KEY `FK38my8w9iha47suscrm95xseje` (`provider_id`),
  CONSTRAINT `FK2pqhut8akhwhoj9mtta3nnumg` FOREIGN KEY (`evaluator_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK38my8w9iha47suscrm95xseje` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FK6u8wvngpbamnj3t094ftx7tjj` FOREIGN KEY (`intervention_request_id`) REFERENCES `intervention_requests` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `provider_ratings`
--

LOCK TABLES `provider_ratings` WRITE;
/*!40000 ALTER TABLE `provider_ratings` DISABLE KEYS */;
/*!40000 ALTER TABLE `provider_ratings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quote_items`
--

DROP TABLE IF EXISTS `quote_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quote_items` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `description` varchar(255) NOT NULL,
  `quantity` int NOT NULL,
  `type` enum('LABOR','MATERIAL') NOT NULL,
  `unit_price` decimal(38,2) NOT NULL,
  `quote_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKrvsmoef7yontnlu1lwxrb0g3g` (`quote_id`),
  CONSTRAINT `FKrvsmoef7yontnlu1lwxrb0g3g` FOREIGN KEY (`quote_id`) REFERENCES `quotes` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=12 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quote_items`
--

LOCK TABLES `quote_items` WRITE;
/*!40000 ALTER TABLE `quote_items` DISABLE KEYS */;
INSERT INTO `quote_items` VALUES (1,'Tuyaux PVC 32mm',4,'MATERIAL',8500.00,1),(2,'Raccords et joints',1,'MATERIAL',15000.00,1),(3,'Main d\'œuvre plomberie',1,'LABOR',45000.00,1),(4,'Kit complet robinetterie haut de gamme',2,'MATERIAL',75000.00,2),(5,'Tuyauterie renforcée',6,'MATERIAL',18000.00,2),(6,'Main d\'œuvre spécialisée plomberie',1,'LABOR',95000.00,2),(7,'Test de pression et vérification finale',1,'LABOR',25000.00,2),(8,'Robinet de cuisine en acier inoxydable',1,'MATERIAL',12000.00,3),(9,'Installation et remplacement du robinet',1,'LABOR',15000.00,3),(10,'Test matériel',1,'MATERIAL',1.00,4),(11,'Test main d’œuvre',1,'LABOR',1.00,4);
/*!40000 ALTER TABLE `quote_items` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `quotes`
--

DROP TABLE IF EXISTS `quotes`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quotes` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `additional_comments` text,
  `created_at` datetime(6) DEFAULT NULL,
  `labor_total_amount` decimal(38,2) NOT NULL,
  `material_total_amount` decimal(38,2) NOT NULL,
  `status` enum('ACCEPTED','DRAFT','REJECTED','SENT') NOT NULL,
  `total_amount` decimal(38,2) NOT NULL,
  `intervention_request_id` bigint NOT NULL,
  `provider_id` bigint NOT NULL,
  `estimated_delay_id` bigint NOT NULL,
  `reference` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKfrs9hf93863t0uccsbsw08fg9` (`intervention_request_id`),
  KEY `FK614b2bnunk5d2bjfk7jcj1ipl` (`provider_id`),
  KEY `FK2vy80xh9n8smpamiarf4opf5c` (`estimated_delay_id`),
  CONSTRAINT `FK2vy80xh9n8smpamiarf4opf5c` FOREIGN KEY (`estimated_delay_id`) REFERENCES `estimated_delays` (`id`),
  CONSTRAINT `FK614b2bnunk5d2bjfk7jcj1ipl` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKfrs9hf93863t0uccsbsw08fg9` FOREIGN KEY (`intervention_request_id`) REFERENCES `intervention_requests` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `quotes`
--

LOCK TABLES `quotes` WRITE;
/*!40000 ALTER TABLE `quotes` DISABLE KEYS */;
INSERT INTO `quotes` VALUES (1,'Intervention possible dès demain matin. Le matériel sera acheté après validation du devis.','2026-05-15 15:53:47.621422',45000.00,49000.00,'ACCEPTED',94000.00,2,8,2,'DEV-235512'),(2,'Intervention premium avec remplacement complet des équipements défectueux et garantie de 6 mois.','2026-05-15 15:58:50.675006',120000.00,258000.00,'REJECTED',378000.00,2,9,3,''),(3,'Nous pouvons commencer les travaux demain matin.','2026-05-22 09:28:17.347176',15000.00,12000.00,'ACCEPTED',27000.00,3,8,2,'DEV-930487'),(4,'Devis de test pour validation du paiement Wave.','2026-05-27 21:43:56.175275',1.00,1.00,'SENT',2.00,4,8,2,'DEV-373097');
/*!40000 ALTER TABLE `quotes` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `residences`
--

DROP TABLE IF EXISTS `residences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `residences` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apartment_count` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `floor_count` int DEFAULT NULL,
  `full_address` varchar(500) NOT NULL,
  `latitude` decimal(10,7) NOT NULL,
  `longitude` decimal(10,7) NOT NULL,
  `name` varchar(255) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `syndic_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKm98kf20ow2cvlgb9iypilgtx4` (`syndic_id`),
  CONSTRAINT `FKm98kf20ow2cvlgb9iypilgtx4` FOREIGN KEY (`syndic_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=latin1;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `residences`
--

LOCK TABLES `residences` WRITE;
/*!40000 ALTER TABLE `residences` DISABLE KEYS */;
INSERT INTO `residences` VALUES (1,24,'2026-05-15 07:53:17.386728',5,'Route des Almadies, Dakar, Sénégal',14.7392000,-17.5113000,'Résidence Les Almadies','2026-05-15 07:53:17.387727',2),(2,40,'2026-05-15 07:53:49.317712',8,'Keur Gorgui, Dakar, Sénégal',14.7165000,-17.4678000,'Résidence Keur Gorgui Prestige','2026-05-15 07:53:49.317712',2),(4,60,'2026-05-15 08:01:16.071152',12,'Avenue Léopold Sédar Senghor, Plateau, Dakar, Sénégal',14.6708000,-17.4381000,'Résidence Plateau Horizon','2026-05-15 08:01:16.071152',2),(5,30,'2026-05-15 08:03:50.674155',6,'Mermoz Pyrotechnie, Dakar, Sénégal',14.7061000,-17.4752000,'Résidence Mermoz Elite','2026-05-15 08:03:50.674155',3),(6,18,'2026-05-15 08:04:15.538541',4,'Parcelles Assainies Unité 15, Dakar, Sénégal',14.7608000,-17.4389000,'Résidence Parcelles Modernes','2026-05-15 08:04:15.538541',2),(7,45,'2026-05-15 08:04:29.820601',9,'Rufisque Centre, Rufisque, Sénégal',14.7159000,-17.2733000,'Résidence Rufisque Océan','2026-05-15 08:04:29.820601',2),(8,0,'2026-05-22 10:26:10.778694',0,'string',0.0000000,0.0000000,'string','2026-05-22 10:26:10.779689',2);
/*!40000 ALTER TABLE `residences` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (5,'ROLE_ADMIN','Rôle par défaut pour Administrateur'),(6,'ROLE_SYNDIC','Rôle par défaut pour Syndic'),(7,'ROLE_PRESTATAIRE','Rôle par défaut pour Prestataire'),(8,'ROLE_COPROPRIETAIRE','Rôle par défaut pour Copropriétaire');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `specialties`
--

DROP TABLE IF EXISTS `specialties`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `specialties` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `specialties`
--

LOCK TABLES `specialties` WRITE;
/*!40000 ALTER TABLE `specialties` DISABLE KEYS */;
INSERT INTO `specialties` VALUES (1,'Plomberie','Installation, réparation et entretien des systèmes d’eau, canalisations et sanitaires.'),(2,'Electricité',NULL);
/*!40000 ALTER TABLE `specialties` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subscription_payments`
--

DROP TABLE IF EXISTS `subscription_payments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscription_payments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `montant` decimal(38,2) DEFAULT NULL,
  `moyen_paiement` enum('ORANGE_MONEY','VIREMENT_BANCAIRE','WAVE') DEFAULT NULL,
  `periode` varchar(255) DEFAULT NULL,
  `reference` varchar(255) NOT NULL,
  `statut` enum('ECHOUE','PAYE') DEFAULT NULL,
  `subscription_id` bigint NOT NULL,
  `renouvellement_auto` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKe9owuc4v5qpx2lhlylt31vcya` (`reference`),
  KEY `FK9note178r84igupsnxsuireyy` (`subscription_id`),
  CONSTRAINT `FK9note178r84igupsnxsuireyy` FOREIGN KEY (`subscription_id`) REFERENCES `subscriptions` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `subscription_payments`
--

LOCK TABLES `subscription_payments` WRITE;
/*!40000 ALTER TABLE `subscription_payments` DISABLE KEYS */;
INSERT INTO `subscription_payments` VALUES (1,'2026-05-19 15:07:05.858367',35000.00,'WAVE','mai 2026','PAY-2026-05-656061','PAYE',7,_binary '\0'),(2,'2026-05-19 15:27:38.190286',35000.00,'ORANGE_MONEY','mai 2026','PAY-2026-05-857928','PAYE',5,_binary '\0');
/*!40000 ALTER TABLE `subscription_payments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subscriptions`
--

DROP TABLE IF EXISTS `subscriptions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subscriptions` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) DEFAULT NULL,
  `date_activation` date DEFAULT NULL,
  `date_expiration` date DEFAULT NULL,
  `moyen_paiement` enum('ORANGE_MONEY','VIREMENT_BANCAIRE','WAVE') DEFAULT NULL,
  `plan` enum('GRATUIT','PREMIUM') DEFAULT NULL,
  `renouvellement_auto` bit(1) NOT NULL,
  `status` enum('ACTIVE','CANCELLED','EXPIRED') DEFAULT NULL,
  `provider_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKcpu0o8yxh6leaymo7yct31vyo` (`provider_id`),
  CONSTRAINT `FKc4axdpg5fvapsbng1w78hrw4i` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `subscriptions`
--

LOCK TABLES `subscriptions` WRITE;
/*!40000 ALTER TABLE `subscriptions` DISABLE KEYS */;
INSERT INTO `subscriptions` VALUES (1,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',17),(2,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',18),(3,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',19),(4,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',20),(5,'2026-05-19 12:42:36.000000','2026-05-19','2026-06-19','ORANGE_MONEY','PREMIUM',_binary '\0','CANCELLED',21),(6,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',22),(7,'2026-05-19 12:42:36.000000','2026-05-19','2026-06-19','WAVE','PREMIUM',_binary '','ACTIVE',13),(8,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',14),(9,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',15),(10,'2026-05-19 12:42:36.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',16),(16,'2026-05-19 15:12:25.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',8),(17,'2026-05-19 15:12:25.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',9),(18,'2026-05-19 15:12:25.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',10),(19,'2026-05-19 15:12:25.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',11),(20,'2026-05-19 15:12:25.000000','2026-05-19',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',12),(23,'2026-05-22 16:06:30.368751','2026-05-22',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',23),(24,'2026-05-23 11:21:28.204965','2026-05-23',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',24),(25,'2026-05-23 12:20:36.820097','2026-05-23',NULL,NULL,'GRATUIT',_binary '\0','ACTIVE',25);
/*!40000 ALTER TABLE `subscriptions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `token_blacklist`
--

DROP TABLE IF EXISTS `token_blacklist`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `token_blacklist` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `token` varchar(512) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expiry_date` datetime(6) NOT NULL,
  `blacklisted_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `token_blacklist`
--

LOCK TABLES `token_blacklist` WRITE;
/*!40000 ALTER TABLE `token_blacklist` DISABLE KEYS */;
/*!40000 ALTER TABLE `token_blacklist` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `first_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `last_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `company_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT 'PENDING',
  `profile_photo_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) DEFAULT CURRENT_TIMESTAMP(6),
  `role_id` bigint DEFAULT NULL,
  `specialty_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `latitude` decimal(10,7) DEFAULT NULL,
  `longitude` decimal(10,7) DEFAULT NULL,
  `address_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `intervention_zone` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_available` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_users_email` (`email`),
  UNIQUE KEY `UK_users_phone` (`phone`),
  KEY `FK_users_role` (`role_id`),
  KEY `FK_users_specialty` (`specialty_id`),
  CONSTRAINT `FK_users_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FK_users_specialty` FOREIGN KEY (`specialty_id`) REFERENCES `specialties` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,'Admin','Solimus','adminsolimus@yopmail.com','+221770000000','$2a$10$U5Njkw7vJtKbU9ysqwXVieYnwCGTgTxhKFxxxq0vTd.A6Ob3XDgXK',NULL,'ACTIVE',NULL,'2026-05-12 16:51:16.874127',5,NULL,'2026-05-12 16:51:16.874127',NULL,NULL,NULL,NULL,_binary '\0'),(2,'Mariétou ','Diallo','sdfaye@innovimpactafrica.com','781883729','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','SEN PLOMBERIE','ACTIVE',NULL,'2026-05-13 13:54:40.210349',6,NULL,'2026-05-27 12:14:49.578141',NULL,NULL,NULL,NULL,_binary '\0'),(3,'Aminata ','Dème','syndic2@yopmail.com','774535665','$2a$10$wujFxPJUiMaHzJBZ094.7ufwjw23LW6ah0LdeayMAtKxLvzxO7ZvW',NULL,'ACTIVE',NULL,'2026-05-13 14:41:04.521835',6,NULL,'2026-05-13 14:43:05.936547',NULL,NULL,NULL,NULL,_binary '\0'),(4,'nata ','Kâ','syndic3@yopmail.com','774235665','$2a$10$JRm4H0frSlm/W9NB5ug4Fe8i3kMUPS1i7HQXWEgQaf/xMGbn8Sb4i',NULL,'ACTIVE',NULL,'2026-05-13 15:23:17.294783',6,NULL,'2026-05-13 15:24:47.814496',NULL,NULL,NULL,NULL,_binary '\0'),(5,'Birame',' DaSilva','coop1@yopmail.com','775676778',NULL,NULL,'PENDING',NULL,'2026-05-15 08:11:55.845008',8,NULL,'2026-05-15 08:11:55.845008',NULL,NULL,NULL,NULL,_binary '\0'),(6,'Marie',' Claire Diop','coop2@yopmail.com','775276778',NULL,NULL,'PENDING',NULL,'2026-05-15 08:16:34.325121',8,NULL,'2026-05-15 08:16:34.325121',NULL,NULL,NULL,NULL,_binary '\0'),(7,'Samba','  Diop','coop3@yopmail.com','755276778',NULL,NULL,'PENDING',NULL,'2026-05-15 08:26:44.772219',8,NULL,'2026-05-15 08:26:44.772219',NULL,NULL,NULL,NULL,_binary '\0'),(8,'sokhna','faye','fayesokhnadiarra51@gmail.com','775642332','$2a$10$b7FMCtycvX8r/n19prKoGuzlBICdJ45xe/mdV1I/3C05HVjdbj.32','SEN PLOMBERIE','ACTIVE','https://minio.innovimpactafrica.cloud/solimus/b54e4e3d-a1a5-4cb7-b130-d7ddc10fe27a.png','2026-05-15 09:41:44.760047',7,1,'2026-05-27 14:55:30.998151',14.7410000,-17.5089000,NULL,NULL,_binary ''),(9,'Aminata','Fall','pres2@yopmail.com','76 445 88 12','\\/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Electro Dakar Services','ACTIVE',NULL,'2026-05-15 09:51:09.835358',7,1,'2026-05-15 09:53:03.646252',14.7305000,-17.4950000,NULL,NULL,_binary '\0'),(10,'Cheikh','Ba','pres3@yopmail.com','78 990 11 22','\\/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Rapid Maintenance','ACTIVE',NULL,'2026-05-15 09:54:33.370255',7,1,'2026-05-15 09:55:54.210171',14.7886000,-16.9245000,NULL,NULL,_binary '\0'),(11,'Ibrahima','Diop','pres4@yopmail.com','70 112 33 44','\\/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Rufisque Tech','ACTIVE',NULL,'2026-05-15 09:56:48.986980',7,2,'2026-05-15 09:58:09.953743',14.7160000,-17.2730000,NULL,NULL,_binary '\0'),(12,'Fatou','Sarr','pres5@yopmail.com','75 221 77 88','\\/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Thiès Maintenance Pro','ACTIVE',NULL,'2026-05-15 09:58:41.902650',7,1,'2026-05-15 10:00:29.551016',14.7886000,-16.9245000,NULL,NULL,_binary '\0'),(13,'Mamadou','Diallo','pres6@yopmail.com','77 123 45 01','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Diallo Services','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.6928000,-17.4467000,NULL,'Plateau',_binary ''),(14,'Awa','Ndiaye','pres7@yopmail.com','77 123 45 02','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Ndiaye Plomberie','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.6760000,-17.4441000,NULL,'M├®dina',_binary ''),(15,'Ibrahima','Sarr','pres8@yopmail.com','77 123 45 03','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Sarr & Fils','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7539000,-17.5350000,NULL,'Almadies',_binary ''),(16,'Fatou','Diop','pres9@yopmail.com','78 456 78 04','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Diop Plomberie Express','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7376000,-17.4856000,NULL,'Ouakam',_binary ''),(17,'Cheikh','Kane','pres10@yopmail.com','78 456 78 05','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Kane Assainissement','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7392000,-17.4912000,NULL,'Yoff',_binary ''),(18,'Ousmane','Fall','pres11@yopmail.com','76 321 54 06','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Fall Plombier Pikine','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7645000,-17.3893000,NULL,'Pikine',_binary ''),(19,'Khadija','Ba','pres12@yopmail.com','76 321 54 07','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Ba Eaux & Plomb','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7708000,-17.4006000,NULL,'Gu├®diawaye',_binary ''),(20,'Serigne','Mbaye','pres13@yopmail.com','70 987 65 08','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Mbaye Depannage','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7151000,-17.2734000,NULL,'Rufisque',_binary ''),(21,'Adama','Sow','pres14@yopmail.com','70 987 65 09','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Sow Plomberie Moderne','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7540000,-17.4530000,NULL,'Parcelles Assainies',_binary ''),(22,'Mariama','Faye','pres15@yopmail.com','77 555 66 10','$2a$10$APdVZ/mVe1JRyfiQHb41se.KZ.xb/s8ASpEPRfjkqpZT96d1tHj3e','Faye Plombier Pro','ACTIVE',NULL,'2026-05-19 12:42:35.000000',7,1,'2026-05-19 12:42:35.000000',14.7179000,-17.4701000,NULL,'Libert├® 6',_binary ''),(23,'Ibrahima','Ndiaye','daoudadiallo@innovimpactafrica.com','781234567','$2a$10$IRK3XUEeFMb43CzvN8DnWeJydCx81LAFO6TjYSSGiEokFAbP9./N.','IN MultiServices','ACTIVE',NULL,'2026-05-22 16:06:30.297456',7,1,'2026-05-22 16:14:26.970357',14.7167000,-17.4677000,NULL,NULL,_binary ''),(24,'Daouda','Diallo','d02.daouda@gmail.com','+221776723124','$2a$10$nyWnEe/oJnBOm4IlAzJbLOg1TlrKIo5rUdOC1YsqOh6VfdsDuiCSa','SenPlomberie','ACTIVE',NULL,'2026-05-23 11:21:28.172583',7,1,'2026-05-23 12:10:38.666482',14.6937000,-17.4441000,NULL,NULL,_binary ''),(25,'Tra Mamadou','Bodian','bodianm372@gmail.com','780185266','$2a$10$eh.LtDApniURV75WVVXhVudRGFGaUw9Zrub.epAs9sNxah7pLPBDW','SenPlomberie','ACTIVE',NULL,'2026-05-23 12:20:36.793705',7,1,'2026-05-23 12:25:23.041476',14.6937000,-17.4441000,NULL,NULL,_binary '');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `wallets`
--

DROP TABLE IF EXISTS `wallets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `wallets` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `available_balance` decimal(38,2) NOT NULL,
  `pending_balance` decimal(38,2) NOT NULL,
  `total_this_month` decimal(38,2) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `provider_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK10trf60hg6clh9ynt2i95toaw` (`provider_id`),
  CONSTRAINT `FKc6dgm6j6em2vu7ipbrj3loii` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=18 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `wallets`
--

LOCK TABLES `wallets` WRITE;
/*!40000 ALTER TABLE `wallets` DISABLE KEYS */;
INSERT INTO `wallets` VALUES (1,67000.00,0.00,94000.00,'2026-05-26 17:01:08.428486',8),(2,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',17),(3,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',18),(4,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',19),(5,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',20),(6,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',21),(7,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',22),(8,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',13),(9,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',14),(10,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',15),(11,0.00,0.00,0.00,'2026-05-19 12:42:36.000000',16),(17,0.00,0.00,0.00,'2026-05-22 16:27:43.605206',23);
/*!40000 ALTER TABLE `wallets` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `withdrawal_requests`
--

DROP TABLE IF EXISTS `withdrawal_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `withdrawal_requests` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `amount` decimal(38,2) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `method` enum('ORANGE_MONEY','VIREMENT_BANCAIRE','WAVE') DEFAULT NULL,
  `phone_number` varchar(255) NOT NULL,
  `processed_at` datetime(6) DEFAULT NULL,
  `reference` varchar(255) NOT NULL,
  `status` enum('COMPLETED','PENDING','REJECTED') DEFAULT NULL,
  `provider_id` bigint NOT NULL,
  `motif_refus` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKim14eek991nwga51pv46rskcu` (`reference`),
  KEY `FK84miuymrpxsfmjlvdpuyivt0u` (`provider_id`),
  CONSTRAINT `FK84miuymrpxsfmjlvdpuyivt0u` FOREIGN KEY (`provider_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `withdrawal_requests`
--

LOCK TABLES `withdrawal_requests` WRITE;
/*!40000 ALTER TABLE `withdrawal_requests` DISABLE KEYS */;
INSERT INTO `withdrawal_requests` VALUES (1,2000.00,'2026-05-19 10:47:03.362883','WAVE','781883729',NULL,'WIT-228872','PENDING',8,NULL),(2,25000.00,'2026-05-26 17:01:08.400126','WAVE','776723124',NULL,'WIT-946284','PENDING',8,NULL);
/*!40000 ALTER TABLE `withdrawal_requests` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-05-27 22:48:59
