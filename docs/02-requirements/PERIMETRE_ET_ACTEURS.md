# Périmètre du Système et Cartographie des Acteurs

**Statut du document** : `[VALIDÉ]`  
**Date** : 2026-09-26  

---

## 1. Périmètre du Système (Scope)

### 1.1 In-Scope (Périmètre Inclus)
* **Socle d'Identité & Sécurité** : Intégration Keycloak OIDC/OAuth2, gestion MFA, PKCE, fédération, tokens JWT courts.
* **Gouvernance Multi-Tenant** : Organisations (hôpitaux, cliniques, laboratoires, officines), départements, hiérarchies de membres.
* **Dossier Patient Longitudinal** : Antécédents, allergies, consultations, biométrie, examens, prescriptions, rapports d'imagerie.
* **Consentement & Break-Glass** : Sollicitation, acceptation, restriction temporelle/périmètre, révocation, accès d'urgence audité.
* **Services d'Imagerie & IA** : Stockage objet sécurisé S3/MinIO, métadonnées, inférence Python découplée (PyTorch/MONAI), explicabilité Grad-CAM, validation clinique obligatoire.
* **Module Pharmacie & Commandes** : Onboarding et vérification officines (ANRP), catalogue en cache, ordonnance numérique, flux commande et paiement différé (escrow).
* **Module Logistique Livraison** : Affectation livreur, preuve de livraison par OTP/QR sécurisé, gestion des incidents.
* **Interopérabilité de Santé** : Passerelle FHIR R4, HL7 v2 (LIS/HIS), passerelle DICOMweb (PACS/VNA).
* **Master Patient Index (MPI)** : Algorithmes déterministes et probabilistes, détection doublons, réconciliation.
* **Canaux d'Accès** : Mobile Flutter (Patient & Livreur), Web (Portail Professionnel & Admin), Passerelle SMS/USSD pour non-smartphone.
* **Mode Offline-First** : Cache local chiffré limité aux données vitales, journal d'événements, synchronisation idempotente.
* **Piste d'Audit Immuable** : Journalisation de tout événement d'accès, de modification ou de délégation.

### 1.2 Out-of-Scope (Périmètre Exclu en V1)
* Stockage de données médicales directement sur la Blockchain (exclu formellement).
* Prise de décision médicale entièrement automatisée par l'IA sans signature d'un praticien (interdit formellement).
* Hébergement direct des données de carte bancaire (délégué intégralement à des PSP certifiés PCI-DSS).
* Remplacement complet des ERP hospitaliers (HIS) : MedScan s'interface via l'Interoperability Hub sans prétendre remplacer la facturation ou la gestion des lits interne de l'hôpital.

---

## 2. Cartographie des Acteurs

### 2.1 Acteurs Humains

```
                                  ┌───────────────────────────┐
                                  │      ACTEURS HUMAINS      │
                                  └─────────────┬─────────────┘
                                                │
         ┌──────────────────────────────┼──────────────────────────────┐
         ▼                              ▼                              ▼
  [ USAGERS SOINS ]             [ PROFESSIONNELS ]             [ LOGISTIQUE & ADMIN ]
  • Patient                     • Médecin Référent             • Pharmacien
  • Proche Tuteur               • Médecin Spécialiste          • Livreur
  • Patient SMS/USSD            • Radiologue                   • Administrateur Tenant
                                • Technicien Labo / Imagerie   • Super-Administrateur MedScan
                                • Infirmier(ère)               • Auditeur / DPO
```

| Acteur | Canaux Utilisés | Responsabilités Principales |
| :--- | :--- | :--- |
| **Patient** | Mobile Flutter, SMS/USSD | Gère son profil, consulte son dossier, octroie/révoque les consentements, commande des médicaments, valide la réception. |
| **Médecin (Généraliste / Spécialiste)** | Portail Web, Mobile | Crée des consultations, prescrit, demande des examens, consulte le dossier (selon consentement), active le Break-Glass justifié. |
| **Radiologue / Spécialiste Imagerie** | Portail Web | Téléverse des examens DICOM/images, déclenche l'aide IA, valide cliniquement les rapports d'imagerie. |
| **Technicien Laboratoire / Imagerie** | Portail Web, Système LIS | Saisit ou importe les résultats d'analyses et clichés bruts. |
| **Pharmacien d'Officine** | Portail Web | Gère son stock/catalogue, valide l'authenticité de l'ordonnance, prépare la commande, remet au livreur. |
| **Livreur de Santé** | Mobile Flutter (App Delivery) | Accepte une course, récupère le colis scellé en pharmacie, livre au patient, valide via OTP/QR code. |
| **Administrateur d'Établissement (Tenant Admin)** | Portail Web | Administre les départements, gère les comptes du personnel de son organisation, configure les politiques locales. |
| **Super-Administrateur MedScan** | Portail Web | Gère la plateforme globale, valide l'onboarding des organisations/pharmacies, supervise la sécurité. |
| **Auditeur de Sécurité / DPO** | Portail Web | Examine les journaux d'audit, les accès Break-Glass, les alertes d'anomalie d'accès. |

### 2.2 Acteurs Systèmes (Systèmes Externes)

| Système Externe | Protocole / Interface | Rôle |
| :--- | :--- | :--- |
| **Keycloak Identity Provider** | OIDC / OAuth2 / JWT | Authentification, gestion de sessions, MFA, fédération d'identités. |
| **AI Inference Engine (FastAPI)** | REST / mTLS / Protobuf | Traitement des images médicales, inférence de modèles PyTorch/MONAI, Grad-CAM. |
| **PACS / VNA Externe** | DICOMweb (WADO-RS, QIDO-RS) | Archivage et communication des séries d'imagerie radiologique. |
| **EHR / HIS Hospitalier** | HL7 FHIR R4 / HL7 v2 MLLP | Synchronisation des flux d'admission, de diagnostics et d'antécédents. |
| **LIS (Système Labo)** | HL7 v2 (ORU^R01) / FHIR | Transmission automatique des résultats d'analyses biologiques. |
| **Fournisseur de Paiement (PSP)** | Webhooks sécurisés (HMAC) | Encaissement, séquestre (escrow) et déblocage des paiements. |
| **Passerelle Télécom (SMS/USSD)** | API REST / SMPP / HTTP Webhook | Envoi de notifications transactionnelles et navigation USSD interactive. |
