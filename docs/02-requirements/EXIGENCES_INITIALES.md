# Exigences Fonctionnelles et Non-Fonctionnelles Initiales

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  
**Normes de Référence** : ISO/IEC/IEEE 29148, ISO/IEC 25010, OWASP ASVS v4.0

---

## 1. Exigences Fonctionnelles (Functional Requirements)

### 1.1 Authentification et Gestion des Identités (FR-AUTH)
* **FR-AUTH-001** : Le système doit déléguer l'authentification primaire à Keycloak via OpenID Connect (Authorization Code Flow avec PKCE pour les clients mobiles et SPA).
* **FR-AUTH-002** : Le système doit imposer l'authentification multifacteur (MFA) pour tout compte de professionnel de santé et administrateur.
* **FR-AUTH-003** : Le système doit vérifier l'autorisation contextuelle (Organisation, Département, Rôle, Permissions) sur chaque requête API entrante.
* **FR-AUTH-004** : Les mots de passe et secrets d'authentification ne doivent jamais transiter ni être stockés par le backend MedScan.

### 1.2 Dossier Patient et Master Patient Index (FR-PAT)
* **FR-PAT-001** : Le système doit maintenir un dossier médical longitudinal unique pour chaque patient, agrégeant antécédents, allergies, consultations, examens et prescriptions.
* **FR-PAT-002** : Le système doit implémenter un Master Patient Index (MPI) avec algorithme de rapprochement déterministe et probabiliste pour dédoublonner les identités entre établissements.
* **FR-PAT-003** : Chaque donnée médicale enregistrée doit comporter son origine explicite (déclarée patient, importée système externe, saisie praticien, validée).

### 1.3 Consentement et Sécurité d'Accès (FR-SEC)
* **FR-SEC-001** : Le système doit conditionner l'accès au dossier patient par un professionnel à un consentement actif et valide accordé par le patient (sauf dérogation Break-Glass).
* **FR-SEC-002** : Le patient doit pouvoir définir le périmètre précis (types de données), la durée d'accès et révoquer un consentement à tout moment.
* **FR-SEC-003** : Le système doit implémenter une procédure d'urgence *Break-Glass* permettant un accès exceptionnel aux données d'urgence en exigeant un motif clinique obligatoire et en générant une notification et un audit prioritaire.

### 1.4 Imagerie Médicale (FR-IMG)
* **FR-IMG-001** : Le système doit séparer le stockage des métadonnées (PostgreSQL) et des binaires d'imagerie (Object Storage S3/MinIO chiffré).
* **FR-IMG-002** : L'accès aux images par les clients doit s'effectuer exclusivement via des URLs pré-signées à durée de validité courte (max. 15 minutes).
* **FR-IMG-003** : Le système doit supporter en phase 1 les formats JPEG/PNG haute résolution et en phase cible le standard DICOM / DICOMweb (WADO-RS, QIDO-RS).

### 1.5 Aide à la Décision Clinique par IA (FR-AI)
* **FR-AI-001** : L'analyse IA doit être traitée par un microservice Python/FastAPI découplé, sans bloquer le thread de l'application Jakarta EE.
* **FR-AI-002** : Tout résultat IA doit être accompagné de la version du modèle, du score de confiance et d'une carte d'explicabilité (Grad-CAM).
* **FR-AI-003** : Aucun résultat IA ne peut être intégré comme diagnostic définitif sans la signature et validation formelle d'un praticien de santé autorisé.

### 1.6 Pharmacie et Ordonnances (FR-PHAR)
* **FR-PHAR-001** : L'intégration d'une officine physique requiert une vérification réglementaire préalable adossée au registre officiel de l'ANRP.
* **FR-PHAR-002** : Les ordonnances numériques doivent être vérifiables cryptographiquement et infalsifiables.
* **FR-PHAR-003** : Le système doit maintenir un cache synchronisé des stocks et prix sans altérer l'autorité de l'officine sur sa disponibilité réelle.

### 1.7 Logistique et Livraison Sécurisée (FR-DEL)
* **FR-DEL-001** : Les colis pharmaceutiques doivent être scellés et remis au livreur avec un identifiant de suivi unique sans révélation des pathologies associées.
* **FR-DEL-002** : La preuve de remise au patient doit exiger la validation conjointe d'un code OTP à usage unique ou d'un QR code dynamique.
* **FR-DEL-003** : Le paiement de la commande doit reposer sur un séquestre (escrow) débloqué uniquement après validation de la livraison par le patient.

### 1.8 Interopérabilité de Santé (FR-INT)
* **FR-INT-001** : Le hub d'interopérabilité doit exposer les ressources standard HL7 FHIR R4 (Patient, Condition, Observation, DiagnosticReport, Encounter).
* **FR-INT-002** : Le système doit être capable de consommer et d'émettre des messages HL7 v2 (ex: ORU^R01 pour les flux de laboratoires).
* **FR-INT-003** : Toutes les interfaces d'interopérabilité doivent être authentifiées via mTLS ou OAuth2, tracées et auditées.

---

## 2. Exigences Non-Fonctionnelles (Non-Functional Requirements)

### 2.1 Sécurité & Confidentialité (NFR-SEC)
* **NFR-SEC-001** : Zéro confiance (Zero Trust) : Aucun paramètre d'identification (patientId, tenantId, etc.) fourni par le client ne doit être accepté sans contrôle d'autorisation au niveau objet (anti-IDOR).
* **NFR-SEC-002** : Chiffrement systématique : En transit via TLS 1.3 (ou 1.2 strict) et au repos pour toutes les bases de données et banques de données objet.
* **NFR-SEC-003** : Masquage & Minimisation : Aucune donnée de santé ou information confidentielle (mots de passe, diagnostics, JWT) ne doit figurer dans les logs applicatifs.
* **NFR-SEC-004** : Protection des Webhooks : Validation obligatoire par signature HMAC sha256 et horodatage pour éviter les attaques par rejeu.

### 2.2 Performance et Disponibilité (NFR-PERF)
* **NFR-PERF-001** : Temps de réponse des API transactionnelles de consultation < 300 ms au 95e percentile.
* **NFR-PERF-002** : Inférence IA asynchrone avec notification Webhook/WebSocket du praticien dès disponibilité du résultat.

### 2.3 Mode Déconnecté & Résilience (NFR-OFF)
* **NFR-OFF-001** : L'application mobile doit permettre la consultation des constantes vitales, allergies et ordonnances récentes en l'absence totale de réseau.
* **NFR-OFF-002** : La synchronisation post-reconnexion doit être idempotente, chiffrée, et résoudre les conflits sur la base d'un horodatage logique vectoriel ou d'une règle déterministe auditable.

### 2.4 Gouvernance de l'IA (NFR-AI)
* **NFR-AI-001** : Traçabilité totale des prédictions (modèle, poids, hyperparamètres, version du pipeline d'ingestion) selon les principes ISO/IEC 42001.
