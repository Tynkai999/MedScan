# MedScan Enterprise — État d'Avancement Global pour Agent IA

**Document d'orientation et de synchronisation pour agents IA & développeurs**  
**Date d'émission** : 2026-09-26  
**Version du système** : `0.1.0-SNAPSHOT` (Architecture Validée & Déployée)  
**Environnement de production actif** : [`https://medscan-sluw.onrender.com/medscan/api`](https://medscan-sluw.onrender.com/medscan/api)  
**Dépôt officiel** : [`https://github.com/Tynkai999/MedScan.git`](https://github.com/Tynkai999/MedScan.git) (Branche `main`)  
**Statut global de la suite de tests** : **53/53 tests passants (100% au vert)**

---

## 1. Vue d'Ensemble & Objectif de ce Document

Ce document fournit à l'agent IA (qu'il soit chargé de la modélisation Machine Learning, du backend, du frontend ou de l'infrastructure) une **cartographie exacte et unifiée** de l'état actuel de MedScan Enterprise :
1. Les 11 modules de la spécification cible et leur niveau d'avancement réel.
2. L'architecture logicielle, les contrats d'API, les protocoles de sécurité (JWT RS256, Keycloak-compat, Row-Level Security PostgreSQL).
3. Le référentiel des identifiants sécurisés (UUID RFC 4122 v4).
4. La passerelle d'inférence IA découplée (MOD-06) et les modalités d'intégration des futurs modèles PyTorch/FastAPI.
5. La feuille de route immédiate pour les prochains chantiers.

---

## 2. Matrice d'Avancement des 11 Modules (Architecture Cible)

```
┌────────────────────────────────────────────────────────────────────────┐
│                        INTERFACE / API GATEWAY                         │
│             Render Live: https://medscan-sluw.onrender.com             │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    ▼                               ▼                               ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│ MOD-01 : IAM &     │    │ MOD-02 : TENANCY & │    │ MOD-03 : PATIENT & │
│ SECURITY GATEWAY   │    │ ORGANISATIONS      │    │ DOSSIER MÉDICAL    │
│  [100% OPÉRATIONNEL│    │  [100% OPÉRATIONNEL│    │  [100% OPÉRATIONNEL│
└────────────────────┘    └────────────────────┘    └────────────────────┘
    │                               │                               │
    ├───────────────────────────────┼───────────────────────────────┤
    ▼                               ▼                               ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│ MOD-04 : CONSENT & │    │ MOD-05 : IMAGERIE  │    │ MOD-06 : AI ENGINE │
│ BREAK-GLASS        │    │ MÉDICALE & PACS    │    │ (SERVICE DÉCOUPLÉ) │
│  [100% OPÉRATIONNEL│    │  [100% OPÉRATIONNEL│    │  [100% OPÉRATIONNEL│
└────────────────────┘    └────────────────────┘    └────────────────────┘
    │                               │                               │
    ├───────────────────────────────┼───────────────────────────────┤
    ▼                               ▼                               ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│ MOD-07 : INTEROP   │    │ MOD-08 : PHARMACY  │    │ MOD-09 : DELIVERY  │
│ HUB (FHIR/HL7)     │    │ & PRESCRIPTIONS    │    │ & LOGISTIQUE (OTP) │
│  [SPÉCIFIÉ / ROAD] │    │  [100% OPÉRATIONNEL│    │  [PROCHAINE ÉTAPE] │
└────────────────────┘    └────────────────────┘    └────────────────────┘
    │                               │                               │
    ├───────────────────────────────┴───────────────────────────────┤
    ▼                                                               ▼
┌────────────────────┐                                    ┌────────────────────┐
│ MOD-10 : OFFLINE & │                                    │ MOD-11 : AUDIT &   │
│ SYNCHRONISATION    │                                    │ OBSERVABILITÉ DPO  │
│  [SPÉCIFIÉ / ROAD] │                                    │  [100% OPÉRATIONNEL│
└────────────────────┘                                    └────────────────────┘
```

### Tableau de Synthèse Fonctionnelle

| Module | Intitulé | État Actuel | Couverture Test | Déploiement Render |
| :--- | :--- | :---: | :---: | :---: |
| **MOD-01** | IAM & Security Gateway (JWT RS256, RBAC) | **100% Terminé** | 22 tests unitaires | **En Ligne** |
| **MOD-02** | Multi-Tenancy & Isolation PostgreSQL RLS | **100% Terminé** | 3 tests & Migrations V1-V3 | **En Ligne** |
| **MOD-03** | Patient & Dossier Longitudinal (Vitals, Consultations) | **100% Terminé** | 4 tests métier | **En Ligne** |
| **MOD-04** | Consentement & Urgence Dérogatoire (Break-Glass) | **100% Terminé** | Validé en intégration | **En Ligne** |
| **MOD-05** | Imagerie Médicale & Métadonnées PACS | **100% Terminé** | 4 tests d'imagerie | **En Ligne** |
| **MOD-06** | Passerelle IA Découplée & Simulation Grad-CAM | **100% Terminé** | Inférence + Fallback testés | **En Ligne** |
| **MOD-07** | Interoperability Hub (Standard HL7 FHIR R4) | *Spécifié* | À implémenter | Backlog (Phase 2) |
| **MOD-08** | E-Prescription & Dispensation en Pharmacie | **100% Terminé** | Validé en intégration | **En Ligne** |
| **MOD-09** | Delivery Sécurisée & Remise par Code OTP | *Conception prête* | Prêt au dev | **Prochaine Étape** |
| **MOD-10** | Synchronisation Hors-Ligne & Delta-Sync | *Spécifié* | À implémenter | Backlog (Phase 3) |
| **MOD-11** | Journal d'Audit Immuable DPO & Conformité | **100% Terminé** | Validé en intégration | **En Ligne** |

---

## 3. Architecture Technique & Choix de Conception

### 3.1. Principes Fondateurs
- **Zero-Dependency Core** : Le backend s'exécute de façon autonome (sans conteneur d'application lourd requis au dev) avec serveur HTTP multi-threadé natif, sérialiseurs JSON sans bibliothèque externe vulnérable, et support des standards Jakarta EE / JAX-RS.
- **Sécurité Cryptographique RS256** : Signatures asymétriques RSA 2048-bit compatibles OIDC / Keycloak (`kid: medscan-jwt-key-1`). Aucune migration de code requise pour brancher un Keycloak distant en production.
- **Isolation Multi-Tenant par RLS (Row-Level Security)** : Chaque requête base de données PostgreSQL applique `SET LOCAL medscan.tenant_id = ?`, garantissant une étanchéité absolue des données entre organisations de santé (hôpitaux, cliniques, officines, laboratoires).
- **UUIDs RFC 4122 v4** : Tous les identifiants déterministes primitifs ont été éliminés au profit d'UUIDs cryptographiquement aléatoires.

---

## 4. Focus MOD-06 : Intégration de l'Agent IA & Modèles Deep Learning

Le système est architecturé selon le patron **Port & Adapter** (*Hexagonal Architecture*) afin que l'équipe / agent IA puisse progresser sans être bloqué par le backend, et vice-versa.

```
       ┌────────────────────────────────────────────────────────┐
       │                 MedScan Java Backend                   │
       │                                                        │
       │  [ImagingService] ──► [AiInferenceGateway (Port)]      │
       └───────────────────────────────┬────────────────────────┘
                                       │
                ┌──────────────────────┴──────────────────────┐
                ▼                                             ▼
┌───────────────────────────────┐             ┌───────────────────────────────┐
│ SimulatedAiInferenceEngine    │             │ HttpExternalAiInferenceEngine │
│ (Adaptateur Actif par défaut) │             │ (Adaptateur Production)       │
│ - DenseNet-121 haute fidélité │             │ - Appel REST HTTP/2           │
│ - Probabilités & Sévérité     │             │ - Timeout & Circuit-breaker   │
│ - Grad-CAM Attention Heatmaps │             │ - Bascule fallback auto       │
└───────────────────────────────┘             └───────────────┬───────────────┘
                                                              │
                                                              ▼
                                              ┌───────────────────────────────┐
                                              │ Service Python / FastAPI      │
                                              │ PyTorch, MONAI, TorchVision   │
                                              │ Variable:                     │
                                              │ MEDSCAN_AI_ENGINE_URL         │
                                              └───────────────────────────────┘
```

### 4.1. Statut Actuel du Moteur IA
- **En local et sur Render** : Le composant `SimulatedAiInferenceEngine` produit des diagnostics cliniques réalistes pour chaque étude d'imagerie (ex: accentuation péri-hilaire, silhouette cardiaque normale, score de confiance 91.2%, lien Grad-CAM).
- **Tolérance aux Pannes & Résilience** : Si l'URL externe est configurée mais que le service IA tombe en panne ou subit un dépassement de timeout, le backend intercepte l'erreur, logue un avertissement et bascule instantanément sur le moteur de secours sans interruption de service pour le praticien.
- **Règle Éthique Humain-dans-la-Boucle** : Toute analyse IA reste au statut `PENDING_REVIEW` jusqu'à validation formelle et signature électronique par un médecin ou radiologue assermenté.

### 4.2. Comment l'Agent IA doit brancher son modèle réel
L'agent IA dispose d'un guide technique complet et autonome avec code Python, schémas Pydantic et Dockerfile dans :  
👉 [`docs/06-api/GUIDE_INTEGRATION_MODELES_IA.md`](file:///Users/tpe4/Downloads/medscan-backend/docs/06-api/GUIDE_INTEGRATION_MODELES_IA.md)

**La seule action requise pour activer le modèle réel** :
Renseigner la variable d'environnement sur le serveur :
```env
MEDSCAN_AI_ENGINE_URL=http://<votre-serveur-ia>:8000/predict
```

---

## 5. Référentiel des Identifiants & Comptes de Test (RFC 4122 v4)

Tous les comptes utilisent le mot de passe unifié : **`Password123!`**

### Organisations (Tenants)
| Code Tenant | Organisation | Identifiant UUID v4 |
| :--- | :--- | :--- |
| `CH_OUAGADOUGOU` | Centre Hospitalier Universitaire | `e2241595-e068-46f7-8e82-ab2b9dd3c18a` |
| `LAB_BIO_SANTE` | Laboratoire d'Analyses Médicales | `95705328-0183-4248-8da7-8691ecf284e6` |
| `PHARMA_CENTRALE`| Pharmacie Principale d'Officine | `82961773-9273-4fef-bdd9-01adcdd51d89` |
| `EXPRESS_MEDIC` | Entreprise de Livraison Médicale | `ddfd4bda-a3a7-401a-a701-3ef502072705` |
| `MEDSCAN_SYS` | Plateforme Centrale de Régulation | `b47c7913-35d0-43e3-8ec1-5614dec9ffcd` |

### Utilisateurs Préconfigurés
| Identifiant (Username) | Rôle Système | Tenant Rattaché | UUID v4 Utilisateur |
| :--- | :--- | :--- | :--- |
| `doctor@medscan.org` | `DOCTOR` | `CH_OUAGADOUGOU` | `179a11bf-92a3-438a-9d7a-a711385c8ef0` |
| `patient@medscan.org` | `PATIENT` | `CH_OUAGADOUGOU` | `116286b8-79e3-48b6-b99e-06fed5f10ee4` |
| `radiologist@medscan.org` | `RADIOLOGIST` | `CH_OUAGADOUGOU` | `acd191a6-4846-45d7-bd38-f0a042b59348` |
| `nurse@medscan.org` | `NURSE` | `CH_OUAGADOUGOU` | `41f4a9b2-3e28-4ef7-8a14-38c201407e33` |
| `pharmacist@medscan.org` | `PHARMACIST` | `PHARMA_CENTRALE` | `62cac0f6-d219-43ca-ab33-2571df0c7630` |
| `delivery@medscan.org` | `DELIVERY_AGENT` | `EXPRESS_MEDIC` | `295a0bc4-ff23-455b-80df-8b21c43d9241` |
| `lab@medscan.org` | `LAB_TECHNICIAN` | `LAB_BIO_SANTE` | `8c628e94-3a9b-4654-bf63-94c65e8a04cb` |
| `auditor@medscan.org` | `AUDITOR` | `MEDSCAN_SYS` | `f70a1335-b31f-4193-88ef-ce3460f3d029` |
| `superadmin@medscan.org` | `SUPER_ADMIN` | `MEDSCAN_SYS` | `690807b5-236b-4e8c-a7c8-d1cf3d865c36` |

---

## 6. Endpoints Disponibles en Production (`https://medscan-sluw.onrender.com/medscan/api`)

### Authentification & Session (MOD-01)
- `GET  /v1/health` : Bilan de santé du service (`{"status":"UP"}`).
- `POST /v1/auth/login` : Authentification et obtention de la paire Access/Refresh Token JWT RS256.
- `POST /v1/auth/refresh` : Renouvellement transparent de jeton expiré.
- `GET  /v1/auth/me` : Profil de l'utilisateur authentifié.
- `GET  /v1/portal/{role}` : Portails RBAC sécurisés (`/doctor`, `/patient`, `/pharmacist`, `/delivery`, `/auditor`, etc.).

### Dossier Patient & Clinique (MOD-03 & MOD-04)
- `GET  /v1/patients` : Recherche et liste des patients autorisés dans le tenant.
- `GET  /v1/patients/{id}` : Dossier médical complet du patient (données démographiques, antécédents).
- `POST /v1/patients/{id}/vitals` : Enregistrement de constantes (Tension, Pouls, Saturation O2, Glycémie).
- `POST /v1/patients/{id}/consultations` : Création d'une note clinique d'observation médicale.
- `POST /v1/patients/{id}/break-glass` : Procédure d'urgence dérogatoire avec motif médical obligatoire.
- `GET  /v1/patient/my-record` : Espace personnel dédié au patient connecté.

### E-Prescription & Pharmacie (MOD-08)
- `GET  /v1/prescriptions/{code}` : Consultation et contrôle d'authenticité d'une ordonnance numérique.
- `POST /v1/prescriptions/{code}/dispense` : Délivrance sécurisée des médicaments par l'officine.

### Imagerie & Diagnostic IA (MOD-05 & MOD-06)
- `GET  /v1/imaging/studies` : Liste des examens radiologiques du patient.
- `POST /v1/imaging/studies` : Ingestion d'une nouvelle étude d'imagerie (RX, Scanner, Échographie).
- `POST /v1/imaging/studies/{id}/ai-analyze` : Déclenchement de l'inférence IA (Computer Vision & Grad-CAM).
- `POST /v1/imaging/studies/{id}/report` : Rédaction et signature du rapport par le radiologue.

### Traçabilité & Audit Réglementaire (MOD-11)
- `GET  /v1/audit/logs` : Piste d'audit inviolable réservée au DPO (`AUDITOR`) et superviseurs.

---

## 7. Feuille de Route & Prochaines Actions Immédiates

### Action Immédiate Conseillée : MOD-09 (Livraison & Logistique Sécurisée)
Le flux actuel permet à un médecin de prescrire (`DOCTOR`), et à une pharmacie de préparer et dispenser (`PHARMACIST`). Pour boucler la boucle patient :
1. **Création de la commande de livraison** liée à une ordonnance dispensée.
2. **Attribution de la mission** au livreur (`DELIVERY_AGENT`, `express-medic`).
3. **Machine à états d'acheminement** : `ASSIGNED` ➔ `PICKED_UP` ➔ `IN_TRANSIT` ➔ `DELIVERED`.
4. **Remise sécurisée par Code OTP à 6 chiffres** (transmis au patient par SMS/App, fourni au livreur pour libérer et valider la course).

### Documentation Complémentaire pour les Développeurs
- 📘 Guide Intégration Frontend (React/Next, Flutter, Dart & TS) : [`docs/06-api/FRONTEND_INTEGRATION_GUIDE.md`](file:///Users/tpe4/Downloads/medscan-backend/docs/06-api/FRONTEND_INTEGRATION_GUIDE.md)
- 🤖 Guide Intégration Modèles IA (Python FastAPI, Grad-CAM) : [`docs/06-api/GUIDE_INTEGRATION_MODELES_IA.md`](file:///Users/tpe4/Downloads/medscan-backend/docs/06-api/GUIDE_INTEGRATION_MODELES_IA.md)
- 📮 Collection Postman d'Intégration Clé-en-main : [`postman/MedScan_Enterprise.postman_collection.json`](file:///Users/tpe4/Downloads/medscan-backend/postman/MedScan_Enterprise.postman_collection.json)
- 📑 Spécification OpenAPI 3.1.0 Interactive : [`src/main/resources/META-INF/openapi.yaml`](file:///Users/tpe4/Downloads/medscan-backend/src/main/resources/META-INF/openapi.yaml)
