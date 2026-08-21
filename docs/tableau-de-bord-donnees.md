# Tableau de Bord Syndic — Documentation technique complète

Ce document couvre toutes les données affichées sur le Tableau de Bord principal du syndic. Pour chaque donnée : la classe/table d'origine, le flux complet Controller → Service → BD, la formule exacte, et les incohérences repérées.

---

## 📦 Classes clés (à connaître avant de lire la suite)

📦 **ChargeCallItem** (entité, table `charge_call_items`)
└─ Représente : une ligne de charge due par UN copropriétaire sur UN appel de charges (ex: "charges trimestre 2 — M. Diallo")
└─ Variables clés :
&nbsp;&nbsp;• `quotePart: BigDecimal` → montant de base dû, calculé au tantième, **sans** pénalité
&nbsp;&nbsp;• `paidAmount: BigDecimal` → ce que le copropriétaire a déjà versé sur cette ligne
&nbsp;&nbsp;• `penaltyAmount: BigDecimal` → pénalité de retard, à 0 tant qu'elle n'a pas été appliquée par le job automatique
&nbsp;&nbsp;• `getTotalDue()` (méthode calculée) → `quotePart + penaltyAmount` = le vrai montant dû
&nbsp;&nbsp;• `getRemainingAmount()` (méthode calculée) → `getTotalDue() - paidAmount` = ce qu'il reste à payer
&nbsp;&nbsp;• `status: ChargeItemPaymentStatus` → PENDING / PARTIALLY_PAID / PAID / NO_AMOUNT_DUE, posé explicitement au moment du paiement (jamais recalculé à l'affichage)

📦 **SyndicWalletTransaction** (entité, table `syndic_wallet_transactions`)
└─ Représente : un mouvement d'argent réel sur le portefeuille du syndic (une entrée ou une sortie)
└─ Variables clés :
&nbsp;&nbsp;• `amount: BigDecimal` → **positif** = argent qui entre (ex: charge payée) ; **négatif** = argent qui sort (ex: prestataire payé)
&nbsp;&nbsp;• `category: WalletTransactionCategory` → CHARGES (copropriétaire paie), TRAVAUX (syndic paie un prestataire), RETRAIT (déclaré dans l'enum mais **jamais utilisé** actuellement, voir incohérence plus bas)
&nbsp;&nbsp;• `residence: Residence` → résidence concernée (permet de filtrer la trésorerie par résidence)
&nbsp;&nbsp;• `transactionDate: LocalDateTime` → date du mouvement, utilisée pour tous les calculs "à une date donnée"

📦 **SyndicWithdrawalRequest** (entité, table `syndic_withdrawal_requests`)
└─ Représente : une demande de retrait de fonds faite par le syndic (PAS une `SyndicWalletTransaction` — table séparée)
└─ Variables clés :
&nbsp;&nbsp;• `status: WithdrawalStatus` → PENDING (en attente de validation admin) / COMPLETED (validé et viré) / REJECTED
&nbsp;&nbsp;• `amount: BigDecimal` → montant demandé

📦 **InterventionRequest** (entité, table `intervention_requests`)
└─ Représente : une demande de travaux/intervention (signalement transformé, ou demande directe)
└─ Variables clés :
&nbsp;&nbsp;• `status: InterventionStatus` → PENDING / SYNDIC_ASSIGNED / QUOTE_VALIDATED / STARTED / FINISHED / FINAL_VALIDATION / CANCELLED
&nbsp;&nbsp;• `urgencyLevel: UrgencyLevel` → FAIBLE / MOYEN / URGENT
&nbsp;&nbsp;• `managementMode: InterventionManagementMode` → SYNDIC (géré manuellement par le syndic) ou OWNER (flux prestataire auto-géré par le copropriétaire)
&nbsp;&nbsp;• `createdAt: LocalDateTime` → date de création, utilisée pour les compteurs "aujourd'hui"

---

## 1. KPIs principaux (6 cartes)

📍 **Flux complet** :
```
SyndicDashboardController.getMainDashboard(residenceId)
  ↓
DasboardServiceImpl.getMainDashboard(residenceId)
  ↓
[selon la donnée] SyndicWalletTransactionRepository / ChargeCallItemRepository /
                   ResidenceRepository / PropertyRepository / InterventionRequestRepository  [BD]
  ↓
Calculs (voir chaque donnée ci-dessous)
  ↓
MainDashboardDTO (réponse JSON)
```
`residenceId` est un **paramètre optionnel de requête** (query param sur l'URL) — s'il est absent, tous les calculs ci-dessous sont faits sur **toutes** les résidences du syndic connecté (identifié via le token JWT → `SecurityContextHolder` → `UserRepository.findByEmail`).

### 2.1. Trésorerie totale (`treasuryTotal`)

- **Simple** : l'argent réellement disponible pour le syndic (ou une résidence), après déduction des retraits déjà effectués.
- **Origine des données** :
  - Les transactions (revenus/dépenses) → `SyndicWalletTransactionRepository.sumTransactionsUpTo()` (global) ou `.sumAllByResidenceId()` (filtré par résidence) → `SUM(amount)` sur la table `syndic_wallet_transactions`, jusqu'à la date demandée
  - Les retraits déjà validés → `SyndicWithdrawalRequestRepository.sumCompletedAmountUpTo()` → `SUM(amount)` sur `syndic_withdrawal_requests` **où `status = COMPLETED` uniquement**
  - Calcul centralisé dans `SyndicTreasuryService.getAvailableBalance(walletId, residenceId)` — seule source de vérité du projet pour ce calcul, réutilisée par 7 endroits différents de l'app (dashboard, finances, wallet, etc.)

**Pseudo-code** :
```java
// SyndicTreasuryService.getAvailableBalanceAsOf(walletId, residenceId, date)
transactions = SUM(SyndicWalletTransaction.amount) OÙ date <= dateDemandée
retraitsCompletes = SUM(SyndicWithdrawalRequest.amount) OÙ status == COMPLETED ET date <= dateDemandée
RETOURNER transactions - retraitsCompletes
```

- **Types de transactions comptées** (catégorie `WalletTransactionCategory`) :

| Catégorie | Signe | Créée où | Déclenchée par |
|---|---|---|---|
| `CHARGES` | positif (+) | `SolimusCallbackController` (webhook TouchPay) | Un copropriétaire paie une charge courante ou exceptionnelle — confirmé |
| `TRAVAUX` | négatif (−) | `SyndicTravauxServiceImpl` | Le syndic verse un acompte ou le solde final à un prestataire |
| `RETRAIT` | — | ⚠️ jamais créée actuellement | Déclarée dans l'enum mais aucun code n'appelle `setCategory(RETRAIT)` — voir incohérence ci-dessous |

- **Exemple** : 500 000 FCFA de transactions (charges payées − travaux payés) − 120 000 FCFA de retraits COMPLETED = **380 000 FCFA disponibles**.

> ⚠️ **INCOHÉRENCE DÉTECTÉE** : `WalletTransactionCategory.RETRAIT` existe dans l'enum mais n'est **jamais utilisé** dans le code — les retraits ne passent jamais par `syndic_wallet_transactions`, ils sont gérés uniquement via la table séparée `syndic_withdrawal_requests`. Ce n'est pas un bug (le calcul fonctionne correctement puisque `SyndicTreasuryService` soustrait les retraits COMPLETED séparément), mais la valeur d'enum `RETRAIT` est morte — soit à supprimer, soit à documenter comme "réservée, non utilisée" pour éviter la confusion d'un futur développeur qui s'attendrait à la voir dans `syndic_wallet_transactions`.

### 2.2. Évolution de la trésorerie (`treasuryEvolutionPercent`)

- **Simple** : la trésorerie a-t-elle augmenté ou diminué par rapport à la fin du mois dernier ?
- **Origine** : deux appels au même calcul que 2.1, à deux dates différentes — `LocalDateTime.now()` (aujourd'hui) et `LocalDate.now().withDayOfMonth(1)` (1er jour du mois actuel = fin du mois précédent).
- **Formule** (méthode `calculerVariation`, locale à `DasboardServiceImpl`) :
```java
// calculerVariation(actuel, precedent)
SI precedent == 0 : RETOURNER 0   // protection division par zéro
RETOURNER (actuel - precedent) / precedent × 100
```
- **Exemple** : 380 000 FCFA aujourd'hui vs 350 000 FCFA fin du mois dernier → `(380000-350000)/350000×100` = **+8,57 %**.
- ⓘ **À noter** : ce calcul utilise la trésorerie **brute** (transactions seules, sans déduire les retraits) pour les deux dates comparées — donc ce n'est techniquement pas exactement `treasuryTotal` (qui, lui, déduit les retraits) qui est comparé à lui-même un mois plus tôt, mais sa version brute des deux côtés. Le résultat en % reste correct puisque c'est la même base des deux côtés de la comparaison.

### 2.3. Taux de recouvrement (`recoveryRate`)

- **Simple** : sur tout ce qui est dû aux copropriétaires (toutes charges confondues, pénalités de retard incluses), quelle part a déjà été payée ?
- **Origine** : `ChargeCallItemRepository.findByChargeCallBudgetResidenceId()` (filtré) ou `.findAllByBudgetSyndicId()` (global) → `List<ChargeCallItem>` depuis la BD.
- **Formule** :
```java
// DasboardServiceImpl.getMainDashboard()
totalDue  = SUM(item.getTotalDue())   POUR CHAQUE ChargeCallItem   // quotePart + pénalité
totalPaid = SUM(item.getPaidAmount()) POUR CHAQUE ChargeCallItem
SI totalDue == 0 : recoveryRate = 0   // protection division par zéro
SINON : recoveryRate = totalPaid / totalDue × 100
```
- **Exemple** : 900 000 FCFA payés sur 1 000 000 FCFA dus (pénalités comprises) → **90 %**.
- ✅ Corrigé récemment : ce calcul utilisait avant `getQuotePart()` seul (sans pénalité), incohérent avec les écrans Paiements/Impayés qui utilisent `getTotalDue()`. Aligné maintenant.

### 2.4. Montant impayé (`unpaidAmount`)

- **Simple** : ce qu'il reste encore à percevoir sur toutes les charges non soldées.
- **Formule** : `SUM(item.getRemainingAmount())` sur la même liste de `ChargeCallItem` que 2.3 — donc `SUM(quotePart + pénalité − paidAmount)`.
- **Exemple** : 3 lignes non soldées de 50 000 / 30 000 / 20 000 FCFA restants → **100 000 FCFA**.

> ⚠️ **INCOHÉRENCE DÉTECTÉE (désactivée volontairement)** : `recoveryRateEvolutionPercent` et `unpaidEvolutionPercent` retournent toujours `null` — le code les désactive explicitement (`// à définir`). Raison notée dans le code : l'ancien calcul comparait les charges **créées** le mois dernier à leur `paidAmount` **d'aujourd'hui**, ce qui donnait un chiffre qui bougeait rétroactivement à chaque nouveau paiement — pas fiable pour un indicateur "évolution". Personne n'a encore validé la bonne formule de remplacement.

### 2.5. Résidences gérées / Lots totaux (`managedResidencesCount`, `totalLotsCount`)

- **Simple** : nombre de résidences du syndic, et nombre total de lots (appartements) dans toutes ces résidences.
- **Origine** : `ResidenceRepository.findBySyndicId()` → `List<Residence>`, puis pour chaque résidence, `PropertyRepository.findByResidenceId()` → `List<Property>`, dont on additionne les tailles.
- ⓘ **Toujours global** : même si une résidence est sélectionnée sur le dashboard (`residenceId` fourni), ces 2 chiffres restent calculés sur **toutes** les résidences du syndic — comportement volontaire, pas un bug.

### 2.6. Incidents ouverts / urgents (`openIncidentsCount`, `urgentIncidentsCount`)

- **Simple** : nombre de demandes de travaux pas encore clôturées, et combien sont marquées urgentes parmi elles.
- **Origine** : `InterventionRequestRepository.countByResidenceIdAndStatusIn()` (filtré) ou `.countByResidenceSyndicIdAndStatusIn()` (global).
- **Formule** : compte les `InterventionRequest` dont `status ∈ {PENDING, SYNDIC_ASSIGNED, QUOTE_VALIDATED, STARTED, FINISHED}` (tout sauf `FINAL_VALIDATION` et `CANCELLED`). Le compteur "urgents" ajoute le filtre `urgencyLevel = URGENT`.

### 2.7. Signalements Ouverts (`openSignalementsCount`, `urgentSignalementsCount`)

- **Simple** : combien de signalements sont encore en attente (ni traités, ni transformés en travaux), et combien sont urgents parmi eux.
- ✅ Remplace l'ancienne carte "Interventions du Jour" (`todayInterventionsCount`/`plannedInterventionsCount`, supprimée avec ses 4 méthodes repository devenues inutiles).
- **Origine** : `SignalementRepository.countUnresolvedBySyndicId()` (global) / `.countUnresolvedByResidenceId()` (filtré) — même liste d'exclusion que l'alerte `SIGNALEMENT` : statut hors `RESOLVED`/`CONVERTED_TO_WORK`.
- **Formule sous-titre** : `countUnresolvedBySyndicIdAndUrgencyLevel(..., URGENT)` (ou variante filtrée par résidence) — même signalements que ci-dessus, filtrés en plus sur `urgencyLevel = URGENT`, affiché "X urgent(s)".

---

## 2. Graphique "Trésorerie vs Appels de charges" (6 derniers mois glissants)

📍 **Flux complet** :
```
SyndicDashboardController.getFinancialEvolution(residenceId)
  ↓
FinanceService.getTreasuryEvolution(residenceId)
  ↓
FinanceServiceImpl.buildTreasuryEvolution(syndicId, walletId, residenceId)
  ↓
ChargeCallRepository.findByBudgetResidenceId() / findByBudgetSyndicId()  [BD]
+ SyndicTreasuryService.getAvailableBalanceAsOf() [pour chaque mois]
  ↓
List<TreasuryEvolutionPointDTO>  (6 points, un par mois)
```

📦 **TreasuryEvolutionPointDTO** (un point du graphique = un mois)
└─ Variables :
&nbsp;&nbsp;• `monthLabel: String` → ex "Jan", "Fév"
&nbsp;&nbsp;• `treasury: BigDecimal` → trésorerie disponible telle qu'elle était à la **fin** de ce mois-là
&nbsp;&nbsp;• `chargeCallsCumulated: BigDecimal` → cumul de tous les appels de charges émis **depuis le début jusqu'à la fin** de ce mois (jamais remis à zéro)

**Pseudo-code** (par mois, sur 6 mois glissants se terminant au mois actuel) :
```java
POUR CHAQUE mois (6 derniers mois glissants) :
    finDuMois = 1er jour du mois suivant
    treasury = SyndicTreasuryService.getAvailableBalanceAsOf(walletId, residenceId, finDuMois)
    chargeCallsCumulated = SUM(ChargeCall.totalAmount) OÙ ChargeCall.createdAt < finDuMois
```
- **Exemple** : en mars, `chargeCallsCumulated` additionne TOUS les appels de charges émis depuis le tout début jusqu'à fin mars — pas seulement ceux créés en mars.
- "6 derniers mois glissants" = si on est en août, la fenêtre va de mars à août, pas figée sur janvier-juin.

---

## 3. Alertes importantes (max 4, une par type)

📍 **Flux** : `SyndicDashboardController.getImportantAlerts()` → `DasboardServiceImpl.getImportantAlerts()` → 4 requêtes indépendantes → `List<AlertDTO>` triée par `occurredAt` décroissant.

📦 **AlertDTO**
└─ Variables : `type` (voir tableau), `title`, `description`, `occurredAt` (pour le tri), `relativeTime` (ex "Il y a 1h", calculé après tri via `ActivityLogPresenter.buildRelativeTime`)

| `type` | Condition de déclenchement | Origine BD | Contenu affiché |
|---|---|---|---|
| `MEETING` | Au moins 1 AG à venir | `MeetingRepository.findBySyndicIdAndStatus(..., UPCOMING)` → la plus proche par `meetingDate` | Résidence + date de l'AG la plus proche |
| `UNPAID` | Au moins 1 paiement en retard | `ChargeCallItemRepository.countLateUnpaidBySyndicId()` — échéance dépassée ET non soldé | Nombre total de paiements en retard |
| `INTERVENTION` | Au moins 1 intervention (travaux) non résolue, tous niveaux d'urgence | `InterventionRequestRepository.countByResidenceSyndicIdAndStatusIn()` — même liste de statuts "ouverts" que le KPI "Incidents ouverts" (tout sauf `FINAL_VALIDATION`/`CANCELLED`) | Nombre total de demandes de travaux non résolues |
| `SIGNALEMENT` | Au moins 1 signalement en attente | `SignalementRepository.countUnresolvedBySyndicId()` — statut hors `RESOLVED`/`CONVERTED_TO_WORK`, tous niveaux d'urgence | Nombre total de signalements en attente |

> ✅ Changé récemment : `INTERVENTION` et `SIGNALEMENT` affichaient avant le titre du dernier élément **urgent** non résolu (un seul, filtré). Ils affichent maintenant un **comptage total** (tous niveaux d'urgence), même logique que `UNPAID` — plus cohérent entre les 4 types d'alerte.

---

## 4. Activités récentes

📍 **Flux** : `SyndicDashboardController.getRecentActivities(limit)` → `DasboardServiceImpl.getRecentActivities()` → `ActivityLogRepository.findByResidenceSyndicIdOrderByCreatedAtDesc()` → `List<ActivityLog>` → transformé en `List<ActivityRowDTO>` via `ActivityLogPresenter.buildActivityRow()`.

- **Simple** : journal des dernières actions sur toutes les résidences du syndic (paiement reçu, document ajouté, AG créée...).
- ⓘ `limit` est un paramètre fourni par le front (query param), pas de valeur fixe côté backend.

## 5. Incidents récents

📍 **Flux** : `SyndicDashboardController.getRecentIncidents(limit)` → `DasboardServiceImpl.getRecentIncidents()` → `InterventionRequestRepository.findByResidenceSyndicIdAndManagementModeOrderByCreatedAtDesc(..., SYNDIC, ...)` → `List<RecentIncidentDTO>`.

- **Simple** : les dernières demandes de travaux gérées **directement par le syndic** — filtre explicite `managementMode = SYNDIC`, donc les interventions auto-gérées par les copropriétaires via le flux prestataire (`managementMode = OWNER`) n'apparaissent **pas** ici.

---

## Résumé des règles transversales

- Tous les KPI et le graphique acceptent un `residenceId` optionnel en query param → filtré si fourni, sinon calculé sur toutes les résidences du syndic.
- Les alertes et activités récentes sont **toujours globales**, peu importe la résidence sélectionnée.
- Le syndic courant est identifié partout via `SecurityContextHolder.getContext().getAuthentication().getName()` (email du token JWT) → `UserRepository.findByEmail()`.