# Découpage Modulaire du Système MedScan Enterprise

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  

---

## 1. Vue d'Ensemble des Modules

MedScan Enterprise est conçu comme un système modulaire à couplage faible et forte cohésion.  
Chaque module possède une responsabilité métier délimitée, des interfaces d'API strictes et des règles d'audit dédiées.

```
┌────────────────────────────────────────────────────────────────────────┐
│                        INTERFACE / API GATEWAY                         │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │
    ┌───────────────────────────────┼───────────────────────────────┐
    ▼                               ▼                               ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│ MOD-01 : IAM &     │    │ MOD-02 : TENANCY & │    │ MOD-03 : PATIENT & │
│ SECURITY GATEWAY   │    │ ORGANISATIONS      │    │ DOSSIER MÉDICAL    │
└────────────────────┘    └────────────────────┘    └────────────────────┘
    │                               │                               │
    ├───────────────────────────────┼───────────────────────────────┤
    ▼                               ▼                               ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│ MOD-04 : CONSENT & │    │ MOD-05 : IMAGERIE  │    │ MOD-06 : AI ENGINE │
│ BREAK-GLASS        │    │ MÉDICALE & PACS    │    │ (SERVICE DÉCOUPLÉ) │
└────────────────────┘    └────────────────────┘    └────────────────────┘
    │                               │                               │
    ├───────────────────────────────┼───────────────────────────────┤
    ▼                               ▼                               ▼
┌────────────────────┐    ┌────────────────────┐    ┌────────────────────┐
│ MOD-07 : INTEROP   │    │ MOD-08 : PHARMACY  │    │ MOD-09 : DELIVERY  │
│ HUB (FHIR/HL7)     │    │ & PRESCRIPTIONS    │    │ & LOGISTIQUE       │
└────────────────────┘    └────────────────────┘    └────────────────────┘
    │                               │                               │
    ├───────────────────────────────┴───────────────────────────────┤
    ▼                                                               ▼
┌────────────────────┐                                    ┌────────────────────┐
│ MOD-10 : OFFLINE & │                                    │ MOD-11 : AUDIT &   │
│ SYNCHRONISATION    │                                    │ OBSERVABILITÉ      │
└────────────────────┘                                    └────────────────────┘
```

---

## 2. Description Détaillée des Modules

### MOD-01 : IAM & Security Gateway
* **Responsabilité** : Validation des jetons OIDC/JWT issus de Keycloak, extraction des claims, vérification contextuelle ABAC, vérification des signatures d'API, limitation de débit (rate limiting).
* **Dépendances** : Keycloak, Redis (optionnel cache de révocation/rate-limit).

### MOD-02 : Tenancy & Organisations
* **Responsabilité** : Modélisation des hôpitaux, cliniques, laboratoires, officines. Gestion des départements/services médicaux, affectation des professionnels et règles d'isolation des données par organisation.
* **Règle clé** : L'appartenance à un tenant ne confère pas de droit automatique sur les patients externes.

### MOD-03 : Patient & Dossier Médical Longitudinal
* **Responsabilité** : Gestion du profil patient, MPI (Master Patient Index), antécédents, allergies, consultations, notes cliniques, comptes-rendus, constantes vitales.
* **Origine des données** : Traçabilité obligatoire (patient déclaré, importé d'un système externe, produit en consultation, validé par praticien).

### MOD-04 : Consentement & Accès d'Urgence (Break-Glass)
* **Responsabilité** : Gestion du cycle de vie du consentement (demande, validation patient, restriction de périmètre/durée, révocation). Gestion du flux d'urgence dérogatoire avec saisie obligatoire du motif et alerte immédiate.

### MOD-05 : Imagerie Médicale & Métadonnées
* **Responsabilité** : Ingestion sécurisée des images (JPEG/PNG en phase 1, DICOM/DICOMweb en phase cible), découplage métadonnées (PostgreSQL) et fichiers binaires (Object Storage S3/MinIO chiffré), génération d'URLs signées temporaires.

### MOD-06 : AI Engine (Aide au Diagnostic Clinique)
* **Responsabilité** : Service externe Python/FastAPI isolé. Exécution des pipelines de Deep Learning (PyTorch/MONAI), génération de prédictions avec score de confiance et explicabilité (Grad-CAM).
* **Règle absolue** : Résultat consultatif soumis à la revue et signature d'un médecin avant intégration dans le dossier.

### MOD-07 : Interoperability Hub
* **Responsabilité** : Passerelle d'échange de santé standardisée. Endpoints FHIR R4 (Patient, Observation, Condition, DiagnosticReport), parseur/générateur HL7 v2 (MLLP), connecteur DICOMweb.
* **Garantie** : Aucune intégration point-à-point sauvage ; tout flux passe par le hub avec journalisation et contrôle d'accès.

### MOD-08 : Pharmacy & Prescriptions
* **Responsabilité** : Processus d'homologation des pharmacies physiques (adossé aux publications ANRP), ordonnance numérique signée, catalogue/stock en cache synchronisé, panier, commande et séquestre financier.

### MOD-09 : Delivery & Logistique
* **Responsabilité** : Attribution de courses aux livreurs agréés, traçabilité des étapes d'acheminement, remise sécurisée par OTP ou QR code infalsifiable, libération du séquestre de paiement après accusé de réception.

### MOD-10 : Offline & Synchronisation
* **Responsabilité** : Définition du paquet de données vitales embarquables, protocole de synchronisation différentielle bidirectionnelle, détection et résolution des conflits avec journal d'idempotence.

### MOD-11 : Audit, Sécurité & Traçabilité
* **Responsabilité** : Capture synchrone et asynchrone de chaque événement sensible (qui, quoi, quand, où, pourquoi, quel patient, quel consentement). Export d'audit pour les autorités réglementaires et DPO.
