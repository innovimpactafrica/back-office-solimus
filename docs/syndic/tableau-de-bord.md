# Syndic — Tableau de Bord

## 1. À quoi sert ce menu et qui y accède

Écran d'accueil du syndic après connexion : vue d'ensemble chiffrée de son activité (trésorerie, recouvrement, incidents, signalements), toutes résidences confondues ou filtrée sur une résidence précise.

**Accès** : rôle `ROLE_SYNDIC` uniquement (`@PreAuthorize("hasAuthority('ROLE_SYNDIC')")` sur tout le contrôleur).

⚠️ **INCOHÉRENCE DÉTECTÉE** : contrairement à la plupart des autres menus syndic (`SyndicBudgetController` exige en plus `@planFeatureGuard.hasFeature('CHARGE_MANAGEMENT')`, `SyndicWalletController` exige `WALLET_MANAGEMENT`, etc.), ce contrôleur n'a **aucune** vérification de plan d'abonnement (`PlanFeatureGuard`). Un syndic peut donc toujours voir son tableau de bord, même si son plan ne couvre pas les modules dont les chiffres proviennent (charges, wallet, travaux...). À confirmer si c'est un choix volontaire ("le dashboard reste toujours accessible, peu importe le plan") ou un oubli.

## 2. Ce que l'utilisateur voit et peut faire

- Un sélecteur de résidence (dropdown) — "Toutes les résidences" ou une résidence précise
- 6 cartes KPI : Trésorerie totale, Taux de recouvrement, Montant impayé, Résidences gérées, Lots totaux, Incidents ouverts (+ urgents), Signalements ouverts (+ urgents)
- Un graphique "Trésorerie vs Appels de charges" sur les 6 derniers mois glissants
- Une liste d'alertes importantes (AG à venir, paiements en retard, travaux non résolus, signalements en attente)
- Un tableau "Activités récentes"
- Un tableau "Incidents récents"

Aucune action d'écriture sur cet écran — c'est un menu 100% lecture seule (aucun formulaire, aucun bouton qui modifie une donnée).

## 3. Comment ça fonctionne concrètement

📍 **Flux général** :
```
SyndicDashboardController
  ↓
DashboardServiceImpl
  ↓
Repositories (ChargeCallItem, InterventionRequest, Signalement, Meeting, ActivityLog, Residence, Property)
+ SyndicTreasuryService (calcul centralisé de la trésorerie)
  ↓
DTOs de réponse
```

Le syndic connecté est identifié partout via `SecurityContextHolder.getContext().getAuthentication().getName()` (email du token JWT) → `UserRepository.findByEmail()`.

### 3.1. KPIs principaux — `GET /dashboard/main`

`residenceId` est **optionnel** en query param.

| Donnée | Filtré par résidence si fournie ? | Formule |
|---|---|---|
| `treasuryTotal` | Oui | `SyndicTreasuryService.getAvailableBalance(walletId, residenceId)` = somme des `SyndicWalletTransaction.amount` jusqu'à maintenant |
| `recoveryRate` | Oui | `SUM(ChargeCallItem.getTotalDue()) / SUM(ChargeCallItem.getPaidAmount()) × 100` (toutes lignes de charges) |
| `unpaidAmount` | Oui | `SUM(ChargeCallItem.getRemainingAmount())` |
| `managedResidencesCount` | **Non, toujours global** | Nombre de `Residence` du syndic |
| `totalLotsCount` | **Non, toujours global** | Somme des `Property` de toutes les résidences du syndic |
| `openIncidentsCount` | Oui | Compte les `InterventionRequest` dont `status ∈ {PENDING, SYNDIC_ASSIGNED, QUOTE_VALIDATED, STARTED, FINISHED}` (tout sauf `FINAL_VALIDATION`/`CANCELLED`) |
| `urgentIncidentsCount` | Oui | Même liste + `urgencyLevel = URGENT` |
| `openSignalementsCount` | Oui | Compte les `Signalement` dont `status ∉ {RESOLVED, CONVERTED_TO_WORK}` |
| `urgentSignalementsCount` | Oui | Même liste + `urgencyLevel = URGENT` |

✅ **Nettoyage effectué** : `treasuryEvolutionPercent`, `recoveryRateEvolutionPercent` et `unpaidEvolutionPercent` ont été supprimés de `MainDashboardDTO` (et leur calcul retiré de `DashboardServiceImpl`, y compris les méthodes `calculerVariation`/`calculerSoldeADate` devenues inutiles) — le front n'affichait aucune flèche/pourcentage d'évolution sur ces 3 cartes, y compris `treasuryEvolutionPercent` qui avait pourtant une vraie valeur calculée (pas `null`). Code mort, retiré.

**Pseudo-code taux de recouvrement :**
```
totalDue  = SUM(item.getTotalDue())    // quotePart + pénalité, pour chaque ChargeCallItem
totalPaid = SUM(item.getPaidAmount())
SI totalDue == 0 : recoveryRate = 0
SINON : recoveryRate = totalPaid / totalDue × 100
```

✅ **Incohérence corrigée** — `DashboardService.java` (l'interface) disait dans son commentaire Javadoc :
> "si absent, utilise automatiquement la résidence la plus récemment créée par le syndic"

Ce qui était faux : le comportement réel du code (`DashboardServiceImpl.getMainDashboard`) a toujours été **si `residenceId` est absent, le calcul se fait sur toutes les résidences du syndic** (mode global), jamais un repli sur une résidence précise. Le Javadoc a été corrigé pour dire exactement ça.

### 3.2. Graphique financier — `GET /dashboard/financial-evolution`

Délégué à `FinanceService.getTreasuryEvolution(residenceId)` (`FinanceServiceImpl.buildTreasuryEvolution`), pas au `DashboardService`.

**Pseudo-code**, pour chacun des 6 derniers mois glissants (se terminant au mois actuel) :
```
POUR CHAQUE mois (du plus ancien au plus récent) :
    finDuMois = 1er jour du mois suivant
    treasury = SyndicTreasuryService.getAvailableBalanceAsOf(walletId, residenceId, finDuMois)
    chargeCallsCumulated = SUM(ChargeCall.totalAmount) OÙ ChargeCall.createdAt < finDuMois   // cumul jamais remis à zéro
```

### 3.3. Alertes importantes — `GET /dashboard/alerts`

Toujours **globales** (aucun paramètre `residenceId` sur cet endpoint). 4 vérifications indépendantes, chacune ajoute au maximum 1 alerte à la liste, triée ensuite par `occurredAt` décroissant :

| `type` | Condition | Source |
|---|---|---|
| `MEETING` | Au moins 1 AG `UPCOMING` | La plus proche par `meetingDate`, `MeetingRepository.findBySyndicIdAndStatus` |
| `UNPAID` | Au moins 1 ligne en retard | `ChargeCallItemRepository.countLateUnpaidBySyndicId()` — échéance dépassée (`dueDate < CURRENT_DATE`) et statut `PENDING` |
| `INTERVENTION` | Au moins 1 intervention ouverte | Même liste de statuts "ouverts" que §3.1 |
| `SIGNALEMENT` | Au moins 1 signalement non résolu | Même critère que §3.1 |

### 3.4. Activités récentes — `GET /dashboard/recent-activities`

Toujours globales. `limit` fourni par le front (défaut 5). Source : `ActivityLogRepository.findByResidenceSyndicIdOrderByCreatedAtDesc()`, transformé via `ActivityLogPresenter.buildActivityRow()` (le même presenter que celui utilisé pour les KPI/labels d'activité ailleurs dans l'app).

### 3.5. Incidents récents — `GET /dashboard/recent-incidents`

Toujours globales. Filtre explicite `managementMode = SYNDIC` — les interventions auto-gérées par les copropriétaires (`managementMode = OWNER`, flux prestataire) **n'apparaissent jamais** ici, même si elles sont récentes.

### 3.6. Dropdown résidences — `GET /dashboard/residences`

`ResidenceRepository.findBySyndicId()` → id + nom uniquement. Aucun tri explicite dans la requête.

## 4. Pourquoi c'est fait ainsi

- **`managedResidencesCount`, `totalLotsCount`, alertes, activités et incidents récents restent toujours globaux**, même quand une résidence est sélectionnée : décision volontaire (confirmée par les commentaires du code) — l'idée est que ces informations donnent une vue d'ensemble de l'activité du syndic, pas un filtre par résidence. Seuls les 4 premiers KPI (trésorerie, recouvrement, impayés, incidents/signalements ouverts) suivent le filtre résidence.
- **La trésorerie passe systématiquement par `SyndicTreasuryService`**, jamais par un calcul local : ce service est explicitement documenté dans son propre code comme "seule source de vérité", partagée avec le menu Wallet et la validation des demandes de retrait — pour que ces 3 endroits ne puissent jamais afficher des chiffres différents.

## 5. Endpoints concernés

Tous sous `/api/syndic`, rôle requis `ROLE_SYNDIC`, aucune vérification de plan (`PlanFeatureGuard`).

| Méthode | URL | Entrée | Sortie |
|---|---|---|---|
| GET | `/dashboard/main` | `residenceId` (optionnel, query) | `MainDashboardDTO` |
| GET | `/dashboard/financial-evolution` | `residenceId` (optionnel, query) | `List<TreasuryEvolutionPointDTO>` |
| GET | `/dashboard/alerts` | — | `List<AlertDTO>` |
| GET | `/dashboard/recent-activities` | `limit` (défaut 5, query) | `List<ActivityRowDTO>` |
| GET | `/dashboard/recent-incidents` | `limit` (défaut 5, query) | `List<RecentIncidentDTO>` |
| GET | `/dashboard/residences` | — | `List<SyndicResidenceDTO>` |

Erreurs possibles sur `/dashboard/main` : `403` si `residenceId` fourni n'appartient pas au syndic connecté, `404` si la résidence n'existe pas.

## 6. Entités et relations en base de données

Ce menu ne modifie aucune entité — il ne fait que lire, à travers plusieurs tables déjà alimentées par d'autres menus :

- `User` (le syndic connecté) → `Residence` (1-N, `residence.syndic_id`)
- `Residence` → `Property` (1-N, lots)
- `Residence` → `SyndicWallet` (via le syndic) → `SyndicWalletTransaction` (1-N) — trésorerie
- `Residence` → `Budget` → `ChargeCall` → `ChargeCallItem` (1-N) — recouvrement/impayés
- `Residence` → `InterventionRequest` (1-N) — incidents
- `Residence` → `Signalement` (1-N) — signalements
- `Residence` → `Meeting` (1-N) — alertes AG
- `Residence` → `ActivityLog` (1-N) — activités récentes

## 7. Dépendances avec les autres menus

C'est le menu le plus dépendant de tous les autres — il n'a aucune logique propre, il agrège :

- **Wallet** : `SyndicTreasuryService` est partagé. Si la logique de trésorerie change côté Wallet, le dashboard change aussi automatiquement (et inversement) — c'est voulu, mais ça veut dire qu'on ne peut pas modifier ce service sans vérifier son impact ici.
- **Charges** : `recoveryRate`/`unpaidAmount` dépendent de `ChargeCallItem.getTotalDue()`/`getRemainingAmount()`. Si la formule de pénalité change dans le menu Charges, ces 2 KPI changent aussi sans code à modifier ici.
- **Travaux** : la liste des statuts "ouverts" (`{PENDING, SYNDIC_ASSIGNED, QUOTE_VALIDATED, STARTED, FINISHED}`) est **dupliquée en dur à 2 endroits** dans `DashboardServiceImpl` (KPI §3.1 et alerte §3.3). Si un nouveau statut `InterventionStatus` est ajouté un jour, il faut penser à mettre à jour ces 2 listes, sinon le KPI et l'alerte "Travaux" divergeront silencieusement.
- **Signalements** : même remarque, la liste d'exclusion `{RESOLVED, CONVERTED_TO_WORK}` vit dans le repository (`SignalementRepository`), pas dupliquée ici — moins fragile que pour les travaux.
- **Assemblées Générales** : l'alerte "AG à venir" dépend de `MeetingStatus.UPCOMING`.
- **Résidences** : le dropdown et les compteurs globaux dépendent directement de `ResidenceRepository`/`PropertyRepository`.

## 8. Notifications, tâches planifiées, intégrations externes

Aucune. Ce menu est strictement en lecture — il ne déclenche aucun email, aucune notification push (Firebase), n'appelle ni MinIO ni TouchPay, et n'est déclenché par aucun job planifié (`@Scheduled`). Il affiche uniquement des données déjà produites par d'autres menus.

## 9. AVANT DE MODIFIER — points d'attention

- **Fragile / non testé** : aucun test automatisé n'existe dans le projet pour ce menu (ni pour le reste de l'application — seul `SolimusApplicationTests.java`, le test de démarrage par défaut de Spring Boot, existe). Toute modification doit être vérifiée manuellement (Swagger ou front).
- ~~**Piège n°1** : Javadoc de `DashboardService.getMainDashboard()` qui parlait d'un repli automatique sur la résidence la plus récente~~ — corrigé. Le Javadoc dit maintenant clairement : si `residenceId` est absent, le calcul se fait sur toutes les résidences du syndic.
- **Piège n°2** : la liste des statuts "ouverts" pour les incidents est dupliquée dans 2 méthodes du même fichier (`getMainDashboard` et `getImportantAlerts`). Si vous ajoutez/retirez un statut `InterventionStatus` de cette notion d'"ouvert", mettez à jour les deux, sinon le KPI "Incidents ouverts" et l'alerte "Travaux non résolus" afficheront des comptages différents.
- ~~**Piège n°3** : commentaires avec anciens noms d'enum dans `SyndicTreasuryService`~~ — corrigé. D'autres repositories (`SyndicWithdrawalRequestRepository`, `WithdrawalRequestRepository`, `InterventionRequestRepository`, hors périmètre de ce menu) mentionnent encore `TRAVAUX`/`RETRAIT` au lieu de `BUDGET_EXPENSE`/`WITHDRAWAL` — à corriger en documentant les menus Wallet/Travaux/Retraits.
- **TODO connu** : `recoveryRateEvolutionPercent` et `unpaidEvolutionPercent` renvoient toujours `null`, en attendant qu'une formule d'évolution stable dans le temps soit validée. Le front doit gérer ce `null` proprement (ne pas planter si la flèche d'évolution est absente sur ces 2 cartes).
- **À ne surtout pas casser** : le comportement "toujours global" de `managedResidencesCount`, `totalLotsCount`, alertes, activités et incidents récents — même si ça peut sembler être un oubli de filtre par résidence à première vue, c'est confirmé comme volontaire par les commentaires du code.
- ~~**Nom de classe trompeur** : l'implémentation s'appelait `DasboardServiceImpl` (faute de frappe, sans "h")~~ — corrigé, la classe s'appelle maintenant `DashboardServiceImpl`.
